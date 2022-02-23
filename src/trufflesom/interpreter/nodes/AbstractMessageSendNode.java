package trufflesom.interpreter.nodes;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;

import bd.primitives.nodes.PreevaluatedExpression;
import bd.tools.nodes.Invocation;
import trufflesom.interpreter.nodes.dispatch.GenericDispatchNode;
import trufflesom.vmobjects.SSymbol;


public abstract class AbstractMessageSendNode extends ExpressionNode
    implements PreevaluatedExpression, Invocation<SSymbol> {

  @Children protected final ExpressionNode[] argumentNodes;

  protected AbstractMessageSendNode(final ExpressionNode[] arguments) {
    this.argumentNodes = arguments;
  }

  @Override
  public Object executeGeneric(final VirtualFrame frame) {
    Object[] arguments = evaluateArguments(frame);
    return doPreEvaluated(frame, arguments);
  }

  @ExplodeLoop
  private Object[] evaluateArguments(final VirtualFrame frame) {
    Object[] arguments = new Object[argumentNodes.length];
    for (int i = 0; i < argumentNodes.length; i++) {
      arguments[i] = argumentNodes[i].executeGeneric(frame);
      assert arguments[i] != null;
    }
    return arguments;
  }

  public ExpressionNode[] getArguments() {
    return argumentNodes;
  }

  public abstract int getNumberOfArguments();

  public abstract void replaceDispatchListHead(GenericDispatchNode replacement);

  public abstract void notifyDispatchInserted();
}
