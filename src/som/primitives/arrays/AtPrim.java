package som.primitives.arrays;

import som.interpreter.nodes.nary.BinaryExpressionNode;
import som.vm.constants.Nil;
import som.vmobjects.SArray;
import som.vmobjects.SArray.ArrayType;

import com.oracle.truffle.api.dsl.Specialization;

public abstract class AtPrim extends BinaryExpressionNode {

  public final static boolean isEmptyType(final SArray receiver) {
    return receiver.getType() == ArrayType.EMPTY;
  }

  public final static boolean isPartiallyEmptyType(final SArray receiver) {
    return receiver.getType() == ArrayType.PARTIAL_EMPTY;
  }

  public final static boolean isObjectType(final SArray receiver) {
    return receiver.getType() == ArrayType.OBJECT;
  }

  public final static boolean isLongType(final SArray receiver) {
    return receiver.getType() == ArrayType.LONG;
  }

  public final static boolean isDoubleType(final SArray receiver) {
    return receiver.getType() == ArrayType.DOUBLE;
  }

  public final static boolean isBooleanType(final SArray receiver) {
    return receiver.getType() == ArrayType.BOOLEAN;
  }

  @Specialization(guards = "isEmptyType")
  public final Object doEmptySArray(final SArray receiver, final long idx) {
    assert idx > 0;
    assert idx <= receiver.getEmptyStorage();
    return Nil.nilObject;
  }

  @Specialization(guards = "isPartiallyEmptyType")
  public final Object doPartiallyEmptySArray(final SArray receiver, final long idx) {
    return receiver.getPartiallyEmptyStorage().get(idx - 1);
  }

  @Specialization(guards = "isObjectType")
  public final Object doObjectSArray(final SArray receiver, final long idx) {
    return receiver.getObjectStorage()[(int) idx - 1];
  }

  @Specialization(guards = "isLongType")
  public final long doLongSArray(final SArray receiver, final long idx) {
    return receiver.getLongStorage()[(int) idx - 1];
  }

  @Specialization(guards = "isDoubleType")
  public final double doDoubleSArray(final SArray receiver, final long idx) {
    return receiver.getDoubleStorage()[(int) idx - 1];
  }

  @Specialization(guards = "isBooleanType")
  public final boolean doBooleanSArray(final SArray receiver, final long idx) {
    return receiver.getBooleanStorage()[(int) idx - 1];
  }
}
