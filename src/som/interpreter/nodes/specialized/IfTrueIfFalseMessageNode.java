package som.interpreter.nodes.specialized;

import som.interpreter.nodes.ExpressionNode;
import som.interpreter.nodes.MessageNode;
import som.vm.Universe;
import som.vmobjects.Block;
import som.vmobjects.Class;
import som.vmobjects.Method;
import som.vmobjects.Object;
import som.vmobjects.Symbol;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.MaterializedFrame;


public class IfTrueIfFalseMessageNode extends MessageNode {

  private final Class falseClass;
  private final Class trueClass;

  private final Method blockMethodTrueBranch;
  private final Method blockMethodFalseBranch;

  private final Object[] noArgs;

  public IfTrueIfFalseMessageNode(ExpressionNode receiver,
      ExpressionNode[] arguments, Symbol selector, Universe universe,
      Block trueBlock, Block falseBlock) {
    super(receiver, arguments, selector, universe);
    falseClass     = universe.falseObject.getSOMClass();
    trueClass      = universe.trueObject.getSOMClass();

    blockMethodTrueBranch  = trueBlock.getMethod();
    blockMethodFalseBranch = falseBlock.getMethod();

    noArgs = new Object[0];
  }

  @Override
  public Object executeGeneric(final MaterializedFrame frame) {
    // determine receiver, determine arguments is not necessary, because
    // the node is specialized only when  the arguments are literal nodes
    Object rcvr = receiver.executeGeneric(frame);

    return evaluateBody(frame, rcvr);
  }

  public Object evaluateBody(final MaterializedFrame frame, Object rcvr) {
    Class currentRcvrClass = classOfReceiver(rcvr, receiver);

    if (currentRcvrClass == trueClass) {
      Block b = universe.newBlock(blockMethodTrueBranch, frame, 1);
      return blockMethodTrueBranch.invoke(frame.pack(), b, noArgs);
    } else if (currentRcvrClass == falseClass) {
      Block b = universe.newBlock(blockMethodFalseBranch, frame, 1);
      return blockMethodFalseBranch.invoke(frame.pack(), b, noArgs);
    } else {
      return fallbackForNonBoolReceiver(frame, rcvr, currentRcvrClass);
    }
  }

  private Object fallbackForNonBoolReceiver(final MaterializedFrame frame,
      Object rcvr, Class currentRcvrClass) {
    CompilerDirectives.transferToInterpreter();

    // So, it might just be a polymorphic send site.
    PolymorpicMessageNode poly = new PolymorpicMessageNode(receiver,
        arguments, selector, universe, currentRcvrClass);
    Block trueBlock  = universe.newBlock(blockMethodTrueBranch,  frame, 1);
    Block falseBlock = universe.newBlock(blockMethodFalseBranch, frame, 1);
    replace(poly, "Receiver wasn't a boolean. So, we need to do the actual send.");
    return doFullSend(frame, rcvr, new Object[] {trueBlock, falseBlock}, currentRcvrClass);
  }
}
