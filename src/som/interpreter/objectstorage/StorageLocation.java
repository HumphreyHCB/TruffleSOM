/*package som.interpreter.objectstorage;

import java.lang.reflect.Field;

import som.interpreter.TruffleCompiler;
import som.interpreter.objectstorage.FieldAccessorNode.AbstractReadFieldNode;
import som.interpreter.objectstorage.FieldAccessorNode.AbstractWriteFieldNode;
import som.interpreter.objectstorage.FieldAccessorNode.ReadDoubleFieldNode;
import som.interpreter.objectstorage.FieldAccessorNode.ReadLongFieldNode;
import som.interpreter.objectstorage.FieldAccessorNode.ReadObjectFieldNode;
import som.interpreter.objectstorage.FieldAccessorNode.ReadUnwrittenFieldNode;
import som.interpreter.objectstorage.FieldAccessorNode.WriteDoubleFieldNode;
import som.interpreter.objectstorage.FieldAccessorNode.WriteLongFieldNode;
import som.interpreter.objectstorage.FieldAccessorNode.WriteObjectFieldNode;
import som.vm.constants.Nil;
import som.vmobjects.SObject;
import sun.misc.Unsafe;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.UnexpectedResultException;
import com.oracle.truffle.api.unsafe.UnsafeAccess;
import com.oracle.truffle.api.unsafe.UnsafeAccessFactory;


public abstract class StorageLocation {
  private static Unsafe loadUnsafe() {
    try {
        return Unsafe.getUnsafe();
    } catch (SecurityException e) {
    }
    try {
        Field theUnsafeInstance = Unsafe.class.getDeclaredField("theUnsafe");
        theUnsafeInstance.setAccessible(true);
        return (Unsafe) theUnsafeInstance.get(Unsafe.class);
    } catch (Exception e) {
        throw new RuntimeException("exception while trying to get Unsafe.theUnsafe via reflection:", e);
    }
}

  private static final UnsafeAccess ua = Truffle.getRuntime().
      getCapability(UnsafeAccessFactory.class).createUnsafeAccess(loadUnsafe());

  public interface LongStorageLocation {
    long readLong(final SObject obj, boolean assumptionValid) throws UnexpectedResultException;
    void writeLong(final SObject obj, final long value);
  }

  public interface DoubleStorageLocation {
    double readDouble(final SObject obj, boolean assumptionValid) throws UnexpectedResultException;
    void   writeDouble(final SObject obj, final double value);
  }

  public static StorageLocation createForLong(final ObjectLayout layout,
      final int primFieldIndex) {
    CompilerAsserts.neverPartOfCompilation("StorageLocation");
    if (primFieldIndex < SObject.NUM_PRIMITIVE_FIELDS) {
      return new LongDirectStoreLocation(layout, primFieldIndex);
    } else {
      return new LongArrayStoreLocation(layout, primFieldIndex);
    }
  }

  public static StorageLocation createForDouble(final ObjectLayout layout,
      final int primFieldIndex) {
    CompilerAsserts.neverPartOfCompilation("StorageLocation");
    if (primFieldIndex < SObject.NUM_PRIMITIVE_FIELDS) {
      return new DoubleDirectStoreLocation(layout, primFieldIndex);
    } else {
      return new DoubleArrayStoreLocation(layout, primFieldIndex);
    }
  }

  public static StorageLocation createForObject(final ObjectLayout layout,
      final int objFieldIndex) {
    CompilerAsserts.neverPartOfCompilation("StorageLocation");
    if (objFieldIndex < SObject.NUM_PRIMITIVE_FIELDS) {
      return new ObjectDirectStorageLocation(layout, objFieldIndex);
    } else {
      return new ObjectArrayStorageLocation(layout, objFieldIndex);
    }
  }

  private final ObjectLayout layout; // for debugging only

  protected StorageLocation(final ObjectLayout layout) {
    this.layout = layout;
  }

  public abstract boolean isSet(SObject obj, boolean assumptionValid);
  public abstract Object  read(SObject obj,  boolean assumptionValid);
  public abstract void    write(SObject obj, Object value) throws GeneralizeStorageLocationException, UninitalizedStorageLocationException;

  public abstract AbstractReadFieldNode  getReadNode(int fieldIndex, ObjectLayout layout, AbstractReadFieldNode next);
  public abstract AbstractWriteFieldNode getWriteNode(int fieldIndex, ObjectLayout layout, AbstractWriteFieldNode next);


  public final class GeneralizeStorageLocationException extends Exception {
    private static final long serialVersionUID = 4610497040788136337L;
  }

  public final class UninitalizedStorageLocationException extends Exception {
    private static final long serialVersionUID = -3046154908139289066L;
  }

  public static final class UnwrittenStorageLocation extends StorageLocation {
    public UnwrittenStorageLocation(final ObjectLayout layout) {
      super(layout);
    }

    @Override
    public boolean isSet(final SObject obj, final boolean assumptionValid) {
      return false;
    }

    @Override
    public Object read(final SObject obj, final boolean assumptionValid) {
      CompilerAsserts.neverPartOfCompilation("StorageLocation");
      return Nil.nilObject;
    }

    @Override
    public void write(final SObject obj, final Object value) throws UninitalizedStorageLocationException {
      CompilerAsserts.neverPartOfCompilation("StorageLocation");
      throw new UninitalizedStorageLocationException();
    }

    @Override
    public AbstractReadFieldNode getReadNode(final int fieldIndex,
        final ObjectLayout layout, final AbstractReadFieldNode next) {
      CompilerAsserts.neverPartOfCompilation("StorageLocation");
      return new ReadUnwrittenFieldNode(fieldIndex, layout, next);
    }

    @Override
    public AbstractWriteFieldNode getWriteNode(final int fieldIndex,
        final ObjectLayout layout, final AbstractWriteFieldNode next) {
      CompilerAsserts.neverPartOfCompilation("StorageLocation");
      throw new RuntimeException("we should not get here, should we?");
      // return new UninitializedWriteFieldNode(fieldIndex);
    }
  }

  public abstract static class AbstractObjectStorageLocation extends StorageLocation {
    protected final int fieldIndex;

    public AbstractObjectStorageLocation(final ObjectLayout layout, final int fieldIndex) {
      super(layout);
      this.fieldIndex = fieldIndex;
    }

    @Override
    public abstract void write(final SObject obj, final Object value);

    @Override
    public final AbstractReadFieldNode getReadNode(final int fieldIndex,
        final ObjectLayout layout, final AbstractReadFieldNode next) {
      CompilerAsserts.neverPartOfCompilation("StorageLocation");
      return new ReadObjectFieldNode(fieldIndex, layout, next);
    }

    @Override
    public final AbstractWriteFieldNode getWriteNode(final int fieldIndex,
        final ObjectLayout layout, final AbstractWriteFieldNode next) {
      CompilerAsserts.neverPartOfCompilation("StorageLocation");
      return new WriteObjectFieldNode(fieldIndex, layout, next);
    }
  }

  public static final class ObjectDirectStorageLocation extends AbstractObjectStorageLocation {
    private final long fieldOffset;
    public ObjectDirectStorageLocation(final ObjectLayout layout, final int fieldIndex) {
      super(layout, fieldIndex);
      fieldOffset = SObject.getObjectFieldOffset(fieldIndex);
    }

    @Override
    public boolean isSet(final SObject obj, final boolean assumptionValid) {
      assert read(obj, assumptionValid) != null;
      return true;
    }

    @Override
    public Object read(final SObject obj, final boolean assumptionValid) {
      // TODO: for the moment Graal doesn't seem to get the optimizations
      // right, still need to pass in the correct location identifier,
      // which can probably be `this`.
      return ua.getObject(obj, fieldOffset, assumptionValid, null);
    }

    @Override
    public void write(final SObject obj, final Object value) {
      assert value != null;

      // TODO: for the moment Graal doesn't seem to get the optimizations
      // right, still need to pass in the correct location identifier, which can probably be `this`.
      ua.putObject(obj, fieldOffset, value, null);
    }
  }

  public static final class ObjectArrayStorageLocation extends AbstractObjectStorageLocation {
    private final int extensionIndex;
    private final int absoluteIndex;
    public ObjectArrayStorageLocation(final ObjectLayout layout, final int fieldIndex) {
      super(layout, fieldIndex);
      extensionIndex = fieldIndex - SObject.NUM_OBJECT_FIELDS;
      absoluteIndex = Unsafe.ARRAY_OBJECT_BASE_OFFSET + Unsafe.ARRAY_OBJECT_INDEX_SCALE * extensionIndex;
    }

    @Override
    public boolean isSet(final SObject obj, final boolean assumptionValid) {
      assert read(obj, assumptionValid) != null;
      return true;
    }

    @Override
    public Object read(final SObject obj, final boolean assumptionValid) {
      Object[] arr = obj.getExtensionObjFields();

//      return CompilerDirectives.unsafeCast(
//          // TODO: for the moment Graal doesn't seem to get the optimizations
//          // right, still need to pass in the correct location identifier, which can probably be `this`.
//          arr[extensionIndex],
////          CompilerDirectives.unsafeGetObject(arr, absoluteIndex, assumptionValid, null),
//          Object.class, true, true);
      return arr[extensionIndex];
    }

    @Override
    public void write(final SObject obj, final Object value) {
      assert value != null;
      Object[] arr = obj.getExtensionObjFields();

//      // TODO: for the moment Graal doesn't seem to get the optimizations
//      // right, still need to pass in the correct location identifier, which can probably be `this`.
//      CompilerDirectives.unsafePutObject(arr, absoluteIndex, value, null);
      arr[extensionIndex] = value;
    }
  }

  public abstract static class PrimitiveStorageLocation extends StorageLocation {
    protected final int mask;

    protected PrimitiveStorageLocation(final ObjectLayout layout, final int primField) {
      super(layout);
      mask = SObject.getPrimitiveFieldMask(primField);
    }

    @Override
    public final boolean isSet(final SObject obj, final boolean assumptionValid) {
      return obj.isPrimitiveSet(mask);
    }

    protected final void markAsSet(final SObject obj) {
      obj.markPrimAsSet(mask);
    }
  }

  public abstract static class PrimitiveDirectStoreLocation extends PrimitiveStorageLocation {
    protected final long offset;
    public PrimitiveDirectStoreLocation(final ObjectLayout layout, final int primField) {
      super(layout, primField);
      offset = SObject.getPrimitiveFieldOffset(primField);
    }
  }

  public static final class DoubleDirectStoreLocation extends PrimitiveDirectStoreLocation
      implements DoubleStorageLocation {
    public DoubleDirectStoreLocation(final ObjectLayout layout, final int primField) {
      super(layout, primField);
    }

    @Override
    public Object read(final SObject obj, final boolean assumptionValid) {
      try {
        return readDouble(obj, assumptionValid);
      } catch (UnexpectedResultException e) {
        return e.getResult();
      }
    }

    @Override
    public double readDouble(final SObject obj, final boolean assumptionValid) throws UnexpectedResultException {
      if (isSet(obj, assumptionValid)) {
        // TODO: for the moment Graal doesn't seem to get the optimizations
        // right, still need to pass in the correct location identifier, which can probably be `this`.
        return ua.getDouble(obj, offset, assumptionValid, null);
      } else {
        TruffleCompiler.transferToInterpreterAndInvalidate("unstabelized read node");
        throw new UnexpectedResultException(Nil.nilObject);
      }
    }

    @Override
    public void write(final SObject obj, final Object value) throws GeneralizeStorageLocationException {
      assert value != null;
      if (value instanceof Double) {
        writeDouble(obj, (double) value);
      } else {
        assert value != Nil.nilObject;
        TruffleCompiler.transferToInterpreterAndInvalidate("unstabelized read node");
        throw new GeneralizeStorageLocationException();
      }
    }

    @Override
    public void writeDouble(final SObject obj, final double value) {
      ua.putDouble(obj, offset, value, null);
      markAsSet(obj);
    }

    @Override
    public AbstractReadFieldNode getReadNode(final int fieldIndex,
        final ObjectLayout layout, final AbstractReadFieldNode next) {
      CompilerAsserts.neverPartOfCompilation("StorageLocation");
      return new ReadDoubleFieldNode(fieldIndex, layout, next);
    }

    @Override
    public AbstractWriteFieldNode getWriteNode(final int fieldIndex,
        final ObjectLayout layout, final AbstractWriteFieldNode next) {
      CompilerAsserts.neverPartOfCompilation("StorageLocation");
      return new WriteDoubleFieldNode(fieldIndex, layout, next);
    }
  }

  public static final class LongDirectStoreLocation extends PrimitiveDirectStoreLocation
      implements LongStorageLocation {
    public LongDirectStoreLocation(final ObjectLayout layout, final int primField) {
      super(layout, primField);
    }

    @Override
    public Object read(final SObject obj, final boolean assumptionValid) {
      try {
        return readLong(obj, assumptionValid);
      } catch (UnexpectedResultException e) {
        return e.getResult();
      }
    }

    @Override
    public long readLong(final SObject obj, final boolean assumptionValid) throws UnexpectedResultException {
      if (isSet(obj, assumptionValid)) {
        // TODO: for the moment Graal doesn't seem to get the optimizations
        // right, still need to pass in the correct location identifier
        return ua.getLong(obj, offset, assumptionValid, null);
      } else {
        TruffleCompiler.transferToInterpreterAndInvalidate("unstabelized read node");
        throw new UnexpectedResultException(Nil.nilObject);
      }
    }

    @Override
    public void write(final SObject obj, final Object value) throws GeneralizeStorageLocationException {
      assert value != null;
      if (value instanceof Long) {
        writeLong(obj, (long) value);
      } else {
        TruffleCompiler.transferToInterpreterAndInvalidate("unstabelized write node");
        throw new GeneralizeStorageLocationException();
      }
    }

    @Override
    public void writeLong(final SObject obj, final long value) {
      ua.putLong(obj, offset, value, null);
      markAsSet(obj);
    }

    @Override
    public AbstractReadFieldNode getReadNode(final int fieldIndex,
        final ObjectLayout layout, final AbstractReadFieldNode next) {
      CompilerAsserts.neverPartOfCompilation("StorageLocation");
      return new ReadLongFieldNode(fieldIndex, layout, next);
    }

    @Override
    public AbstractWriteFieldNode getWriteNode(final int fieldIndex,
        final ObjectLayout layout, final AbstractWriteFieldNode next) {
      CompilerAsserts.neverPartOfCompilation("StorageLocation");
      return new WriteLongFieldNode(fieldIndex, layout, next);
    }
  }

  public abstract static class PrimitiveArrayStoreLocation extends PrimitiveStorageLocation {
    protected final int extensionIndex;
    public PrimitiveArrayStoreLocation(final ObjectLayout layout, final int primField) {
      super(layout, primField);
      extensionIndex = primField - SObject.NUM_PRIMITIVE_FIELDS;
      assert extensionIndex >= 0;
    }
  }

  public static final class LongArrayStoreLocation extends PrimitiveArrayStoreLocation
      implements LongStorageLocation {
    public LongArrayStoreLocation(final ObjectLayout layout, final int primField) {
      super(layout, primField);
    }

    @Override
    public Object read(final SObject obj, final boolean assumptionValid) {
      try {
        return readLong(obj, assumptionValid);
      } catch (UnexpectedResultException e) {
        return e.getResult();
      }
    }

    @Override
    public long readLong(final SObject obj, final boolean assumptionValid) throws UnexpectedResultException {
      if (isSet(obj, assumptionValid)) {
        // perhaps we should use the unsafe operations as for doubles
        return obj.getExtendedPrimFields()[extensionIndex];
      } else {
        TruffleCompiler.transferToInterpreterAndInvalidate("unstabelized read node");
        throw new UnexpectedResultException(Nil.nilObject);
      }
    }

    @Override
    public void write(final SObject obj, final Object value) throws GeneralizeStorageLocationException {
      assert value != null;
      if (value instanceof Long) {
        writeLong(obj, (long) value);
      } else {
        assert value != Nil.nilObject;
        TruffleCompiler.transferToInterpreterAndInvalidate("unstabelized write node");
        throw new GeneralizeStorageLocationException();
      }
    }

    @Override
    public void writeLong(final SObject obj, final long value) {
      obj.getExtendedPrimFields()[extensionIndex] = value;
      markAsSet(obj);
    }

    @Override
    public AbstractReadFieldNode getReadNode(final int fieldIndex,
        final ObjectLayout layout, final AbstractReadFieldNode next) {
      CompilerAsserts.neverPartOfCompilation("StorageLocation");
      return new ReadLongFieldNode(fieldIndex, layout, next);
    }

    @Override
    public AbstractWriteFieldNode getWriteNode(final int fieldIndex,
        final ObjectLayout layout, final AbstractWriteFieldNode next) {
      CompilerAsserts.neverPartOfCompilation("StorageLocation");
      return new WriteLongFieldNode(fieldIndex, layout, next);
    }
  }

  public static final class DoubleArrayStoreLocation extends PrimitiveArrayStoreLocation
      implements DoubleStorageLocation {
    public DoubleArrayStoreLocation(final ObjectLayout layout, final int primField) {
      super(layout, primField);
    }

    @Override
    public Object read(final SObject obj, final boolean assumptionValid) {
      try {
        return readDouble(obj, assumptionValid);
      } catch (UnexpectedResultException e) {
        return e.getResult();
      }
    }

    @Override
    public double readDouble(final SObject obj, final boolean assumptionValid) throws UnexpectedResultException {
      if (isSet(obj, assumptionValid)) {
        long[] arr = obj.getExtendedPrimFields();
        // TODO: for the moment Graal doesn't seem to get the optimizations
        // right, still need to pass in the correct location identifier, which can probably be `this`.
        return ua.getDouble(arr,
            Unsafe.ARRAY_DOUBLE_BASE_OFFSET + Unsafe.ARRAY_DOUBLE_INDEX_SCALE * extensionIndex,
            true, null);
      } else {
        TruffleCompiler.transferToInterpreterAndInvalidate("unstabelized read node");
        throw new UnexpectedResultException(Nil.nilObject);
      }
    }

    @Override
    public void write(final SObject obj, final Object value) throws GeneralizeStorageLocationException {
      assert value != null;
      if (value instanceof Double) {
        writeDouble(obj, (double) value);
      } else {
        assert value != Nil.nilObject;
        TruffleCompiler.transferToInterpreterAndInvalidate("unstabelized write node");
        throw new GeneralizeStorageLocationException();
      }
    }

    @Override
    public void writeDouble(final SObject obj, final double value) {
      final long[] arr = obj.getExtendedPrimFields();

      // TODO: for the moment Graal doesn't seem to get the optimizations
      // right, still need to pass in the correct location identifier, which can probably be `this`.
      ua.putDouble(arr,
          Unsafe.ARRAY_DOUBLE_BASE_OFFSET + Unsafe.ARRAY_DOUBLE_INDEX_SCALE * this.extensionIndex,
          value, null);

      markAsSet(obj);
    }

    @Override
    public AbstractReadFieldNode getReadNode(final int fieldIndex,
        final ObjectLayout layout, final AbstractReadFieldNode next) {
      CompilerAsserts.neverPartOfCompilation("StorageLocation");
      return new ReadDoubleFieldNode(fieldIndex, layout, next);
    }

    @Override
    public AbstractWriteFieldNode getWriteNode(final int fieldIndex,
        final ObjectLayout layout, final AbstractWriteFieldNode next) {
      CompilerAsserts.neverPartOfCompilation("StorageLocation");
      return new WriteDoubleFieldNode(fieldIndex, layout, next);
    }
  }
}
*/