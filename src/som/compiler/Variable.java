package som.compiler;

import static som.interpreter.SNodeFactory.createArgumentRead;
import static som.interpreter.SNodeFactory.createLocalVarRead;
import static som.interpreter.SNodeFactory.createSuperRead;
import static som.interpreter.SNodeFactory.createVariableWrite;
import static som.interpreter.TruffleCompiler.transferToInterpreterAndInvalidate;
import som.interpreter.nodes.ExpressionNode;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.source.SourceSection;

public abstract class Variable {
  public final String name;

  Variable(final String name) {
    this.name = name;
  }

  @Override
  public String toString() {
    return getClass().getName() + "(" + name + ")";
  }

  public abstract ExpressionNode getReadNode(final int contextLevel,
      final SourceSection source);

  public static final class Argument extends Variable {
    public final int index;

    Argument(final String name, final int index) {
      super(name);
      this.index = index;
    }

    public boolean isSelf() {
      return "self".equals(name) || "$blockSelf".equals(name);
    }

    @Override
    public final ExpressionNode getReadNode(final int contextLevel,
        final SourceSection source) {
      transferToInterpreterAndInvalidate("Variable.getReadNode");
      return createArgumentRead(this, contextLevel, source);
    }

    public final ExpressionNode getSuperReadNode(final int contextLevel,
        final SSymbol holderClass, final boolean classSide,
        final SourceSection source) {
      return createSuperRead(contextLevel, holderClass, classSide, source);
    }
  }

  public static final class Local extends Variable {
    private final FrameSlot slot;

    Local(final String name, final FrameSlot slot) {
      super(name);
      this.slot = slot;
    }

    public final FrameSlot getSlot() {
      return slot;
    }

    public final Object getSlotIdentifier() {
      return slot.getIdentifier();
    }

    public Local cloneForInlining(final FrameSlot inlinedSlot) {
      return new Local(name, inlinedSlot);
    }

    @Override
    public final ExpressionNode getReadNode(final int contextLevel,
        final SourceSection source) {
      transferToInterpreterAndInvalidate("Variable.getReadNode");
      return createLocalVarRead(this, contextLevel, source);
    }

    public ExpressionNode getWriteNode(final int contextLevel,
        final ExpressionNode valueExpr, final SourceSection source) {
      transferToInterpreterAndInvalidate("Variable.getWriteNode");
      return createVariableWrite(this, contextLevel, valueExpr, source);
    }
  }
}
