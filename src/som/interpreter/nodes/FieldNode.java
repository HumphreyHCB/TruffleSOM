/**
 * Copyright (c) 2013 Stefan Marr, stefan.marr@vub.ac.be
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
package som.interpreter.nodes;

import som.compiler.MethodGenerationContext;
import som.vmobjects.Object;

import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.MaterializedFrame;


public abstract class FieldNode extends ContextualNode {

  protected final int fieldIndex;

  public FieldNode(final int fieldIndex, final int contextLevel) {
    super(contextLevel);
    this.fieldIndex = fieldIndex;
  }

  protected Object getSelfFromMaterialized(final MaterializedFrame ctx) {
    try {
      return (Object) ctx.getObject(MethodGenerationContext.getStandardSelfSlot());
    } catch (FrameSlotTypeException e) {
      throw new RuntimeException("uninitialized selfSlot, which should be pretty much imposible???");
    }
  }

  public static class FieldReadNode extends FieldNode {

    public FieldReadNode(final int fieldIndex, final int contextLevel) {
      super(fieldIndex, contextLevel);
    }

    @Override
    public Object executeGeneric(final MaterializedFrame frame) {
      MaterializedFrame ctx = determineContext(frame);
      Object self = getSelfFromMaterialized(ctx);
      return self.getField(fieldIndex);
    }

    @Override
    public ExpressionNode cloneForInlining() {
      return this;
    }
  }

  public static class FieldWriteNode extends FieldNode {

    @Child protected final ExpressionNode exp;

    public FieldWriteNode(final int fieldIndex,
        final int contextLevel,
        final ExpressionNode exp) {
      super(fieldIndex, contextLevel);
      this.exp = adoptChild(exp);
    }

    @Override
    public Object executeGeneric(final MaterializedFrame frame) {
      MaterializedFrame ctx = determineContext(frame);
      Object value = exp.executeGeneric(frame);
      Object self  = getSelfFromMaterialized(ctx);

      self.setField(fieldIndex, value);
      return value;
    }

    @Override
    public ExpressionNode cloneForInlining() {
      return new FieldWriteNode(fieldIndex, contextLevel, exp.cloneForInlining());
    }
  }
}
