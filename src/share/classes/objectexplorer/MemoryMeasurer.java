package objectexplorer;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.sun.btrace.BTraceUtils;


/**
 * A utility that can be used to measure the memory footprint of an arbitrary
 * object graph. In a nutshell, the user gives a root object, and this class
 * recursively and reflectively explores the object's references.
 * <p>
 * <p>This class can only be used if the containing jar has been given to the
 * Java VM as an agent, as follows:
 * {@code -javaagent:path/to/object-explorer.jar}
 *
 * @see #measureBytes(Object)
 * @see #measureBytes(Object, Predicate)
 */
public class MemoryMeasurer {

    /*
     * The bare minimum memory footprint of an enum value, measured empirically.
     * This should be subtracted for any enum value encountered, since it
     * is static in nature.
     */
    private static final long costOfBareEnumConstant =
            BTraceUtils.sizeof(DummyEnum.CONSTANT);

    /**
     * Measures the memory footprint, in bytes, of an object graph. The object
     * graph is defined by a root object and whatever object can be reached
     * through that, excluding static fields, {@code Class} objects, and
     * fields defined in {@code enum}s (all these are considered shared values,
     * which should not contribute to the cost of any single object graph).
     * <p>
     * <p>Equivalent to {@code measureBytes(rootObject,
     * Predicates.alwaysTrue())}.
     *
     * @param rootObject the root object that defines the object graph to be
     *                   measured
     * @return the memory footprint, in bytes, of the object graph
     */
    public static long measureBytes(Object rootObject) {
        return measureBytes(rootObject, Predicates.alwaysTrue());
    }

    /**
     * Measures the memory footprint, in bytes, of an object graph. The object
     * graph is defined by a root object and whatever object can be reached
     * through that, excluding static fields, {@code Class} objects, and
     * fields defined in {@code enum}s (all these are considered shared values,
     * which should not contribute to the cost of any single object graph), and
     * any object for which the user-provided predicate returns {@code false}.
     *
     * @param rootObject     the root object that defines the object graph to be
     *                       measured
     * @param objectAcceptor a predicate that returns {@code true} for objects
     *                       to be explored (and treated as part of the object graph), or
     *                       {@code false} to forbid the traversal to traverse the given object
     * @return the memory footprint, in bytes, of the object graph
     */
    public static long measureBytes(Object rootObject, Predicate<Object> objectAcceptor) {
        Preconditions.checkNotNull(objectAcceptor, "predicate");

        Predicate<Chain> completePredicate = Predicates.and(ImmutableList.of(
                new ObjectExplorer.AtMostOncePredicate(),
                ObjectExplorer.notEnumFieldsOrClasses,
                Predicates.compose(objectAcceptor, ObjectExplorer.chainToObject)
        ));

        return ObjectExplorer.exploreObject(rootObject,
                new MemoryMeasurerVisitor(completePredicate));
    }

    private enum DummyEnum {
        CONSTANT;
    }

    private static class MemoryMeasurerVisitor implements ObjectVisitor<Long> {
        private final Predicate<Chain> predicate;
        private long memory;

        MemoryMeasurerVisitor(Predicate<Chain> predicate) {
            this.predicate = predicate;
        }

        @Override
        public Traversal visit(Chain chain) {
            if (predicate.apply(chain)) {
                Object o = chain.getValue();
                memory += BTraceUtils.sizeof(o);
                if (Enum.class.isAssignableFrom(o.getClass())) {
                    memory -= costOfBareEnumConstant;
                }
                return Traversal.EXPLORE;
            }
            return Traversal.SKIP;
        }

        @Override
        public Long result() {
            return memory;
        }
    }
}
