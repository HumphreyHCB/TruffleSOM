/**
 * Copyright (c) 2013 Stefan Marr,   stefan.marr@vub.ac.be
 * Copyright (c) 2009 Michael Haupt, michael.haupt@hpi.uni-potsdam.de
 * Software Architecture Group, Hasso Plattner Institute, Potsdam, Germany
 * http://www.hpi.uni-potsdam.de/swa/
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package trufflesom.compiler;

import static trufflesom.interpreter.SNodeFactory.createCatchNonLocalReturn;
import static trufflesom.interpreter.SNodeFactory.createFieldRead;
import static trufflesom.interpreter.SNodeFactory.createFieldWrite;
import static trufflesom.interpreter.SNodeFactory.createNonLocalReturn;
import static trufflesom.vm.SymbolTable.symBlockSelf;
import static trufflesom.vm.SymbolTable.symFrameOnStack;
import static trufflesom.vm.SymbolTable.symSelf;
import static trufflesom.vm.SymbolTable.symbolFor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import com.oracle.truffle.api.source.Source;

import bd.basic.ProgramDefinitionError;
import bd.inlining.Scope;
import bd.inlining.ScopeBuilder;
import bd.inlining.nodes.Inlinable;
import bd.source.SourceCoordinate;
import bd.tools.structure.StructuralProbe;
import trufflesom.compiler.Variable.Argument;
import trufflesom.compiler.Variable.Internal;
import trufflesom.compiler.Variable.Local;
import trufflesom.interpreter.LexicalScope;
import trufflesom.interpreter.Method;
import trufflesom.interpreter.nodes.ExpressionNode;
import trufflesom.interpreter.nodes.FieldNode;
import trufflesom.interpreter.nodes.FieldNode.FieldReadNode;
import trufflesom.interpreter.nodes.LocalVariableNode.LocalVariableReadNode;
import trufflesom.interpreter.nodes.NonLocalVariableNode.NonLocalVariableReadNode;
import trufflesom.interpreter.nodes.ReturnNonLocalNode;
import trufflesom.interpreter.nodes.UninitializedMessageSendNode;
import trufflesom.interpreter.nodes.literals.BlockNode;
import trufflesom.interpreter.nodes.minibytecodes.ComputeBytecode;
import trufflesom.interpreter.nodes.minibytecodes.DoubleBytecode;
import trufflesom.interpreter.nodes.minibytecodes.InitMethodBytecodes;
import trufflesom.interpreter.nodes.minibytecodes.LoopBytecode;
import trufflesom.interpreter.supernodes.IntIncrementNode;
import trufflesom.interpreter.supernodes.LocalVarReadUnaryMsgWriteNode;
import trufflesom.interpreter.supernodes.LocalVariableSquareNode;
import trufflesom.interpreter.supernodes.NonLocalVarReadUnaryMsgWriteNode;
import trufflesom.interpreter.supernodes.NonLocalVariableSquareNode;
import trufflesom.primitives.Primitives;
import trufflesom.vm.NotYetImplementedException;
import trufflesom.vm.SymbolTable;
import trufflesom.vm.VmSettings;
import trufflesom.vmobjects.SClass;
import trufflesom.vmobjects.SInvokable;
import trufflesom.vmobjects.SInvokable.SMethod;
import trufflesom.vmobjects.SSymbol;


@SuppressWarnings("unchecked")
public class MethodGenerationContext
    implements ScopeBuilder<MethodGenerationContext>, Scope<LexicalScope, Method> {

  protected final ClassGenerationContext  holderGenc;
  protected final MethodGenerationContext outerGenc;
  private final boolean                   blockMethod;

  protected SSymbol signature;
  private boolean   primitive;
  private boolean   needsToCatchNonLocalReturn;

  // does directly or indirectly a non-local return
  protected boolean throwsNonLocalReturn;

  protected boolean accessesVariablesOfOuterScope;

  protected final LinkedHashMap<SSymbol, Argument> arguments;
  protected final LinkedHashMap<SSymbol, Local>    locals;

  private Internal frameOnStack;

  protected final LexicalScope currentScope;

  private final List<SMethod> embeddedBlockMethods;

  public final StructuralProbe<SSymbol, SClass, SInvokable, Field, Variable> structuralProbe;

  public MethodGenerationContext(final ClassGenerationContext holderGenc,
      final StructuralProbe<SSymbol, SClass, SInvokable, Field, Variable> structuralProbe) {
    this(holderGenc, null, false, structuralProbe);
  }

  public MethodGenerationContext(
      final StructuralProbe<SSymbol, SClass, SInvokable, Field, Variable> structuralProbe) {
    this(null, null, false, structuralProbe);
  }

  public MethodGenerationContext(final ClassGenerationContext holderGenc,
      final MethodGenerationContext outerGenc) {
    this(holderGenc, outerGenc, true, outerGenc.structuralProbe);
  }

  protected MethodGenerationContext(final ClassGenerationContext holderGenc,
      final MethodGenerationContext outerGenc, final boolean isBlockMethod,
      final StructuralProbe<SSymbol, SClass, SInvokable, Field, Variable> structuralProbe) {
    this.holderGenc = holderGenc;
    this.outerGenc = outerGenc;
    this.blockMethod = isBlockMethod;
    this.structuralProbe = structuralProbe;

    LexicalScope outer = (outerGenc != null) ? outerGenc.getCurrentLexicalScope() : null;
    this.currentScope = new LexicalScope(outer);

    accessesVariablesOfOuterScope = false;
    throwsNonLocalReturn = false;
    needsToCatchNonLocalReturn = false;
    embeddedBlockMethods = new ArrayList<SMethod>();

    arguments = new LinkedHashMap<SSymbol, Argument>();
    locals = new LinkedHashMap<SSymbol, Local>();
  }

  @Override
  public Source getSource() {
    return holderGenc.getSource();
  }

  public void markAccessingOuterScopes() {
    MethodGenerationContext context = this;
    while (context != null) {
      context.accessesVariablesOfOuterScope = true;
      context = context.outerGenc;
    }
  }

  public void addEmbeddedBlockMethod(final SMethod blockMethod) {
    embeddedBlockMethods.add(blockMethod);
    currentScope.addEmbeddedScope(((Method) blockMethod.getInvokable()).getScope());
  }

  public LexicalScope getCurrentLexicalScope() {
    return currentScope;
  }

  public Internal getFrameOnStackMarker(final long coord) {
    if (outerGenc != null) {
      return outerGenc.getFrameOnStackMarker(coord);
    }

    if (frameOnStack == null) {
      assert needsToCatchNonLocalReturn;
      assert !locals.containsKey(symFrameOnStack);

      int index = locals.size();
      frameOnStack = new Internal(symFrameOnStack, coord, index);
      locals.put(symFrameOnStack, frameOnStack);
      currentScope.addVariable(frameOnStack);
    }
    return frameOnStack;
  }

  public void makeOuterCatchNonLocalReturn() {
    throwsNonLocalReturn = true;

    MethodGenerationContext ctx = markOuterContextsToRequireContextAndGetRootContext();
    assert ctx != null;
    ctx.needsToCatchNonLocalReturn = true;
  }

  public boolean requiresContext() {
    return throwsNonLocalReturn || accessesVariablesOfOuterScope;
  }

  private MethodGenerationContext markOuterContextsToRequireContextAndGetRootContext() {
    MethodGenerationContext ctx = outerGenc;
    while (ctx.outerGenc != null) {
      ctx.throwsNonLocalReturn = true;
      ctx = ctx.outerGenc;
    }
    return ctx;
  }

  public boolean needsToCatchNonLocalReturn() {
    // only the most outer method needs to catch
    return needsToCatchNonLocalReturn && outerGenc == null;
  }

  private String getMethodIdentifier() {
    String cls = holderGenc.getName().getString();
    if (holderGenc.isClassSide()) {
      cls += "_class";
    }
    return cls + ">>" + signature.toString();
  }

  public final SInvokable assemble(final ExpressionNode body, final long coord) {
    currentScope.finalizeVariables(locals.size());

    if (primitive) {
      return Primitives.constructEmptyPrimitive(
          signature, holderGenc.getSource(), coord, structuralProbe);
    }

    return assembleMethod(body, coord);
  }

  protected SMethod assembleMethod(ExpressionNode body, final long coord) {
    if (needsToCatchNonLocalReturn()) {
      body = createCatchNonLocalReturn(body, getFrameOnStackMarker(coord));
    }

    String className = holderGenc.getName().getString();
    String methodName = signature.getString();

    if (VmSettings.UseAstInterp) {
      if (className.equals("List")) {
        if (methodName.equals("isShorter:than:")) {
          body = new LoopBytecode(new byte[] {0, 1, 2, 13, 3, 4, 6, 7, 5, 6, 8, 9, 2, 10},
              SymbolTable.symbolFor("next")).initialize(coord);
        }
      }

      if (className.equals("Random") || className.equals("SomRandom")) {
        if (!holderGenc.isClassSide()) {
          if (methodName.equals("next")) {
            body = new ComputeBytecode(1309, 13849, 65535, new byte[] {0, 1, 2, 3, 4, 5, 6},
                0).initialize(coord);
          } else if (methodName.equals("initialize")) {
            body = new InitMethodBytecodes(
                new byte[] {10}, new int[] {0}, 74755L).initialize(coord);
          }
        }
      }

      if (!holderGenc.isClassSide()) {
        if (className.equals("BenchmarkHarness")) {
          if (methodName.equals("initialize")) {
            // | total benchmarkClass numIterations innerIterations printAll doGC |
            body = new InitMethodBytecodes(
                new byte[] {5, 6, 6, 7, 8}, new int[] {0, 2, 3, 4, 5}, null).initialize(
                    coord);
          }
        } else if (className.equals("Planner")) {
          if (methodName.equals("initialize")) {
            body = new InitMethodBytecodes(
                new byte[] {6}, new int[] {0}, null).initialize(coord);
          }
        } else if (className.equals("JsonParser")) {
          if (methodName.equals("initializeWith:")) {
            // | input index line column current captureBuffer captureStart exceptionBlock |
            body = new InitMethodBytecodes(
                new byte[] {0, 5, 6, 5, 9, 11}, new int[] {0, 1, 2, 3, 4, 5}, "").initialize(
                    coord);
          }
        } else if (className.equals("ParseException")) {
          if (methodName.equals("initializeWith:at:line:column:")) {
            // initializeWith: message at: anOffset line: aLine column: aColumn
            // | offset line column msg |
            body = new InitMethodBytecodes(
                new byte[] {0, 1, 2, 3}, new int[] {3, 0, 1, 2}, null).initialize(
                    coord);
          }
        } else if (className.equals("Edge")) {
          if (methodName.equals("initializeWith:and:")) {
            // initializeWith: destination and: w = (
            // | dest weight |
            body = new InitMethodBytecodes(
                new byte[] {0, 1}, new int[] {0, 1}, null).initialize(coord);
          }
        } else if (className.equals("Lexer")) {
          if (methodName.equals("initialize:")) {
            // | fileContent state stateAfterPeek peekDone
            // index sym text nextSym nextText |
            body = new InitMethodBytecodes(
                new byte[] {0, 8, 6}, new int[] {0, 3, 4}, null).initialize(coord);
          }
        } else if (className.equals("SBlock")) {
          if (methodName.equals("initialize:in:with:")) {
            // #initialize: aSMethod in: aContext with: aBlockClass = (
            // | method context blockClass |
            body = new InitMethodBytecodes(
                new byte[] {0, 1, 2}, new int[] {0, 1, 2}, null).initialize(coord);
          }
        } else if (className.equals("SMethod")) {
          if (methodName.equals("initializeWith:bc:literals:numLocals:maxStack:")) {
            // #initializeWith: aSSymbol bc: bcArray literals: literalsArray numLocals:
            // numLocals maxStack: maxStack = (
            // | signature holder bytecodes literals
            // numberOfLocals maximumNumberOfStackElements |
            body = new InitMethodBytecodes(
                new byte[] {0, 1, 2, 3, 4}, new int[] {0, 2, 3, 4, 5}, null).initialize(coord);
          }
        } else if (className.equals("SPrimitive")) {
          if (methodName.equals("initialize:with:")) {
            // #initialize: aSSymbol with: aBlock = (
            // numLocals maxStack: maxStack = (
            // | signature holder isEmpty operation |
            body = new InitMethodBytecodes(
                new byte[] {0, 8, 1}, new int[] {0, 2, 3}, null).initialize(coord);
          }
        }

        else if (className.equals("Vector2D")) {
          if (methodName.equals("compare:and:")) {
            body = new DoubleBytecode(
                new byte[] {
                    0, 1, 2, 9, 6 /* target 1 */,
                    6, /* target 1 */ 3, 9, 10, 7,
                    /* target 2 */ 4, 9, 14, 8, /* target 3 */ 5,
                    9, 18, 8, 7},
                0L, -1L, 1L).initialize(coord);
          } else if (methodName.equals("initX:y:")) {
            body = new InitMethodBytecodes(
                new byte[] {0, 1}, new int[] {0, 1}, null).initialize(coord);
          }
        }
      }
    }

    Method truffleMethod =
        new Method(getMethodIdentifier(), holderGenc.getSource(), coord,
            body, currentScope, (ExpressionNode) body.deepCopy());

    SMethod meth = new SMethod(signature, truffleMethod,
        embeddedBlockMethods.toArray(new SMethod[0]));

    if (structuralProbe != null) {
      String id = meth.getIdentifier();
      structuralProbe.recordNewMethod(symbolFor(id), meth);
    }

    // return the method - the holder field is to be set later on!
    return meth;
  }

  @Override
  public Variable[] getVariables() {
    int numVars = arguments.size() + locals.size();

    Variable[] vars = new Variable[numVars];
    int i = 0;
    for (Argument a : arguments.values()) {
      vars[i] = a;
      i += 1;
    }

    for (Local l : locals.values()) {
      vars[i] = l;
      i += 1;
    }

    return vars;
  }

  public void setVarsOnMethodScope() {
    currentScope.setVariables(getVariables());
  }

  public void markAsPrimitive() {
    primitive = true;
  }

  public void setSignature(final SSymbol sig) {
    signature = sig;
  }

  private void addArgument(final SSymbol arg, final long coord) {
    if ((symSelf == arg || symBlockSelf == arg) && arguments.size() > 0) {
      throw new IllegalStateException(
          "The self argument always has to be the first argument of a method");
    }

    Argument argument = new Argument(arg, arguments.size(), coord);
    arguments.put(arg, argument);

    if (structuralProbe != null) {
      structuralProbe.recordNewVariable(argument);
    }
  }

  public void addArgumentIfAbsent(final SSymbol arg, final long coord) {
    if (arguments.containsKey(arg)) {
      return;
    }

    addArgument(arg, coord);
  }

  public boolean hasLocal(final SSymbol local) {
    return locals.containsKey(local);
  }

  public int getNumberOfLocals() {
    return locals.size();
  }

  public Local addLocal(final SSymbol local, final long coord) {
    int index = locals.size();
    Local l = new Local(local, coord, index);
    assert !locals.containsKey(local);
    locals.put(local, l);

    if (structuralProbe != null) {
      structuralProbe.recordNewVariable(l);
    }
    return l;
  }

  private Local addLocalAndUpdateScope(final SSymbol name, final long coord)
      throws ProgramDefinitionError {
    Local l = addLocal(name, coord);
    currentScope.addVariable(l);
    return l;
  }

  public boolean isBlockMethod() {
    return blockMethod;
  }

  public ClassGenerationContext getHolder() {
    return holderGenc;
  }

  private int getOuterSelfContextLevel() {
    int level = 0;
    MethodGenerationContext ctx = outerGenc;
    while (ctx != null) {
      ctx = ctx.outerGenc;
      level++;
    }
    return level;
  }

  public int getContextLevel(final SSymbol varName) {
    if (locals.containsKey(varName) || arguments.containsKey(varName)) {
      return 0;
    }

    if (outerGenc != null) {
      return 1 + outerGenc.getContextLevel(varName);
    }

    return 0;
  }

  public int getContextLevel(final Variable var) {
    if (locals.containsValue(var) || arguments.containsValue(var)) {
      return 0;
    }

    if (outerGenc != null) {
      return 1 + outerGenc.getContextLevel(var);
    }

    return 0;
  }

  public Local getEmbeddedLocal(final SSymbol embeddedName) {
    return locals.get(embeddedName);
  }

  protected Variable getVariable(final SSymbol varName) {
    if (locals.containsKey(varName)) {
      return locals.get(varName);
    }

    if (arguments.containsKey(varName)) {
      return arguments.get(varName);
    }

    if (outerGenc != null) {
      Variable outerVar = outerGenc.getVariable(varName);
      if (outerVar != null) {
        accessesVariablesOfOuterScope = true;
      }
      return outerVar;
    }
    return null;
  }

  public ExpressionNode getLocalReadNode(final Variable variable, final long coord) {
    return variable.getReadNode(getContextLevel(variable), coord);
  }

  public ExpressionNode getLocalWriteNode(final Variable variable,
      final ExpressionNode valExpr, final long coord) {
    int ctxLevel = getContextLevel(variable);

    if (valExpr instanceof IntIncrementNode
        && ((IntIncrementNode) valExpr).doesAccessVariable(variable)) {
      return ((IntIncrementNode) valExpr).createIncNode((Local) variable, ctxLevel);
    }

    if (ctxLevel == 0) {
      if (valExpr instanceof LocalVariableSquareNode) {
        return variable.getReadSquareWriteNode(ctxLevel, coord,
            ((LocalVariableSquareNode) valExpr).getLocal());
      }
      if (valExpr instanceof NonLocalVariableSquareNode) {
        throw new NotYetImplementedException(
            "a missing read/square/write combination, used in a benchmark?");
      }

      if (valExpr instanceof UninitializedMessageSendNode) {
        UninitializedMessageSendNode val = (UninitializedMessageSendNode) valExpr;
        ExpressionNode[] args = val.getArguments();
        if (args.length == 1 && args[0] instanceof LocalVariableReadNode) {
          LocalVariableReadNode var = (LocalVariableReadNode) args[0];
          if (var.getLocal() == variable) {
            return new LocalVarReadUnaryMsgWriteNode((Local) variable,
                val.getInvocationIdentifier()).initialize(coord);
          }
        }
      }
    } else {
      if (valExpr instanceof NonLocalVariableSquareNode) {
        return variable.getReadSquareWriteNode(ctxLevel, coord,
            ((NonLocalVariableSquareNode) valExpr).getLocal());
      }

      if (valExpr instanceof LocalVariableSquareNode) {
        throw new NotYetImplementedException(
            "a missing read/square/write combination, used in a benchmark?");
      }

      if (valExpr instanceof UninitializedMessageSendNode) {
        UninitializedMessageSendNode val = (UninitializedMessageSendNode) valExpr;
        ExpressionNode[] args = val.getArguments();
        if (args.length == 1 && args[0] instanceof NonLocalVariableReadNode) {
          NonLocalVariableReadNode var = (NonLocalVariableReadNode) args[0];
          if (var.getLocal() == variable) {
            return new NonLocalVarReadUnaryMsgWriteNode(ctxLevel, (Local) variable,
                val.getInvocationIdentifier()).initialize(coord);
          }
        }
      }
    }
    return variable.getWriteNode(ctxLevel, valExpr, coord);
  }

  protected Local getLocal(final SSymbol varName) {
    if (locals.containsKey(varName)) {
      return locals.get(varName);
    }

    if (outerGenc != null) {
      Local outerLocal = outerGenc.getLocal(varName);
      if (outerLocal != null) {
        accessesVariablesOfOuterScope = true;
      }
      return outerLocal;
    }
    return null;
  }

  public ReturnNonLocalNode getNonLocalReturn(final ExpressionNode expr,
      final long coord) {
    makeOuterCatchNonLocalReturn();
    return createNonLocalReturn(expr, getFrameOnStackMarker(coord),
        getOuterSelfContextLevel(), coord);
  }

  private ExpressionNode getSelfRead(final long coord) {
    return getVariable(symSelf).getReadNode(getContextLevel(symSelf), coord);
  }

  public FieldReadNode getObjectFieldRead(final SSymbol fieldName,
      final long coord) {
    if (!holderGenc.hasField(fieldName)) {
      return null;
    }
    return createFieldRead(getSelfRead(coord),
        holderGenc.getFieldIndex(fieldName), coord);
  }

  public FieldNode getObjectFieldWrite(final SSymbol fieldName, final ExpressionNode exp,
      final long coord) {
    if (!holderGenc.hasField(fieldName)) {
      return null;
    }

    return createFieldWrite(getSelfRead(coord), exp,
        holderGenc.getFieldIndex(fieldName), coord);
  }

  protected void addLocal(final Local l, final SSymbol name) {
    assert !locals.containsKey(name);
    locals.put(name, l);
    currentScope.addVariable(l);
  }

  public void mergeIntoScope(final LexicalScope scope, final SMethod toBeInlined) {
    for (Variable v : scope.getVariables()) {
      int slotIndex = locals.size();
      Local l = v.splitToMergeIntoOuterScope(slotIndex);
      if (l != null) { // can happen for instance for the block self, which we omit
        SSymbol name = l.getQualifiedName(holderGenc.getSource());
        addLocal(l, name);
      }
    }

    SMethod[] embeddedBlocks = toBeInlined.getEmbeddedBlocks();
    LexicalScope[] embeddedScopes = scope.getEmbeddedScopes();

    assert ((embeddedBlocks == null || embeddedBlocks.length == 0) &&
        (embeddedScopes == null || embeddedScopes.length == 0)) ||
        embeddedBlocks.length == embeddedScopes.length;

    if (embeddedScopes != null) {
      for (LexicalScope e : embeddedScopes) {
        currentScope.addEmbeddedScope(e.split(currentScope));
      }

      for (SMethod m : embeddedBlocks) {
        embeddedBlockMethods.add(m);
      }
    }

    boolean removed = embeddedBlockMethods.remove(toBeInlined);
    assert removed;
    currentScope.removeMerged(scope);
  }

  @Override
  public bd.inlining.Variable<?> introduceTempForInlinedVersion(
      final Inlinable<MethodGenerationContext> blockOrVal, final long coord)
      throws ProgramDefinitionError {
    Local loopIdx;
    if (blockOrVal instanceof BlockNode) {
      Argument[] args = ((BlockNode) blockOrVal).getArguments();
      assert args.length == 2;
      loopIdx = getLocal(args[1].getQualifiedName(holderGenc.getSource()));
    } else {
      // if it is a literal, we still need a memory location for counting, so,
      // add a synthetic local
      loopIdx = addLocalAndUpdateScope(symbolFor(
          "!i" + SourceCoordinate.getLocationQualifier(
              holderGenc.getSource(), coord)),
          coord);
    }
    return loopIdx;
  }

  public boolean isFinished() {
    throw new UnsupportedOperationException(
        "You'll need the BytecodeMethodGenContext. "
            + "This method should only be used when creating bytecodes.");
  }

  public void markFinished() {
    throw new UnsupportedOperationException(
        "You'll need the BytecodeMethodGenContext. "
            + "This method should only be used when creating bytecodes.");
  }

  /**
   * @return number of explicit arguments,
   *         i.e., excluding the implicit 'self' argument
   */
  public int getNumberOfArguments() {
    return arguments.size();
  }

  public SSymbol getSignature() {
    return signature;
  }

  private String stripColonsAndSourceLocation(String str) {
    int startOfSource = str.indexOf('@');
    if (startOfSource > -1) {
      str = str.substring(0, startOfSource);
    }

    // replacing classic colons with triple colons to still indicate them without breaking
    // selector semantics based on colon counting
    return str.replace(":", "⫶");
  }

  public void setBlockSignature(final Source source, final long coord) {
    String outerMethodName =
        stripColonsAndSourceLocation(outerGenc.getSignature().getString());

    int numArgs = getNumberOfArguments();
    int line = SourceCoordinate.getLine(source, coord);
    int column = SourceCoordinate.getColumn(source, coord);
    String blockSig = "λ" + outerMethodName + "@" + line + "@" + column;

    for (int i = 1; i < numArgs; i++) {
      blockSig += ":";
    }

    setSignature(symbolFor(blockSig));
  }

  @Override
  public String toString() {
    String sig = signature == null ? "" : signature.toString();
    return "MethodGenC(" + holderGenc.getName().getString() + ">>" + sig + ")";
  }

  @Override
  public LexicalScope getOuterScopeOrNull() {
    return currentScope.getOuterScopeOrNull();
  }

  @Override
  public LexicalScope getScope(final Method method) {
    return currentScope.getScope(method);
  }

  @Override
  public String getName() {
    return getMethodIdentifier();
  }
}
