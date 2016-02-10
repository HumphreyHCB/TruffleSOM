package som.matenodes;

import som.interpreter.SArguments;
import som.interpreter.nodes.ISuperReadNode;
import som.interpreter.nodes.MateMethodActivationNode;
import som.vm.MateUniverse;
import som.vm.constants.ExecutionLevel;
import som.vmobjects.SArray;
import som.vmobjects.SInvokable;
import som.vmobjects.SObject;
import som.vmobjects.SSymbol;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.object.basic.DynamicObjectBasic;

public abstract class MateAbstractReflectiveDispatch extends Node {

  protected final static int INLINE_CACHE_SIZE = 6;

  public MateAbstractReflectiveDispatch(final SourceSection source) {
    super(source);
  }

  protected Object[] computeArgumentsForMetaDispatch(final VirtualFrame frame, final Object[] arguments) {
    return SArguments.createSArguments(SArguments.getEnvironment(frame), ExecutionLevel.Meta, arguments);
  }

  public DirectCallNode createDispatch(final SInvokable metaMethod) {
    return MateUniverse.current().getTruffleRuntime()
        .createDirectCallNode(metaMethod.getCallTarget());
  }

  public abstract static class MateDispatchFieldAccessor extends
      MateAbstractReflectiveDispatch {

    public MateDispatchFieldAccessor(final SourceSection source) {
      super(source);
    }
  }

  @Override
  public NodeCost getCost() {
    return NodeCost.NONE;
  }

  public abstract static class MateAbstractStandardDispatch extends
      MateAbstractReflectiveDispatch {

    public MateAbstractStandardDispatch(final SourceSection source) {
      super(source);
    }

    public abstract Object executeDispatch(final VirtualFrame frame,
        SInvokable method, Object[] arguments);

    @Specialization(guards = "cachedMethod==method")
    public Object doMateNode(final VirtualFrame frame, final SInvokable method,
        final Object[] arguments,
        @Cached("method") final SInvokable cachedMethod,
        @Cached("createDispatch(method)") final DirectCallNode reflectiveMethod) {
      Object value = reflectiveMethod.call(frame, this.computeArgumentsForMetaDispatch(frame, arguments));
      return value;
    }
  }

  public abstract static class MateDispatchFieldAccess extends
      MateAbstractStandardDispatch {

    public MateDispatchFieldAccess(final SourceSection source) {
      super(source);
    }
  }

  public abstract static class MateDispatchMessageLookup extends
      MateAbstractStandardDispatch {

    private final SSymbol    selector;
    @Child MateMethodActivationNode activationNode;

    public MateDispatchMessageLookup(final SourceSection source, final SSymbol sel) {
      super(source);
      selector = sel;
      activationNode = new MateMethodActivationNode();
    }

    @Override
    @Specialization(guards = {"cachedMethod==method"})
    public Object doMateNode(final VirtualFrame frame, final SInvokable method,
        final Object[] arguments,
        @Cached("method") final SInvokable cachedMethod,
        @Cached("createDispatch(method)") final DirectCallNode reflectiveMethod) {
      // The MOP receives the class where the lookup must start (find: aSelector since: aClass)
      SInvokable actualMethod = this.reflectiveLookup(frame, reflectiveMethod, arguments);
      return activationNode.doActivation(frame, actualMethod, arguments);
    }

    public SInvokable reflectiveLookup(final VirtualFrame frame, final DirectCallNode reflectiveMethod,
        final Object[] arguments) {
      DynamicObjectBasic receiver = SObject.castDynObj(arguments[0]);
      Object[] args = { SArguments.getEnvironment(frame), ExecutionLevel.Meta, receiver, this.getSelector(), this.lookupSinceFor(receiver)};
      return (SInvokable) reflectiveMethod.call(frame, args);
    }

    protected DynamicObjectBasic lookupSinceFor(final DynamicObjectBasic receiver){
      return SObject.getSOMClass(receiver);
    }

    protected SSymbol getSelector() {
      return selector;
    }
  }

  public abstract static class MateDispatchSuperMessageLookup extends MateDispatchMessageLookup{
    ISuperReadNode superNode;

    public MateDispatchSuperMessageLookup(final SourceSection source, final SSymbol sel, final ISuperReadNode node) {
      super(source, sel);
      superNode = node;
    }

    @Override
    protected DynamicObjectBasic lookupSinceFor(final DynamicObjectBasic receiver){
      return superNode.getLexicalSuperClass();
    }
  }

  public abstract static class MateCachedDispatchMessageLookup extends
    MateDispatchMessageLookup {

    public MateCachedDispatchMessageLookup(final SourceSection source, final SSymbol sel) {
      super(source, sel);
    }

    @Specialization(guards = {"cachedMethod==method", "classOfReceiver(arguments) == cachedClass"},
        insertBefore="doMateNode", limit = "INLINE_CACHE_SIZE")
    public Object doMateNodeCached(final VirtualFrame frame, final SInvokable method,
        final Object[] arguments,
        @Cached("method") final SInvokable cachedMethod,
        @Cached("classOfReceiver(arguments)") final DynamicObjectBasic cachedClass,
        @Cached("lookupResult(frame, method, arguments)") final SInvokable lookupResult){
      // The MOP receives the class where the lookup must start (find: aSelector since: aClass)
      return activationNode.doActivation(frame, lookupResult, arguments);
    }

    @Specialization(guards = {"cachedMethod==method"}, contains = {"doMateNodeCached"}, insertBefore="doMateNode")
    public Object doMegaMorphic(final VirtualFrame frame, final SInvokable method,
        final Object[] arguments,
        @Cached("method") final SInvokable cachedMethod,
        @Cached("createDispatch(method)") final DirectCallNode reflectiveMethod) {
      return super.doMateNode(frame, method, arguments, cachedMethod, reflectiveMethod);
    }

    public SInvokable lookupResult(final VirtualFrame frame, final SInvokable method,
        final Object[] arguments){
      return this.reflectiveLookup(frame, this.createDispatch(method), arguments);
    }

    protected DynamicObjectBasic classOfReceiver(final Object[] arguments){
      return SObject.getSOMClass(SObject.castDynObj(arguments[0]));
    }

  }

  public abstract static class MateCachedDispatchSuperMessageLookup extends MateCachedDispatchMessageLookup{
    ISuperReadNode superNode;

    public MateCachedDispatchSuperMessageLookup(final SourceSection source, final SSymbol sel, final ISuperReadNode node) {
      super(source, sel);
      superNode = node;
    }

    @Override
    protected DynamicObjectBasic lookupSinceFor(final DynamicObjectBasic receiver){
      return superNode.getLexicalSuperClass();
    }
  }

  public abstract static class MateActivationDispatch extends
      MateAbstractReflectiveDispatch {

    public MateActivationDispatch(final SourceSection source) {
      super(source);
    }

    public abstract Object executeDispatch(final VirtualFrame frame,
        SInvokable method, SInvokable methodToActivate, Object[] arguments);

    @SuppressWarnings("unchecked")
    @Specialization(guards = {"cachedMethod==method","methodToActivate == cachedMethodToActivate"}, limit = "INLINE_CACHE_SIZE")
    public Object doMetaLevel(final VirtualFrame frame,
        final SInvokable method, final SInvokable methodToActivate,
        final Object[] arguments,
        @Cached("method") final SInvokable cachedMethod,
        @Cached("methodToActivate") final SInvokable cachedMethodToActivate,
        @Cached("createDirectCall(methodToActivate)") final DirectCallNode callNode,
        @Cached("createDispatch(method)") final DirectCallNode reflectiveMethod) {
      // The MOP receives the standard ST message Send stack (rcvr, method, arguments) and returns its own
      Object[] args = { SArguments.getEnvironment(frame), ExecutionLevel.Meta, SObject.castDynObj(arguments[0]), methodToActivate,
          SArray.create(SArguments.createSArguments(SArguments.getEnvironment(frame), ExecutionLevel.Base, arguments))};
      SArray realArguments = (SArray) reflectiveMethod.call(frame, args);
      return callNode.call(frame, realArguments.toJavaArray());
    }

    @Specialization(guards = {"cachedMethod==method"}, contains = "doMetaLevel")
    public Object doMegamorphicMetaLevel(final VirtualFrame frame,
        final SInvokable method, final SInvokable methodToActivate,
        final Object[] arguments,
        @Cached("method") final SInvokable cachedMethod,
        @Cached("createDispatch(method)") final DirectCallNode reflectiveMethod,
        @Cached("createIndirectCall()") final IndirectCallNode callNode) {
      /// THIS Change does nothing on NBody...

      Object[] args = { SArguments.getEnvironment(frame), ExecutionLevel.Meta, SObject.castDynObj(arguments[0]), methodToActivate,
          SArray.create(SArguments.createSArguments(SArguments.getEnvironment(frame), ExecutionLevel.Base, arguments))};
      SArray realArguments = (SArray) reflectiveMethod.call(frame, args);
      return callNode.call(frame, methodToActivate.getCallTarget(), realArguments.toJavaArray());
    }
  }

  public static DirectCallNode createDirectCall(final SInvokable methodToActivate){
    return DirectCallNode.create(methodToActivate.getCallTarget());
  }

  public static IndirectCallNode createIndirectCall(){
    return IndirectCallNode.create();
  }
}
