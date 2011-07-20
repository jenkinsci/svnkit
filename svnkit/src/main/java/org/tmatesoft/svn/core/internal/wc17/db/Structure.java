package org.tmatesoft.svn.core.internal.wc17.db;

import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Structure {

    public interface TypeSafety {
        Class<?> getType();
    }
    
    private static final StructuresPool globalPool = new StructuresPool();
    
    private static final Object LONG_MARKER = Long.TYPE;
    private static final Object BOOLEAN_MARKER = Boolean.TYPE;

    public static Structure obtain(Class<?> e) {
        assert e.isEnum();
        return globalPool.obtain(e);
    }
    
    public static void release(Structure e) {
        if (e != null) {
            globalPool.release(e);
        }
    }
    
    private Class<?> enumClass;
    private Object[] nonPrimitiveValues;
    private long[] longValues;
    
    private Structure(Class<?> enumClass) {
        setEnumClass(enumClass);
    }
    
    public long lng(Enum<?> e) {
        if (e instanceof TypeSafety) {
            assert ((TypeSafety) e).getType() == Long.TYPE;
        }
        if (nonPrimitiveValues[e.ordinal()] == LONG_MARKER) {
            return longValues[e.ordinal()];
        }
        assert false;
        return 0;
    }
    
    public boolean is(Enum<?> e) {
        if (e instanceof TypeSafety) {
            assert ((TypeSafety) e).getType() == Boolean.TYPE;
        }
        if (nonPrimitiveValues[e.ordinal()] == BOOLEAN_MARKER) {
            return longValues[e.ordinal()] != 0;
        }
        assert false;
        return false;
    }
    
    @SuppressWarnings("unchecked")
    public <T> T get(Enum<?> e) {
        Class<?> expectedType = null;
        if (e instanceof TypeSafety) {
            expectedType = ((TypeSafety) e).getType(); 
        }
        Object value = nonPrimitiveValues[e.ordinal()];
        if (value == null) {
            return null;
        } 
        assert value != LONG_MARKER && value != BOOLEAN_MARKER;
        if (expectedType != null) {
            assert expectedType.isAssignableFrom(value.getClass());
        }
        return (T) value;
    }
    
    public void set(Enum<?> x, Object v) {
        nonPrimitiveValues[x.ordinal()] = v;
    }

    public void set(Enum<?> x, long v) {        
        longValues[x.ordinal()] = v;
        set(x, LONG_MARKER);
    }
    
    public void set(Enum<?> x, boolean v) {        
        longValues[x.ordinal()] = v ? 1 : 0;
        set(x, BOOLEAN_MARKER);
    }
    
    public void clear() {
        Arrays.fill(nonPrimitiveValues, null);
        Arrays.fill(longValues, 0);
    }
    
    public void release() {
        release(this);
    }
    
    public int hashCode() {
        int code = enumClass.hashCode();
        for (int i = 0; i < nonPrimitiveValues.length; i++) {
            if (nonPrimitiveValues != null) {
                code += 13*nonPrimitiveValues[i].hashCode();
            }
        }
        for (int i = 0; i < longValues.length; i++) {
            code += 17*longValues[i];            
        }
        return code;
    }
    
    public boolean equals(Object e) {
        if (e == null || e.getClass() != Structure.class) {
            return false;
        }
        Structure other = (Structure) e;
        if (other.enumClass == enumClass) {
            return Arrays.equals(other.nonPrimitiveValues, nonPrimitiveValues) &&
                Arrays.equals(longValues, other.longValues);
        }
        return false;
    }

    private void setEnumClass(Class<?> enumClass) {
        this.enumClass = enumClass;
        
        Object[] enumConstants = enumClass.getEnumConstants();
        assert enumConstants != null;
        
        nonPrimitiveValues = adjustArraySize(nonPrimitiveValues, enumConstants.length);
        longValues = adjustArraySize(longValues, enumConstants.length);        
        
        clear();
    }
    
    private static Object[] adjustArraySize(Object[] array, int desiredSize) {
        if (array == null || array.length < desiredSize) {
            return new Object[desiredSize];
        }
        return array;
    }
    
    private static long[] adjustArraySize(long[] array, int desiredSize) {
        if (array == null || array.length < desiredSize) {
            return new long[desiredSize];
        }
        return array;
    }
    
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('<');
        sb.append(enumClass.getSimpleName());
        sb.append(">\n");
        for (Object field : enumClass.getEnumConstants()) {
            Enum<?> e = (Enum<?>) field;
            Object o = nonPrimitiveValues[e.ordinal()];
            if (o != null) {
                sb.append(e.name());
                sb.append(" = ");
                if (o == LONG_MARKER) {
                    sb.append(Long.toString(lng(e)));
                } else if (o == BOOLEAN_MARKER) {
                    sb.append(is(e));
                    
                } else {
                    sb.append(o);
                    
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }
    
    private static class StructuresPool {
        
        private BlockingQueue<Structure> objectsQueues = new LinkedBlockingQueue<Structure>(23);
        
        public Structure obtain(Class<?> enumClass) {
            Structure t = objectsQueues.poll();
            if (t == null) {
                t = new Structure(enumClass);
            } else {
                t.setEnumClass(enumClass);
            }
            return t;
        }
        
        public void release(Structure t) {
            objectsQueues.offer(t);
        }        
    }
}
