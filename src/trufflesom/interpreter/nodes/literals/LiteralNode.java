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
package trufflesom.interpreter.nodes.literals;

import java.math.BigInteger;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;

import bd.inlining.nodes.Inlinable;
import bd.primitives.nodes.PreevaluatedExpression;
import trufflesom.compiler.MethodGenerationContext;
import trufflesom.interpreter.nodes.ExpressionNode;
import trufflesom.interpreter.nodes.GlobalNode.FalseGlobalNode;
import trufflesom.interpreter.nodes.GlobalNode.NilGlobalNode;
import trufflesom.interpreter.nodes.GlobalNode.TrueGlobalNode;
import trufflesom.vm.constants.Nil;
import trufflesom.vmobjects.SArray;
import trufflesom.vmobjects.SBlock;
import trufflesom.vmobjects.SSymbol;


@NodeInfo(cost = NodeCost.NONE)
public abstract class LiteralNode extends ExpressionNode
    implements PreevaluatedExpression, Inlinable<MethodGenerationContext> {
  public static ExpressionNode create(final Object literal) {
    if (literal instanceof SArray) {
      return new ArrayLiteralNode((SArray) literal);
    }

    if (literal instanceof BigInteger) {
      return new BigIntegerLiteralNode((BigInteger) literal);
    }

    if (literal instanceof SBlock) {
      throw new IllegalArgumentException(
          "SBlock isn't supported here, BlockNodes need to be constructed directly.");
    }

    if (literal instanceof Long) {
      return new IntegerLiteralNode((Long) literal);
    }

    if (literal instanceof Double) {
      return new DoubleLiteralNode((Double) literal);
    }

    if (literal instanceof String) {
      return new StringLiteralNode((String) literal);
    }

    if (literal instanceof SSymbol) {
      return new SymbolLiteralNode((SSymbol) literal);
    }

    if (literal == Boolean.TRUE) {
      return new TrueGlobalNode(null);
    }

    if (literal == Boolean.FALSE) {
      return new FalseGlobalNode(null);
    }

    if (literal == Nil.nilObject) {
      return new NilGlobalNode(null);
    }

    throw new IllegalAccessError(
        "Can't create a literal node for " + literal.getClass().getSimpleName());
  }

  @Override
  public final Object doPreEvaluated(final VirtualFrame frame, final Object[] arguments) {
    return executeGeneric(frame);
  }

  @Override
  public final Object doPreUnary(final VirtualFrame frame, final Object rcvr) {
    return executeGeneric(frame);
  }

  @Override
  public final Object doPreBinary(final VirtualFrame frame, final Object rcvr,
      final Object arg) {
    return executeGeneric(frame);
  }

  @Override
  public final Object doPreTernary(final VirtualFrame frame, final Object rcvr,
      final Object arg1, final Object arg2) {
    return executeGeneric(frame);
  }

  @Override
  public final Object doPreQuat(final VirtualFrame frame, final Object rcvr, final Object arg1,
      final Object arg2, final Object arg3) {
    return executeGeneric(frame);
  }

  @Override
  public ExpressionNode inline(final MethodGenerationContext mgenc) {
    return this;
  }

  @Override
  public boolean isTrivial() {
    return true;
  }

  @Override
  public PreevaluatedExpression copyTrivialNode() {
    return (PreevaluatedExpression) copy();
  }
}
