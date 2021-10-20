package trufflesom.tests;

import static trufflesom.vm.SymbolTable.symbolFor;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.junit.Ignore;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;

import bd.tools.structure.StructuralProbe;
import trufflesom.compiler.ClassGenerationContext;
import trufflesom.compiler.Field;
import trufflesom.compiler.Variable;
import trufflesom.interpreter.SomLanguage;
import trufflesom.interpreter.nodes.ExpressionNode;
import trufflesom.interpreter.nodes.literals.BlockNode;
import trufflesom.interpreter.objectstorage.StorageAnalyzer;
import trufflesom.vm.Universe;
import trufflesom.vmobjects.SClass;
import trufflesom.vmobjects.SInvokable;
import trufflesom.vmobjects.SSymbol;


@Ignore("provides just setup")
public class TruffleTestSetup {
  protected final ClassGenerationContext cgenc;

  protected final Universe universe;

  protected final Source sourceForTests;

  protected final StructuralProbe<SSymbol, SClass, SInvokable, Field, Variable> probe;

  protected int fieldCount;

  private static Universe getUniverse() {
    return SomLanguage.getCurrent().getUniverse();
  }

  protected TruffleTestSetup() {
    universe = getUniverse();
    probe = null;

    cgenc = new ClassGenerationContext(universe, null);
    cgenc.setName(symbolFor("Test"));

    sourceForTests = SomLanguage.getSyntheticSource("dummy-source", "test");
  }

  private static void initTruffle() {
    StorageAnalyzer.initAccessors();

    Builder builder = Universe.createContextBuilder();
    builder.logHandler(System.err);

    Context context = builder.build();
    context.eval(SomLanguage.INIT);

    Universe u = getUniverse();
    Source s = SomLanguage.getSyntheticSource("self", "self");
    u.selfSource = s.createSection(1);
  }

  protected void addField(final String name) {
    fieldCount += 1;
    cgenc.addInstanceField(symbolFor(name),
        sourceForTests.createSection(1, fieldCount, 1));
  }

  private static java.lang.reflect.Field lookup(final Class<?> cls, final String fieldName) {
    try {
      return cls.getDeclaredField(fieldName);
    } catch (NoSuchFieldException e) {
      if (cls.getSuperclass() != null) {
        return lookup(cls.getSuperclass(), fieldName);
      }
    } catch (SecurityException e) {
      throw new RuntimeException(e);
    }
    throw new RuntimeException("Didn't find field: " + fieldName);
  }

  protected static Node read(final Object obj, final String fieldName) {
    return read(obj, fieldName, Node.class);
  }

  protected static ExpressionNode read(final Object obj, final String fieldName,
      final int idx) {
    return read(obj, fieldName, ExpressionNode[].class)[idx];
  }

  protected static ExpressionNode[] getBlockExprs(final BlockNode blockNode) {
    return read(read(blockNode.getMethod().getInvokable(), "expressionOrSequence"),
        "expressions", ExpressionNode[].class);
  }

  public static <T> T read(final Object obj, final String fieldName, final Class<T> c) {
    java.lang.reflect.Field field = lookup(obj.getClass(), fieldName);
    field.setAccessible(true);
    try {
      return c.cast(field.get(obj));
    } catch (IllegalAccessException | IllegalArgumentException e) {
      throw new RuntimeException(e);
    }
  }

  static {
    initTruffle();
  }
}
