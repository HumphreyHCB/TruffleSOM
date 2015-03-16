package som.primitives;

import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vm.constants.Classes;
import som.vmobjects.SClass;

import com.oracle.truffle.api.dsl.Specialization;


public abstract class DoublePrims  {

  public abstract static class RoundPrim extends UnaryExpressionNode {
    @Specialization
    public final long doDouble(final double receiver) {
      return Math.round(receiver);
    }
  }

  public abstract static class PositiveInfinityPrim extends UnaryExpressionNode {
    protected final boolean receiverIsDoubleClass(final SClass receiver) {
      return receiver == Classes.doubleClass;
    }

    @Specialization(guards = "receiverIsDoubleClass")
    public final double doSClass(final SClass receiver) {
      return Double.POSITIVE_INFINITY;
    }
  }
}
