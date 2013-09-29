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

import som.vmobjects.Object;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

public class SequenceNode extends ExpressionNode {
  @CompilationFinal @Children private final ExpressionNode[] expressions;

  public SequenceNode(ExpressionNode[] expressions) {
    this.expressions = adoptChildren(expressions);
  }

  @Override
  @ExplodeLoop
  public Object executeGeneric(final MaterializedFrame frame) {
    Object last = null;

    for (int i = 0; i < expressions.length; i++) {
      last = expressions[i].executeGeneric(frame);
    }

    return last;
  }

  @Override
  public ExpressionNode cloneForInlining() {
    ExpressionNode[] exprs = new ExpressionNode[expressions.length];

    for (int i = 0; i < expressions.length; i++) {
      exprs[i] = expressions[i].cloneForInlining();
    }

    return new SequenceNode(exprs);
  }
}
