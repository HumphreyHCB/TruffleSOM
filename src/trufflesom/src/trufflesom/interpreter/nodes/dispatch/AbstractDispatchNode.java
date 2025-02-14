package trufflesom.interpreter.nodes.dispatch;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import trufflesom.bdt.inlining.nodes.WithSource;
import trufflesom.bdt.source.SourceCoordinate;
import trufflesom.interpreter.nodes.AbstractMessageSendNode;
import trufflesom.tools.nodestats.Tags.AnyNode;
import trufflesom.vm.VmSettings;


@GenerateWrapper
public abstract class AbstractDispatchNode extends Node
    implements DispatchChain, InstrumentableNode, WithSource {
  public static final int INLINE_CACHE_SIZE = 6;

  public abstract Object executeDispatch(VirtualFrame frame, Object[] arguments);

  @Override
  public boolean isInstrumentable() {
    return true;
  }

  @Override
  @SuppressWarnings("unchecked")
  public AbstractDispatchNode initialize(final long sourceCoord) {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getSourceCoordinate() {
    return getSendNode().getSourceCoordinate();
  }

  @Override
  public Source getSource() {
    return getSendNode().getSource();
  }

  @Override
  public boolean hasSource() {
    return false;
  }

  @Override
  public WrapperNode createWrapper(final ProbeNode probe) {
    return new AbstractDispatchNodeWrapper(this, probe);
  }

  @Override
  public boolean hasTag(final Class<? extends Tag> tag) {
    if (tag == AnyNode.class) {
      return true;
    }
    return false;
  }

  public void notifyAsInserted() {
    if (VmSettings.UseInstrumentation) {
      notifyInserted(this);
    }
  }

  @Override
  public SourceSection getSourceSection() {
    AbstractMessageSendNode send = getSendNode();
    return SourceCoordinate.createSourceSection(send, send.getSourceCoordinate());
  }

  protected AbstractMessageSendNode getSendNode() {
    Node i = this;
    while (i.getParent() instanceof AbstractDispatchNode) {
      i = i.getParent();
    }
    return (AbstractMessageSendNode) i.getParent();
  }
}
