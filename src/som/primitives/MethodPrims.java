package som.primitives;

import som.interpreter.SArguments;
import som.interpreter.nodes.dispatch.InvokeOnCache;
import som.interpreter.nodes.nary.TernaryExpressionNode;
import som.interpreter.nodes.nary.UnaryExpressionNode;
import som.vmobjects.SAbstractObject;
import som.vmobjects.SInvokable;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;


public final class MethodPrims {

  public abstract static class SignaturePrim extends UnaryExpressionNode {
    @Specialization
    public final SAbstractObject doSMethod(final SInvokable receiver) {
      return receiver.getSignature();
    }
  }

  public abstract static class HolderPrim extends UnaryExpressionNode {
    @Specialization
    public final SAbstractObject doSMethod(final SInvokable receiver) {
      return receiver.getHolder();
    }
  }

  public abstract static class InvokeOnPrim extends TernaryExpressionNode {
    @Child private InvokeOnCache callNode;

    public InvokeOnPrim() {
      super(null);
      callNode = InvokeOnCache.create();
    }
    public InvokeOnPrim(final InvokeOnPrim node) { this(); }

    @Specialization
    public final Object doInvoke(final VirtualFrame frame,
        final SInvokable receiver, final Object target, final Object[] somArr) {
      return callNode.executeDispatch(frame, receiver,
          SArguments.somArrayToSArgumentArray(target, somArr));
    }
  }
}
