package som.primitives;

import java.math.BigInteger;

import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.primitives.arithmetic.ArithmeticPrim;
import som.vm.constants.Classes;
import som.vmobjects.SClass;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.utilities.BranchProfile;

public abstract class IntegerPrims {

  public abstract static class RandomPrim extends UnaryExpressionNode {
    @Specialization
    public final long doLong(final long receiver) {
      return (long) (receiver * Math.random());
    }
  }

  public abstract static class As32BitSignedValue extends UnaryExpressionNode {
    @Specialization
    public final long doLong(final long receiver) {
      return (int) receiver;
    }
  }

  public abstract static class As32BitUnsignedValue extends UnaryExpressionNode {
    @Specialization
    public final long doLong(final long receiver) {
      return Integer.toUnsignedLong((int) receiver);
    }
  }

  public abstract static class FromStringPrim extends ArithmeticPrim {
    protected final boolean receiverIsIntegerClass(final SClass receiver) {
      return receiver == Classes.integerClass;
    }

    @Specialization(guards = "receiverIsIntegerClass")
    public final Object doSClass(final SClass receiver, final String argument) {
      return Long.parseLong(argument);
    }

    @Specialization(guards = "receiverIsIntegerClass")
    public final Object doSClass(final SClass receiver, final SSymbol argument) {
      return Long.parseLong(argument.getString());
    }
  }

  public abstract static class LeftShiftPrim extends ArithmeticPrim {
    private final BranchProfile overflow = BranchProfile.create();

    @Specialization(rewriteOn = ArithmeticException.class)
    public final long doLong(final long receiver, final long right) {
      assert right >= 0;  // currently not defined for negative values of right

      if (Long.SIZE - Long.numberOfLeadingZeros(receiver) + right > Long.SIZE - 1) {
        overflow.enter();
        throw new ArithmeticException("shift overflows long");
      }
      return receiver << right;
    }

    @Specialization
    public final BigInteger doLongWithOverflow(final long receiver, final long right) {
      assert right >= 0;  // currently not defined for negative values of right
      assert right <= Integer.MAX_VALUE;

      return BigInteger.valueOf(receiver).shiftLeft((int) right);
    }
  }

  public abstract static class UnsignedRightShiftPrim extends ArithmeticPrim {
    @Specialization
    public final long doLong(final long receiver, final long right) {
      return receiver >>> right;
    }
  }

  public abstract static class MaxIntPrim extends ArithmeticPrim {
    @Specialization
    public final long doLong(final long receiver, final long right) {
      return Math.max(receiver, right);
    }
  }

  public abstract static class ToPrim extends BinaryExpressionNode {
    @Specialization
    public final Object[] doLong(final long receiver, final long right) {
      int cnt = (int) right - (int) receiver + 1;
      Object[] arr = new Object[cnt];
      for (int i = 0; i < cnt; i++) {
        arr[i] = i + receiver;
      }
      return arr;
    }
  }

  public abstract static class AbsPrim extends UnaryExpressionNode {
    @Specialization
    public final long doLong(final long receiver) {
      return Math.abs(receiver);
    }
  }
}
