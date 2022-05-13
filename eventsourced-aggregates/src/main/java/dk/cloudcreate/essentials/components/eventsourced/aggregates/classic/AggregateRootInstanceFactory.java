package dk.cloudcreate.essentials.components.eventsourced.aggregates.classic;

import dk.cloudcreate.essentials.components.eventsourced.aggregates.Aggregate;
import dk.cloudcreate.essentials.shared.reflection.Reflector;
import org.objenesis.*;
import org.objenesis.instantiator.ObjectInstantiator;

import java.util.concurrent.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Factory that helps the {@link AggregateRootRepository}/{@link ClassicAggregateInMemoryProjector} to create an instance of a given {@link Aggregate}.
 *
 * @see #defaultConstructorFactory()
 * @see DefaultConstructorAggregateRootInstanceFactory
 * @see #objenesisAggregateRootFactory()
 * @see ObjenesisAggregateRootInstanceFactory
 */
public interface AggregateRootInstanceFactory {
    /**
     * An {@link AggregateRootInstanceFactory} that calls the default no-arguments constructor on the concrete {@link Aggregate} type to
     * create a new instance of the {@link Aggregate}
     */
    DefaultConstructorAggregateRootInstanceFactory DEFAULT_CONSTRUCTOR_AGGREGATE_ROOT_FACTORY = new DefaultConstructorAggregateRootInstanceFactory();
    /**
     * An {@link AggregateRootInstanceFactory} that uses {@link Objenesis} to create a new instance of the {@link Aggregate}<br>
     * <b>Please note: Objenesis doesn't initialize fields nor call any constructors</b>, so you {@link Aggregate} design needs to take
     * this into consideration.<br>
     * All concrete aggregates that extends {@link AggregateRoot} have been prepared to be initialized by {@link Objenesis}
     */
    AggregateRootInstanceFactory                   OBJENESIS_AGGREGATE_ROOT_FACTORY           = new ObjenesisAggregateRootInstanceFactory();

    <ID, AGGREGATE> AGGREGATE create(Class<ID> idClass, Class<AGGREGATE> aggregateType);

    /**
     * Returns an {@link AggregateRootInstanceFactory} that calls the default no-arguments constructor on the concrete {@link Aggregate} type to
     * create a new instance of the {@link Aggregate}
     *
     * @return #DEFAULT_CONSTRUCTOR_AGGREGATE_ROOT_FACTORY
     */
    static DefaultConstructorAggregateRootInstanceFactory defaultConstructorFactory() {
        return DEFAULT_CONSTRUCTOR_AGGREGATE_ROOT_FACTORY;
    }

    /**
     * Returns an {@link AggregateRootInstanceFactory} that uses {@link Objenesis} to create a new instance of the {@link Aggregate}<br>
     * <b>Please note: Objenesis doesn't initialize fields nor call any constructors</b>, so you {@link Aggregate} design needs to take
     * this into consideration.<br>
     * All concrete aggregates that extends {@link AggregateRoot} have been prepared to be initialized by {@link Objenesis}
     *
     * @return #OBJENESIS_AGGREGATE_ROOT_FACTORY
     */
    static AggregateRootInstanceFactory objenesisAggregateRootFactory() {
        return OBJENESIS_AGGREGATE_ROOT_FACTORY;
    }


    /**
     * {@link AggregateRootInstanceFactory} that calls the default no-arguments constructor on the concrete {@link Aggregate} type to
     * create a new instance of the {@link Aggregate}
     */
    class DefaultConstructorAggregateRootInstanceFactory implements AggregateRootInstanceFactory {
        @Override
        public <ID, AGGREGATE> AGGREGATE create(Class<ID> idClass, Class<AGGREGATE> aggregateType) {
            requireNonNull(aggregateType, "You must provide an aggregateTypeClass");
            return Reflector.reflectOn(aggregateType).newInstance();
        }
    }

    /**
     * {@link AggregateRootInstanceFactory} that uses {@link Objenesis} to create a new instance of the {@link Aggregate}<br>
     * <b>Please note: Objenesis doesn't initialize fields nor call any constructors</b>, so you {@link Aggregate} design needs to take
     * this into consideration.<br>
     * All concrete aggregates that extends {@link AggregateRoot} have been prepared to be initialized by {@link Objenesis}
     */
    class ObjenesisAggregateRootInstanceFactory implements AggregateRootInstanceFactory {
        private final Objenesis                                      objenesis       = new ObjenesisStd();
        private final ConcurrentMap<Class<?>, ObjectInstantiator<?>> instantiatorMap = new ConcurrentHashMap<>();

        @SuppressWarnings("unchecked")
        @Override
        public <ID, AGGREGATE> AGGREGATE create(Class<ID> idClass, Class<AGGREGATE> aggregateType) {
            requireNonNull(aggregateType, "You must provide an aggregateType");
            return (AGGREGATE) instantiatorMap.computeIfAbsent(aggregateType,
                                                               objenesis::getInstantiatorOf)
                                              .newInstance();
        }
    }
}
