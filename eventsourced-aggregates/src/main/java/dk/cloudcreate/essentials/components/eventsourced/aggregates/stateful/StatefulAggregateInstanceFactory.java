package dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful;

import dk.cloudcreate.essentials.components.eventsourced.aggregates.Aggregate;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.classic.AggregateRoot;
import dk.cloudcreate.essentials.shared.reflection.Reflector;
import org.objenesis.*;
import org.objenesis.instantiator.ObjectInstantiator;

import java.util.concurrent.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * Factory that helps the {@link StatefulAggregateRepository}/{@link StatefulAggregateInMemoryProjector} to create an instance of a given {@link Aggregate}.
 *
 * @see #reflectionBasedAggregateRootFactory()
 * @see ReflectionBasedAggregateInstanceFactory
 * @see #objenesisAggregateRootFactory()
 * @see ObjenesisAggregateInstanceFactory
 */
public interface StatefulAggregateInstanceFactory {
    /**
     * An {@link StatefulAggregateInstanceFactory} that calls the default no-arguments constructor on the concrete {@link Aggregate} type to
     * create a new instance of the {@link Aggregate}
     */
    StatefulAggregateInstanceFactory DEFAULT_REFLECTION_BASED_CONSTRUCTOR_AGGREGATE_ROOT_FACTORY = new ReflectionBasedAggregateInstanceFactory();
    /**
     * An {@link StatefulAggregateInstanceFactory} that uses {@link Objenesis} to create a new instance of the {@link Aggregate}<br>
     * <b>Please note: Objenesis doesn't initialize fields nor call any constructors</b>, so you {@link Aggregate} design needs to take
     * this into consideration.<br>
     * All concrete aggregates that extends {@link StatefulAggregate} have been prepared to be initialized by {@link Objenesis}
     */
    StatefulAggregateInstanceFactory OBJENESIS_AGGREGATE_ROOT_FACTORY                            = new ObjenesisAggregateInstanceFactory();

    /**
     * Create an instance of the {@link Aggregate}
     *
     * @param id            the id value
     * @param aggregateType the {@link Aggregate} implementation type
     * @param <ID>          the type of aggregate id
     * @param <AGGREGATE>   the {@link Aggregate} implementation type
     * @return new aggregate instance
     */
    <ID, AGGREGATE> AGGREGATE create(ID id, Class<AGGREGATE> aggregateType);

    /**
     * Returns an {@link StatefulAggregateInstanceFactory} that calls the default no-arguments constructor on the concrete {@link Aggregate} type to
     * create a new instance of the {@link Aggregate}
     *
     * @return #DEFAULT_CONSTRUCTOR_AGGREGATE_ROOT_FACTORY
     */
    static StatefulAggregateInstanceFactory reflectionBasedAggregateRootFactory() {
        return DEFAULT_REFLECTION_BASED_CONSTRUCTOR_AGGREGATE_ROOT_FACTORY;
    }

    /**
     * Returns an {@link StatefulAggregateInstanceFactory} that uses {@link Objenesis} to create a new instance of the {@link Aggregate}<br>
     * <b>Please note: Objenesis doesn't initialize fields nor call any constructors</b>, so you {@link Aggregate} design needs to take
     * this into consideration.<br>
     * All concrete aggregates that extends {@link AggregateRoot} have been prepared to be initialized by {@link Objenesis}
     *
     * @return #OBJENESIS_AGGREGATE_ROOT_FACTORY
     */
    static StatefulAggregateInstanceFactory objenesisAggregateRootFactory() {
        return OBJENESIS_AGGREGATE_ROOT_FACTORY;
    }


    /**
     * {@link StatefulAggregateInstanceFactory} that calls the default no-arguments constructor on the concrete {@link Aggregate} type to
     * create a new instance of the {@link Aggregate}
     */
    class ReflectionBasedAggregateInstanceFactory implements StatefulAggregateInstanceFactory {
        @Override
        public <ID, AGGREGATE> AGGREGATE create(ID id, Class<AGGREGATE> aggregateType) {
            requireNonNull(id, "You must provide an id value");
            requireNonNull(aggregateType, "You must provide an aggregateTypeClass");
            var reflector = Reflector.reflectOn(aggregateType);
            if (reflector.hasDefaultConstructor()) {
                return reflector.newInstance();
            } else if (reflector.hasMatchingConstructorBasedOnArguments(id)) {
                return reflector.newInstance(id);
            } else {
                throw new IllegalArgumentException(msg("Couldn't find default constructor nor a constructor that accepts an aggregateId of type {}", id.getClass().getName()));
            }
        }
    }

    /**
     * {@link StatefulAggregateInstanceFactory} that uses {@link Objenesis} to create a new instance of the {@link Aggregate}<br>
     * <b>Please note: Objenesis doesn't initialize fields nor call any constructors</b>, so you {@link Aggregate} design needs to take
     * this into consideration.<br>
     * All concrete aggregates that extends {@link AggregateRoot} have been prepared to be initialized by {@link Objenesis}
     */
    class ObjenesisAggregateInstanceFactory implements StatefulAggregateInstanceFactory {
        private final Objenesis                                      objenesis       = new ObjenesisStd();
        private final ConcurrentMap<Class<?>, ObjectInstantiator<?>> instantiatorMap = new ConcurrentHashMap<>();

        @SuppressWarnings("unchecked")
        @Override
        public <ID, AGGREGATE> AGGREGATE create(ID id, Class<AGGREGATE> aggregateType) {
            requireNonNull(aggregateType, "You must provide an aggregateType");
            return (AGGREGATE) instantiatorMap.computeIfAbsent(aggregateType,
                                                               objenesis::getInstantiatorOf)
                                              .newInstance();
        }
    }
}
