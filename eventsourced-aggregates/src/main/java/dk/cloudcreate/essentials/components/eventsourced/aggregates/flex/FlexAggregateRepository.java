package dk.cloudcreate.essentials.components.eventsourced.aggregates.flex;

import dk.cloudcreate.essentials.components.common.transaction.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.AggregateTypeConfiguration;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventOrder;
import dk.cloudcreate.essentials.shared.reflection.Reflector;
import dk.cloudcreate.essentials.types.LongRange;
import org.slf4j.*;

import java.util.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Opinionated {@link FlexAggregate} Repository that's built to persist and load a specific {@link FlexAggregate} type in combination
 * with {@link EventStore}, {@link UnitOfWorkFactory} and a {@link FlexAggregateRepository}.<br>
 * <p>
 * Here's how to create an {@link FlexAggregateRepository} instance that can persist an {@link FlexAggregate}
 * of type <code>Order</code> which has an aggregate id of type <code>OrderId</code>:
 * <pre>{@code
 * EasyAggregateRepository<OrderId, Order> repository = EasyAggregateRepository.from(eventStores,
 *                                                                                 unitOfWorkController,
 *                                                                                 OrderId.class,
 *                                                                                 Order.class
 *                                                                                  );
 * }</pre>
 * Here's a typical usage pattern for when you want to persist an new {@link FlexAggregate} instance
 * (i.e. the {@link EventStore} doesn't contain an events related to the given Aggregate id):
 * <pre>{@code
 * unitOfWorkController.usingUnitOfWork(unitOfWorkController -> {
 *      var eventsToPersist = Order.createNewOrder(orderId, CustomerId.random(), 123);
 *      repository.persist(eventsToPersist);
 *  });
 * }</pre>
 * Here's the typical usage pattern for {@link FlexAggregateRepository} for already existing {@link FlexAggregate}
 * instance (i.e. an instance that has events in the {@link EventStore}):
 * <pre>{@code
 * unitOfWorkController.usingUnitOfWork(unitOfWorkController -> {
 *      var order = repository.load(orderId);
 *      var eventsToPersist = order.accept();
 *      repository.persist(eventsToPersist);
 *  });
 * }
 * </pre>
 *
 * @param <ID>             the aggregate id type
 * @param <AGGREGATE_TYPE> the aggregate type
 * @see FlexAggregate
 */
public interface FlexAggregateRepository<ID, AGGREGATE_TYPE extends FlexAggregate<ID, AGGREGATE_TYPE>> {
    /**
     * @param <ID>                     the aggregate ID type
     * @param <AGGREGATE_TYPE>         the concrete aggregate type  (MUST be a subtype of {@link FlexAggregate})
     * @param eventStore               The {@link EventStore} instance to use
     * @param eventStreamConfiguration the configuration for the event stream that will contain all the events related to the aggregate type
     * @param unitOfWorkFactory        The factory that provides {@link UnitOfWork}'s
     * @param aggregateIdType          the concrete aggregate ID type
     * @param aggregateType            the concrete aggregate type (MUST be a subtype of {@link FlexAggregate})
     * @return a repository instance that can be used load, add and query aggregates of type <code>aggregateType</code>
     */
    static <CONFIG extends AggregateTypeConfiguration, ID, AGGREGATE_TYPE extends FlexAggregate<ID, AGGREGATE_TYPE>> FlexAggregateRepository<ID, AGGREGATE_TYPE> from(EventStore<CONFIG> eventStore,
                                                                                                                                                                      CONFIG eventStreamConfiguration,
                                                                                                                                                                      UnitOfWorkFactory unitOfWorkFactory,
                                                                                                                                                                      Class<ID> aggregateIdType,
                                                                                                                                                                      Class<AGGREGATE_TYPE> aggregateType) {
        return new DefaultFlexAggregateRepository<>(eventStore, eventStreamConfiguration, unitOfWorkFactory, aggregateType, aggregateIdType);
    }

    // -------------------------------------------------------------------------------------------------------------------------------------------------

    /**
     * Try to load an {@link FlexAggregate} instance with the specified <code>aggregateId</code> from the underlying {@link EventStore}<br>
     * If the aggregate instance exists it will be associated with the {@link UnitOfWorkFactory#getRequiredUnitOfWork()}
     * and any changes to will be tracked and will be persisted to the underlying {@link EventStore} when the {@link UnitOfWork}
     * is committed.
     *
     * @param aggregateId              the id of the aggregate we want to load
     * @param expectedLatestEventOrder the expected {@link PersistedEvent#eventOrder()} of the last event stored in relation to the given aggregate instance
     * @return an {@link Optional} with the matching {@link FlexAggregate} instance if it exists, otherwise it will return an {@link Optional#empty()}
     * @throws OptimisticFlexAggregateLoadException in case the {@link PersistedEvent#eventOrder()} of the last event stored in relation to the given aggregate instance
     *                                              is different from the <code>expectedLatestEventOrder</code>
     */
    default Optional<AGGREGATE_TYPE> tryLoad(ID aggregateId, long expectedLatestEventOrder) {
        return tryLoad(aggregateId, Optional.of(expectedLatestEventOrder));
    }

    /**
     * Try to load an {@link FlexAggregate} instance with the specified <code>aggregateId</code> from the underlying {@link EventStore}<br>
     * If the aggregate instance exists it will be associated with the {@link UnitOfWorkFactory#getRequiredUnitOfWork()}
     * and any changes to will be tracked and will be persisted to the underlying {@link EventStore} when the {@link UnitOfWork}
     * is committed.
     *
     * @param aggregateId              the id of the aggregate we want to load
     * @param expectedLatestEventOrder Optional with the expected {@link PersistedEvent#eventOrder()} of the last event stored in relation to the given aggregate instance (if any)
     * @return an {@link Optional} with the matching {@link FlexAggregate} instance if it exists, otherwise it will return an {@link Optional#empty()}
     * @throws OptimisticFlexAggregateLoadException in case the {@link PersistedEvent#eventOrder()} of the last event stored in relation to the given aggregate instance
     *                                              is different from the <code>expectedLatestEventOrder</code>
     */
    Optional<AGGREGATE_TYPE> tryLoad(ID aggregateId, Optional<Long> expectedLatestEventOrder);

    /**
     * Try to load an {@link FlexAggregate} instance with the specified <code>aggregateId</code> from the underlying {@link EventStore}<br>
     * If the aggregate instance exists it will be associated with the {@link UnitOfWorkFactory#getRequiredUnitOfWork()}
     * and any changes to will be tracked and will be persisted to the underlying {@link EventStore} when the {@link UnitOfWork}
     * is committed.
     *
     * @param aggregateId the id of the aggregate we want to load
     * @return an {@link Optional} with the matching {@link FlexAggregate} instance if it exists, otherwise it will return an {@link Optional#empty()}
     * @throws OptimisticFlexAggregateLoadException in case the {@link PersistedEvent#eventOrder()} of the last event stored in relation to the given aggregate instance
     *                                              is different from the <code>expectedLatestEventOrder</code>
     */
    default Optional<AGGREGATE_TYPE> tryLoad(ID aggregateId) {
        return tryLoad(aggregateId, Optional.empty());
    }

    /**
     * Load an {@link FlexAggregate} instance with the specified <code>aggregateId</code> from the underlying {@link EventStore}<br>
     * The loaded aggregate instance will be associated with the {@link UnitOfWorkFactory#getRequiredUnitOfWork()}
     * and any changes to will be tracked and will be persisted to the underlying {@link EventStore} when the {@link UnitOfWork}
     * is committed.
     *
     * @param aggregateId the id of the aggregate we want to load
     * @return an {@link Optional} with the matching {@link FlexAggregate} instance
     * @throws AggregateNotFoundException in case a matching {@link FlexAggregate} doesn't exist in the {@link EventStore}
     */
    default AGGREGATE_TYPE load(ID aggregateId) {
        return tryLoad(aggregateId).orElseThrow(() -> new AggregateNotFoundException(aggregateId, aggregateRootImplementationType(), aggregateType()));
    }

    /**
     * The type of {@link FlexAggregate} implementation this repository handles
     */
    Class<AGGREGATE_TYPE> aggregateRootImplementationType();

    /**
     * The type of {@link AggregateType} this repository is using to persist Events
     */
    AggregateType aggregateType();

    /**
     * Load an {@link FlexAggregate} instance with the specified <code>aggregateId</code> from the underlying {@link EventStore}<br>
     * The loaded aggregate instance will be associated with the {@link UnitOfWorkFactory#getRequiredUnitOfWork()}
     * and any changes to will be tracked and will be persisted to the underlying {@link EventStore} when the {@link UnitOfWork}
     * is committed.
     *
     * @param aggregateId              the id of the aggregate we want to load
     * @param expectedLatestEventOrder the expected {@link PersistedEvent#eventOrder()} of the last event stored in relation to the given aggregate instance
     * @return an {@link Optional} with the matching {@link FlexAggregate} instance
     * @throws AggregateNotFoundException in case a matching {@link FlexAggregate} doesn't exist in the {@link EventStore}
     */
    default AGGREGATE_TYPE load(ID aggregateId, long expectedLatestEventOrder) {
        return tryLoad(aggregateId, expectedLatestEventOrder).orElseThrow(() -> new AggregateNotFoundException(aggregateId, aggregateRootImplementationType(), aggregateType()));
    }

    /**
     * Associate a newly created and not yet persisted {@link FlexAggregate} instance with the {@link UnitOfWorkFactory#getRequiredUnitOfWork()}<br>
     * Any changes to will be tracked and will be persisted to the underlying {@link EventStore} when the {@link UnitOfWork}
     * is committed.
     *
     * @param eventsToPersist the events to persist to the underlying {@link EventStore} (a result of a Command method
     *                        invocation on an {@link FlexAggregate} instance
     */
    void persist(EventsToPersist<ID> eventsToPersist);

    // TODO: Add loadReadOnly (that doesn't require a UnitOfWork), getAllAggregateId's, loadByIds, loadAll, query, etc.

    /**
     * The type of aggregate ID this repository uses
     */
    Class<ID> aggregateIdType();

    // -------------------------------------------------------------------------------------------------------------------------------------------------

    class DefaultFlexAggregateRepository<ID, AGGREGATE_TYPE extends FlexAggregate<ID, AGGREGATE_TYPE>> implements FlexAggregateRepository<ID, AGGREGATE_TYPE> {
        private static final Logger log = LoggerFactory.getLogger(FlexAggregateRepository.class);

        private final EventStore<?>                                      eventStore;
        private final Class<AGGREGATE_TYPE>                              aggregateRootImplementationType;
        private final Class<ID>                  aggregateIdType;
        private final AggregateTypeConfiguration eventStreamConfiguration;
        private final UnitOfWorkFactory          unitOfWorkFactory;
        private final EasyAggregateRepositoryUnitOfWorkLifecycleCallback unitOfWorkCallback;

        /**
         * Create an {@link FlexAggregateRepository}
         *
         * @param eventStore                      the event store that can load and persist events related to the aggregateType
         * @param eventStreamConfiguration        the configuration for the event stream that will contain all the events related to the aggregate type
         * @param unitOfWorkFactory               The factory that provides {@link UnitOfWork}'s
         * @param aggregateRootImplementationType the concrete aggregate type (MUST be a subtype of {@link FlexAggregate})
         * @param aggregateIdType                 the concrete aggregate ID type
         */
        private <CONFIG extends AggregateTypeConfiguration> DefaultFlexAggregateRepository(EventStore<CONFIG> eventStore,
                                                                                           CONFIG eventStreamConfiguration,
                                                                                           UnitOfWorkFactory unitOfWorkFactory,
                                                                                           Class<AGGREGATE_TYPE> aggregateRootImplementationType,
                                                                                           Class<ID> aggregateIdType) {
            this.eventStore = requireNonNull(eventStore, "You must supply an EventStore instance");
            this.eventStreamConfiguration = requireNonNull(eventStreamConfiguration, "You must supply an eventStreamConfiguration");
            this.unitOfWorkFactory = requireNonNull(unitOfWorkFactory, "You must supply a UnitOfWorkFactory instance");
            this.aggregateRootImplementationType = requireNonNull(aggregateRootImplementationType, "You must supply an aggregateImplementationType");
            this.aggregateIdType = requireNonNull(aggregateIdType, "You must supply an aggregateIdType");
            this.unitOfWorkCallback = new EasyAggregateRepositoryUnitOfWorkLifecycleCallback();
            eventStore.addAggregateTypeConfiguration(eventStreamConfiguration);
        }

        /**
         * The {@link EventStore} that's being used for support
         */
        protected EventStore<?> eventStore() {
            return eventStore;
        }

        @Override
        public AggregateType aggregateType() {
            return eventStreamConfiguration.aggregateType;
        }

        @Override
        public String toString() {
            return "EasyAggregateRepository{" +
                    "aggregateType=" + aggregateType() +
                    ", aggregateIdType=" + aggregateIdType() +
                    ", aggregateImplementationType=" + aggregateRootImplementationType().getName() +
                    '}';
        }

        @Override
        public Optional<AGGREGATE_TYPE> tryLoad(ID aggregateId, Optional<Long> expectedLatestEventOrder) {
            log.trace("Trying to load {} with id '{}' and expectedLatestEventOrder {}", aggregateRootImplementationType.getName(), aggregateId, expectedLatestEventOrder);
            UnitOfWork unitOfWork = unitOfWorkFactory.getRequiredUnitOfWork();
            var potentialPersistedEventStream = eventStore.fetchStream(eventStreamConfiguration.aggregateType,
                                                                       aggregateId,
                                                                       LongRange.from(EventOrder.FIRST_EVENT_ORDER.longValue())); // TODO: Support for looking up a snapshot version of the aggregate, where we only need to load events not included in the snapshot
            if (potentialPersistedEventStream.isEmpty()) {
                log.trace("Didn't find a {} with id '{}'", aggregateRootImplementationType.getName(), aggregateId);
                return Optional.empty();
            } else {
                var persistedEventsStream = potentialPersistedEventStream.get();
                if (expectedLatestEventOrder.isPresent()) {
                    PersistedEvent lastEventPersisted = persistedEventsStream.eventList().get(persistedEventsStream.eventList().size() - 1);
                    if (!lastEventPersisted.eventOrder().equals(expectedLatestEventOrder.get())) {
                        log.trace("Found {} with id '{}' but expectedLatestEventOrder {} != actualLatestEventOrder {}",
                                  aggregateRootImplementationType.getName(),
                                  aggregateId,
                                  expectedLatestEventOrder.get(),
                                  lastEventPersisted.eventOrder());
                        throw new OptimisticFlexAggregateLoadException(aggregateId,
                                                                       aggregateRootImplementationType,
                                                                       expectedLatestEventOrder.map(value -> EventOrder.of(value)).get(),
                                                                       lastEventPersisted.eventOrder());
                    }

                }
                log.debug("Found {} with id '{}' and expectedLatestEventOrder {}", aggregateRootImplementationType.getName(), aggregateId, expectedLatestEventOrder);
                AGGREGATE_TYPE aggregate = Reflector.reflectOn(aggregateRootImplementationType).newInstance();
                return Optional.of(aggregate.rehydrate(persistedEventsStream));
            }
        }

        @Override
        public Class<AGGREGATE_TYPE> aggregateRootImplementationType() {
            return aggregateRootImplementationType;
        }

        @Override
        public void persist(EventsToPersist<ID> eventsToPersist) {
            log.debug("Adding {} with id '{}' to the current UnitOfWork so it will be persisted at commit time",
                      aggregateRootImplementationType.getName(),
                      eventsToPersist.aggregateId);
            unitOfWorkFactory.getRequiredUnitOfWork()
                             .registerLifecycleCallbackForResource(eventsToPersist, unitOfWorkCallback);
        }

        @Override
        public Class<ID> aggregateIdType() {
            return aggregateIdType;
        }

        /**
         * The {@link UnitOfWorkLifecycleCallback} that's responsible for persisting {@link EventsToPersist} that were a side effect of command methods invoked on
         * {@link FlexAggregate} instances during the {@link UnitOfWork} - see {@link FlexAggregateRepository#persist(EventsToPersist)}
         */
        private class EasyAggregateRepositoryUnitOfWorkLifecycleCallback implements UnitOfWorkLifecycleCallback<EventsToPersist<ID>> {

            @Override
            public void beforeCommit(UnitOfWork unitOfWork, List<EventsToPersist<ID>> associatedResources) {
                log.trace("beforeCommit processing {} '{}' registered with the UnitOfWork being committed", associatedResources.size(), aggregateRootImplementationType.getName());
                associatedResources.forEach(eventsToPersist -> {
                    log.trace("beforeCommit processing '{}' with id '{}'", aggregateRootImplementationType.getName(), eventsToPersist.aggregateId);
                    if (eventsToPersist.eventsToPersist.isEmpty()) {
                        log.trace("No changes detected for '{}' with id '{}'", aggregateRootImplementationType.getName(), eventsToPersist.aggregateId);
                    } else {
                        if (log.isTraceEnabled()) {
                            log.trace("Persisting {} event(s) related to '{}' with id '{}': {}", eventsToPersist.eventsToPersist.size(), aggregateRootImplementationType.getName(), eventsToPersist.aggregateId, eventsToPersist.eventsToPersist.stream().map(persistableEvent -> persistableEvent.getClass().getName()).reduce((s, s2) -> s + ", " + s2));
                        } else {
                            log.debug("Persisting {} event(s) related to '{}' with id '{}'", eventsToPersist.eventsToPersist.size(), aggregateRootImplementationType.getName(), eventsToPersist.aggregateId);
                        }
                        eventStore.appendToStream(eventStreamConfiguration.aggregateType,
                                                  eventsToPersist.aggregateId,
                                                  eventsToPersist.eventsToPersist);
                        eventsToPersist.markEventsAsCommitted();
                    }
                });
            }

            @Override
            public void afterCommit(UnitOfWork unitOfWork, java.util.List<EventsToPersist<ID>> associatedResources) {

            }

            @Override
            public void beforeRollback(UnitOfWork unitOfWork, java.util.List<EventsToPersist<ID>> associatedResources, Exception causeOfTheRollback) {

            }

            @Override
            public void afterRollback(UnitOfWork unitOfWork, java.util.List<EventsToPersist<ID>> associatedResources, Exception causeOfTheRollback) {

            }
        }
    }
}
