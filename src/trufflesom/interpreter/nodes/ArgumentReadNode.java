package trufflesom.interpreter.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;

import bd.inlining.ScopeAdaptationVisitor;
import bd.tools.nodes.Invocation;
import trufflesom.compiler.Variable.Argument;
import trufflesom.vmobjects.SSymbol;


public abstract class ArgumentReadNode {

  public static class LocalArgumentReadNode extends ExpressionNode
      implements Invocation<SSymbol> {
    public final int argumentIndex;

    protected final Argument arg;

    public LocalArgumentReadNode(final Argument arg) {
      assert arg.index >= 2;
      this.arg = arg;
      this.argumentIndex = arg.index;
    }

    /** Only to be used in primitives. */
    public LocalArgumentReadNode(final boolean useInPrim, final int idx) {
      assert idx >= 2;
      this.arg = null;
      this.argumentIndex = idx;
      assert useInPrim;
    }

    @Override
    public final Object executeGeneric(final VirtualFrame frame) {
      return frame.getArguments()[argumentIndex];
    }

    @Override
    public void replaceAfterScopeChange(final ScopeAdaptationVisitor inliner) {
      inliner.updateRead(arg, this, 0);
    }

    @Override
    public SSymbol getInvocationIdentifier() {
      return arg.name;
    }

    public boolean isSelfRead() {
      return argumentIndex == 0;
    }

    @Override
    public String toString() {
      return "ArgRead(" + arg.name + ")";
    }
  }

  public static class LocalArgument1ReadNode extends ExpressionNode
      implements Invocation<SSymbol> {

    protected final Argument arg;

    public LocalArgument1ReadNode(final Argument arg) {
      this.arg = arg;
    }

    /** Only to be used in primitives. */
    public LocalArgument1ReadNode(final boolean useInPrim) {
      this.arg = null;
      assert useInPrim;
    }

    @Override
    public final Object executeGeneric(final VirtualFrame frame) {
      return frame.getArgument1();
    }

    @Override
    public void replaceAfterScopeChange(final ScopeAdaptationVisitor inliner) {
      inliner.updateRead(arg, this, 0);
    }

    @Override
    public SSymbol getInvocationIdentifier() {
      return arg.name;
    }

    public boolean isSelfRead() {
      return true;
    }

    @Override
    public String toString() {
      return "ArgRead(" + arg.name + ")";
    }
  }

  public static class LocalArgument2ReadNode extends ExpressionNode
      implements Invocation<SSymbol> {

    protected final Argument arg;

    public LocalArgument2ReadNode(final Argument arg) {
      this.arg = arg;
    }

    /** Only to be used in primitives. */
    public LocalArgument2ReadNode(final boolean useInPrim) {
      this.arg = null;
      assert useInPrim;
    }

    @Override
    public final Object executeGeneric(final VirtualFrame frame) {
      return frame.getArgument2();
    }

    @Override
    public void replaceAfterScopeChange(final ScopeAdaptationVisitor inliner) {
      inliner.updateRead(arg, this, 0);
    }

    @Override
    public SSymbol getInvocationIdentifier() {
      return arg.name;
    }

    public boolean isSelfRead() {
      return false;
    }

    @Override
    public String toString() {
      return "ArgRead(" + arg.name + ")";
    }
  }

  public static class LocalArgumentWriteNode extends ExpressionNode {
    protected final int      argumentIndex;
    protected final Argument arg;

    @Child protected ExpressionNode valueNode;

    public LocalArgumentWriteNode(final Argument arg, final ExpressionNode valueNode) {
      assert arg.index >= 0;
      this.arg = arg;
      this.argumentIndex = arg.index;
      this.valueNode = valueNode;
    }

    /** Only to be used in primitives. */
    public LocalArgumentWriteNode(final boolean useInPrim, final int idx,
        final ExpressionNode valueNode) {
      assert idx >= 0;
      this.arg = null;
      this.argumentIndex = idx;
      assert useInPrim;
      this.valueNode = valueNode;
    }

    @Override
    public final Object executeGeneric(final VirtualFrame frame) {
      Object value = valueNode.executeGeneric(frame);
      frame.getArguments()[argumentIndex] = value;
      return value;
    }

    @Override
    public void replaceAfterScopeChange(final ScopeAdaptationVisitor inliner) {
      inliner.updateWrite(arg, this, valueNode, 0);
    }
  }

  public static class NonLocalArgumentReadNode extends ContextualNode
      implements Invocation<SSymbol> {
    protected final int      argumentIndex;
    protected final Argument arg;

    public NonLocalArgumentReadNode(final Argument arg, final int contextLevel) {
      super(contextLevel);
      assert contextLevel > 0;
      assert arg.index >= 2;
      this.arg = arg;
      this.argumentIndex = arg.index;
    }

    @Override
    public final Object executeGeneric(final VirtualFrame frame) {
      return determineContext(frame).getArguments()[argumentIndex];
    }

    @Override
    public void replaceAfterScopeChange(final ScopeAdaptationVisitor inliner) {
      inliner.updateRead(arg, this, contextLevel);
    }

    @Override
    public SSymbol getInvocationIdentifier() {
      return arg.name;
    }

    @Override
    public String toString() {
      return "ArgRead(" + arg.name + ", ctx: " + contextLevel + ")";
    }
  }

  public static class NonLocalArgument1ReadNode extends ContextualNode
      implements Invocation<SSymbol> {

    protected final Argument arg;

    public NonLocalArgument1ReadNode(final Argument arg, final int contextLevel) {
      super(contextLevel);
      assert contextLevel > 0;
      this.arg = arg;
    }

    @Override
    public final Object executeGeneric(final VirtualFrame frame) {
      return determineContext(frame).getArgument1();
    }

    @Override
    public void replaceAfterScopeChange(final ScopeAdaptationVisitor inliner) {
      inliner.updateRead(arg, this, contextLevel);
    }

    @Override
    public SSymbol getInvocationIdentifier() {
      return arg.name;
    }

    @Override
    public String toString() {
      return "ArgRead(" + arg.name + ", ctx: " + contextLevel + ")";
    }
  }

  public static class NonLocalArgument2ReadNode extends ContextualNode
      implements Invocation<SSymbol> {

    protected final Argument arg;

    public NonLocalArgument2ReadNode(final Argument arg, final int contextLevel) {
      super(contextLevel);
      assert contextLevel > 0;
      this.arg = arg;
    }

    @Override
    public final Object executeGeneric(final VirtualFrame frame) {
      return determineContext(frame).getArgument2();
    }

    @Override
    public void replaceAfterScopeChange(final ScopeAdaptationVisitor inliner) {
      inliner.updateRead(arg, this, contextLevel);
    }

    @Override
    public SSymbol getInvocationIdentifier() {
      return arg.name;
    }

    @Override
    public String toString() {
      return "ArgRead(" + arg.name + ", ctx: " + contextLevel + ")";
    }
  }

  public static class NonLocalArgumentWriteNode extends ContextualNode {
    protected final int      argumentIndex;
    protected final Argument arg;

    @Child protected ExpressionNode valueNode;

    public NonLocalArgumentWriteNode(final Argument arg, final int contextLevel,
        final ExpressionNode valueNode) {
      super(contextLevel);
      assert contextLevel > 0;
      this.arg = arg;
      this.argumentIndex = arg.index;

      this.valueNode = valueNode;
    }

    @Override
    public final Object executeGeneric(final VirtualFrame frame) {
      Object value = valueNode.executeGeneric(frame);
      determineContext(frame).getArguments()[argumentIndex] = value;
      return value;
    }

    @Override
    public void replaceAfterScopeChange(final ScopeAdaptationVisitor inliner) {
      inliner.updateWrite(arg, this, valueNode, contextLevel);
    }
  }
}
