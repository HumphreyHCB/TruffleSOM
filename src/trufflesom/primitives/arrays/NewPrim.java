package trufflesom.primitives.arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;

import bd.primitives.Primitive;
import bd.primitives.Specializer;
import trufflesom.interpreter.SomLanguage;
import trufflesom.interpreter.nodes.ExpressionNode;
import trufflesom.interpreter.nodes.nary.BinaryExpressionNode.BinarySystemOperation;
import trufflesom.vm.Universe;
import trufflesom.vmobjects.SArray;
import trufflesom.vmobjects.SSymbol;


@Primitive(className = "Array", primitive = "new:", selector = "new:", classSide = true,
    inParser = false, specializer = NewPrim.IsArrayClass.class)
public abstract class NewPrim extends BinarySystemOperation {

  public static class IsArrayClass extends Specializer<Universe, ExpressionNode, SSymbol> {
    @CompilationFinal private Universe universe;

    public IsArrayClass(final Primitive prim, final NodeFactory<ExpressionNode> fact) {
      super(prim, fact);
    }

    @Override
    public boolean matches(final Object[] args, final ExpressionNode[] argNodes) {
      if (universe == null) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        universe = SomLanguage.getCurrentContext(argNodes[0]);
      }
      return args[0] == universe.arrayClass;
    }
  }

  @Specialization(guards = "receiver == universe.arrayClass")
  public final SArray doSClass(final DynamicObject receiver, final long length) {
    return new SArray(length);
  }
}
