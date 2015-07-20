/**
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

package som.vmobjects;

import som.interpreter.SArguments;
import som.primitives.BlockPrimsFactory.ValueMorePrimFactory;
import som.primitives.BlockPrimsFactory.ValueNonePrimFactory;
import som.primitives.BlockPrimsFactory.ValueOnePrimFactory;
import som.primitives.BlockPrimsFactory.ValueTwoPrimFactory;
import som.primitives.Primitives;
import som.vm.Universe;
import som.vm.constants.Blocks;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.MaterializedFrame;

public abstract class SBlock extends SAbstractObject {

  public static SBlock create(final SInvokable blockMethod,
      final MaterializedFrame context) {
    switch (blockMethod.getNumberOfArguments()) {
      case 1: return new SBlock1(blockMethod, context);
      case 2: return new SBlock2(blockMethod, context);
      case 3: return new SBlock3(blockMethod, context);
    }

    CompilerDirectives.transferToInterpreter();
    throw new RuntimeException("We do currently not have support for more than 3 arguments to a block.");
  }

  public static final class SBlock1 extends SBlock {
    public SBlock1(final SInvokable blockMethod, final MaterializedFrame context) {
      super(blockMethod, context);
    }

    @Override
    public SClass getSOMClass() {
      return Blocks.blockClass1;
    }
  }

  public static final class SBlock2 extends SBlock {
    public SBlock2(final SInvokable blockMethod, final MaterializedFrame context) {
      super(blockMethod, context);
    }

    @Override
    public SClass getSOMClass() {
      return Blocks.blockClass2;
    }
  }

  public static final class SBlock3 extends SBlock {
    public SBlock3(final SInvokable blockMethod, final MaterializedFrame context) {
      super(blockMethod, context);
    }

    @Override
    public SClass getSOMClass() {
      return Blocks.blockClass3;
    }
  }

  public SBlock(final SInvokable blockMethod, final MaterializedFrame context) {
    this.method  = blockMethod;
    this.context = context;
  }

  public final SInvokable getMethod() {
    return method;
  }

  public final MaterializedFrame getContext() {
    assert context != null;
    return context;
  }

  public final Object getOuterSelf() {
    return SArguments.rcvr(getContext());
  }

  public static SInvokable getEvaluationPrimitive(final int numberOfArguments,
      final Universe universe, final SClass rcvrClass) {
    CompilerAsserts.neverPartOfCompilation("SBlock.getEvaluationPrimitive(...)");
    SSymbol sig = universe.symbolFor(computeSignatureString(numberOfArguments));

    switch (numberOfArguments) {
      case 1: return Primitives.constructPrimitive(sig,
          ValueNonePrimFactory.getInstance(), universe, rcvrClass);
      case 2: return Primitives.constructPrimitive(sig,
          ValueOnePrimFactory.getInstance(), universe, rcvrClass);
      case 3: return Primitives.constructPrimitive(sig,
          ValueTwoPrimFactory.getInstance(), universe, rcvrClass);
      case 4: return Primitives.constructPrimitive(sig,
          ValueMorePrimFactory.getInstance(), universe, rcvrClass);
      default:
        throw new RuntimeException("Should not reach here. SOM only has blocks with up to 2 arguments.");
    }
  }

  private static String computeSignatureString(final int numberOfArguments) {
    // Compute the signature string
    String signatureString = "value";
    if (numberOfArguments > 1) { signatureString += ":"; }

    // Add extra value: selector elements if necessary
    for (int i = 2; i < numberOfArguments; i++) {
      signatureString += "with:";
    }
    return signatureString;
  }

  private final SInvokable        method;
  private final MaterializedFrame context;
}
