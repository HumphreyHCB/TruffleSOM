package som.matenodes;

import som.matenodes.MateAbstractReflectiveDispatch.MateAbstractStandardDispatch;
import som.matenodes.MateAbstractSemanticNodes.MateSemanticCheckNode;
import com.oracle.truffle.api.frame.VirtualFrame;

public interface MateBehavior {

  public abstract MateSemanticCheckNode getMateNode();

  public abstract MateAbstractStandardDispatch getMateDispatch();

  public default Object doMateSemantics(final VirtualFrame frame,
      final Object[] arguments) {
    return this.getMateDispatch().executeDispatch(frame,
        this.getMateNode().execute(frame, arguments), arguments);
  }
}
