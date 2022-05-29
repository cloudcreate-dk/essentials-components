package dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful;


import dk.cloudcreate.essentials.components.common.transaction.*;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.*;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.classic.Event;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.AggregateTypeConfiguration;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventOrder;
import dk.cloudcreate.essentials.shared.types.GenericType;
import dk.cloudcreate.essentials.types.LongRange;
import org.slf4j.*;

import java.util.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Opinionated {@link Aggregate} Repository that's built to persist and load a specific {@link StatefulAggregate} type in combination
 * with {@link EventStore}, {@link UnitOfWorkFactory} and a {@link StatefulAggregateInstanceFactory}.<br>
 * You can use the {@link #from(EventStore, AggregateTypeConfiguration, StatefulAggregateInstanceFactory, Class)} to create a new {@link StatefulAggregateRepository}
 * instance that supports the most common repository method.<br>
 * Alternatively you can extend from the {@link DefaultStatefulAggregateRepository} and add your own special methods
 *
 * @param <ID>                  the aggregate id type (aka stream-id)
 * @param <EVENT_TYPE>          the type of event
 * @param <AGGREGATE_IMPL_TYPE> the aggregate implementation type
 * @see DefaultStatefulAggregateRepository
 */
public interface StatefulAggregateRepository<ID, EVENT_TYPE, AGGREGATE_IMPL_TYPE extends StatefulAggregate<ID, EVENT_TYPE, AGGREGATE_IMPL_TYPE>> {
    /**
     * Create an {@link StatefulAggregateRepository} instance that supports loading and persisting the given Aggregate type.<br>
     * This factory method will try to resolve the Aggregate Id type from the aggregateImplementationType type parameters<br>
     * If that fails please use {@link #from(EventStore, AggregateTypeConfiguration, StatefulAggregateInstanceFactory, Class, Class)}
     *
     * @param <CONFIG>                     the aggregate type configuration
     * @param <ID>                         the aggregate ID type
     * @param <EVENT_TYPE>                 the type of event
     * @param <AGGREGATE_IMPL_TYPE>        the concrete aggregate type  (MUST be a subtype of {@link StatefulAggregate})
     * @param eventStore                   the {@link EventStore} instance to use
     * @param eventStreamConfiguration     the configuration for the event stream that will contain all the events related to the aggregate type
     * @param aggregateRootInstanceFactory the factory responsible for instantiating your {@link StatefulAggregate}'s when loading them from the {@link EventStore}
     * @param aggregateImplementationType  the concrete aggregate type (MUST be a subtype of {@link StatefulAggregate}). It will try to resolve the Aggregate Id type from
     *                                     the aggregateImplementationType type parameters
     * @return a repository instance that can be used load, add and query aggregates of type <code>aggregateType</code>
     */
    @SuppressWarnings("unchecked")
    static <CONFIG extends AggregateTypeConfiguration, ID, EVENT_TYPE, AGGREGATE_IMPL_TYPE extends StatefulAggregate<ID, EVENT_TYPE, AGGREGATE_IMPL_TYPE>> StatefulAggregateRepository<ID, EVENT_TYPE, AGGREGATE_IMPL_TYPE> from(EventStore<CONFIG> eventStore,
                                                                                                                                                                                                                                 CONFIG eventStreamConfiguration,
                                                                                                                                                                                                                                 StatefulAggregateInstanceFactory aggregateRootInstanceFactory,
                                                                                                                                                                                                                                 Class<AGGREGATE_IMPL_TYPE> aggregateImplementationType) {
        requireNonNull(aggregateImplementationType, "No aggregateImplementationType provided");
        return new DefaultStatefulAggregateRepository<>(eventStore,
                                                        eventStreamConfiguration,
                                                        aggregateRootInstanceFactory,
                                                        (Class<ID>) GenericType.resolveGenericTypeOnSuperClass(aggregateImplementationType, 0),
                                                        aggregateImplementationType);
    }

    /**
     * Create an {@link StatefulAggregateRepository} instance that supports loading and persisting the given Aggregate type.<br>
     *
     * @param <CONFIG>                     the aggregate type configuration
     * @param <ID>                         the aggregate ID type
     * @param <EVENT_TYPE>                 the type of event
     * @param <AGGREGATE_IMPL_TYPE>        the concrete aggregate type  (MUST be a subtype of {@link StatefulAggregate})
     * @param eventStore                   the {@link EventStore} instance to use
     * @param eventStreamConfiguration     the configuration for the event stream that will contain all the events related to the aggregate type
     * @param aggregateRootInstanceFactory the factory responsible for instantiating your {@link StatefulAggregate}'s when loading them from the {@link EventStore}
     * @param aggregateIdType              the concrete aggregate ID type
     * @param aggregateImplementationType  the concrete aggregate type (MUST be a subtype of {@link StatefulAggregate})
     * @return a repository instance that can be used load, add and query aggregates of type <code>aggregateType</code>
     */
    static <CONFIG extends AggregateTypeConfiguration, ID, EVENT_TYPE, AGGREGATE_IMPL_TYPE extends StatefulAggregate<ID, EVENT_TYPE, AGGREGATE_IMPL_TYPE>> StatefulAggregateRepository<ID, EVENT_TYPE, AGGREGATE_IMPL_TYPE> from(EventStore<CONFIG> eventStore,
                                                                                                                                                                                                                                 CONFIG eventStreamConfiguration,
                                                                                                                                                                                                                                 StatefulAggregateInstanceFactory aggregateRootInstanceFactory,
                                                                                                                                                                                                                                 Class<ID> aggregateIdType,
                                                                                                                                                                                                                                 Class<AGGREGATE_IMPL_TYPE> aggregateImplementationType) {
        return new DefaultStatefulAggregateRepository<>(eventStore,
                                                        eventStreamConfiguration,
                                                        aggregateRootInstanceFactory,
                                                        aggregateIdType,
                                                        aggregateImplementationType);
    }

    // -------------------------------------------------------------------------------------------------------------------------------------------------

    /**
     * Try to load an {@link StatefulAggregate} instance with the specified <code>aggregateId</code> from the underlying {@link EventStore}<br>
     * If the aggregate instance exists it will be associated with the {@link UnitOfWorkFactory#getRequiredUnitOfWork()}
     * and any changes to will be tracked and will be persisted to the underlying {@link EventStore} when the {@link UnitOfWork}
     * is committed.
     *
     * @param aggregateId              the id of the aggregate we want to load
     * @param expectedLatestEventOrder the expected {@link Event#eventOrder()} of the last event stored in relation to the given aggregate instance
     * @return an {@link Optional} with the matching {@link StatefulAggregate} instance if it exists, otherwise it will return an {@link Optional#empty()}
     * @throws OptimisticAggregateLoadException in case the {@link Event#eventOrder()} of the last event stored in relation to the given aggregate instance
     *                                          is different from the <code>expectedLatestEventOrder</code>
     */
    default Optional<AGGREGATE_IMPL_TYPE> tryLoad(ID aggregateId, EventOrder expectedLatestEventOrder) {
        return tryLoad(aggregateId, Optional.of(expectedLatestEventOrder));
    }

    /**
     * Try to load an {@link StatefulAggregate} instance with the specified <code>aggregateId</code> from the underlying {@link EventStore}<br>
     * If the aggregate instance exists it will be associated with the {@link UnitOfWorkFactory#getRequiredUnitOfWork()}
     * and any changes to will be tracked and will be persisted to the underlying {@link EventStore} when the {@link UnitOfWork}
     * is committed.
     *
     * @param aggregateId              the id of the aggregate we want to load
     * @param expectedLatestEventOrder option with the expected {@link Event#eventOrder()} of the last event stored in relation to the given aggregate instance (if any)
     * @return an {@link Optional} with the matching {@link StatefulAggregate} instance if it exists, otherwise it will return an {@link Optional#empty()}
     * @throws OptimisticAggregateLoadException in case the {@link Event#eventOrder()} of the last event stored in relation to the given aggregate instance
     *                                          is different from the <code>expectedLatestEventOrder</code>
     */
    Optional<AGGREGATE_IMPL_TYPE> tryLoad(ID aggregateId, Optional<EventOrder> expectedLatestEventOrder);

    /**
     * Try to load an {@link StatefulAggregate} instance with the specified <code>aggregateId</code> from the underlying {@link EventStore}<br>
     * If the aggregate instance exists it will be associated with the {@link UnitOfWorkFactory#getRequiredUnitOfWork()}
     * and any changes to will be tracked and will be persisted to the underlying {@link EventStore} when the {@link UnitOfWork}
     * is committed.
     *
     * @param aggregateId the id of the aggregate we want to load
     * @return an {@link Optional} with the matching {@link StatefulAggregate} instance if it exists, otherwise it will return an {@link Optional#empty()}
     * @throws OptimisticAggregateLoadException in case the {@link Event#eventOrder()} of the last event stored in relation to the given aggregate instance
     *                                          is different from the <code>expectedLatestEventOrder</code>
     */
    default Optional<AGGREGATE_IMPL_TYPE> tryLoad(ID aggregateId) {
        return tryLoad(aggregateId, Optional.empty());
    }

    /**
     * Load an {@link StatefulAggregate} instance with the specified <code>aggregateId</code> from the underlying {@link EventStore}<br>
     * The loaded aggregate instance will be associated with the {@link UnitOfWorkFactory#getRequiredUnitOfWork()}
     * and any changes to will be tracked and will be persisted to the underlying {@link EventStore} when the {@link UnitOfWork}
     * is committed.
     *
     * @param aggregateId the id of the aggregate we want to load
     * @return an {@link Optional} with the matching {@link StatefulAggregate} instance
     * @throws AggregateNotFoundException in case a matching {@link StatefulAggregate} doesn't exist in the {@link EventStore}
     */
    default AGGREGATE_IMPL_TYPE load(ID aggregateId) {
        return tryLoad(aggregateId).orElseThrow(() -> new AggregateNotFoundException(aggregateId, aggregateRootImplementationType(), aggregateType()));
    }

    /**
     * Load an {@link StatefulAggregate} instance with the specified <code>aggregateId</code> from the underlying {@link EventStore}<br>
     * The loaded aggregate instance will be associated with the {@link UnitOfWorkFactory#getRequiredUnitOfWork()}
     * and any changes to will be tracked and will be persisted to the underlying {@link EventStore} when the {@link UnitOfWork}
     * is committed.
     *
     * @param aggregateId              the id of the aggregate we want to load
     * @param expectedLatestEventOrder the expected {@link Event#eventOrder()} of the last event stored in relation to the given aggregate instance
     * @return an {@link Optional} with the matching {@link StatefulAggregate} instance
     * @throws AggregateNotFoundException in case a matching {@link StatefulAggregate} doesn't exist in the {@link EventStore}
     */
    default AGGREGATE_IMPL_TYPE load(ID aggregateId, EventOrder expectedLatestEventOrder) {
        return tryLoad(aggregateId, expectedLatestEventOrder).orElseThrow(() -> new AggregateNotFoundException(aggregateId, aggregateRootImplementationType(), aggregateType()));
    }

    /**
     * Associate a newly created and not yet persisted {@link StatefulAggregate} instance with the {@link UnitOfWorkFactory#getRequiredUnitOfWork()}<br>
     * Any changes to will be tracked and will be persisted to the underlying {@link EventStore} when the {@link UnitOfWork}
     * is committed.
     *
     * @param aggregate the aggregate instance to persist to the underlying {@link EventStore}
     */
    void persist(AGGREGATE_IMPL_TYPE aggregate);

    // TODO: Add loadReadOnly (that doesn't require a UnitOfWork), getAllAggregateId's, loadByIds, loadAll, query, etc.

    /**
     * The type of aggregate ID this repository uses
     */
    Class<ID> aggregateIdType();

    /**
     * The type of {@link StatefulAggregate} implementation this repository handles
     */
    Class<AGGREGATE_IMPL_TYPE> aggregateRootImplementationType();

    /**
     * The type of {@link AggregateType} this repository is using to persist Events
     */
    AggregateType aggregateType();

    // -------------------------------------------------------------------------------------------------------------------------------------------------

    /**
     * Default {@link StatefulAggregateRepository} implementation. You can extend this class directly if you need to expand the supported method or
     * use {@link StatefulAggregateRepository#from(EventStore, AggregateTypeConfiguration, StatefulAggregateInstanceFactory, Class, Class)} to create a default instance
     *
     * @param <ID>             the aggregate ID type
     * @param <AGGREGATE_TYPE> the concrete aggregate type  (MUST be a subtype of {@link StatefulAggregate})
     */
    class DefaultStatefulAggregateRepository<ID, EVENT_TYPE, AGGREGATE_TYPE extends StatefulAggregate<ID, EVENT_TYPE, AGGREGATE_TYPE>> implements StatefulAggregateRepository<ID, EVENT_TYPE, AGGREGATE_TYPE> {
        private static final Logger log = LoggerFactory.getLogger(StatefulAggregateRepository.class);

        private final EventStore<?>                                          eventStore;
        private final AggregateTypeConfiguration                             aggregateTypeConfiguration;
        private final Class<AGGREGATE_TYPE>                                  aggregateImplementationType;
        private final Class<ID>                                              aggregateIdType;
        private final StatefulAggregateRepositoryUnitOfWorkLifecycleCallback unitOfWorkCallback;
        private final StatefulAggregateInstanceFactory                       aggregateRootInstanceFactory;

        /**
         * Create an {@link StatefulAggregateRepository}<br>
         *
         * @param eventStore                       the {@link EventStore} instance to use
         * @param aggregateTypeConfiguration       the configuration for the event stream that will contain all the events related to the aggregate type
         * @param statefulAggregateInstanceFactory the factory responsible for instantiating your {@link StatefulAggregate}'s when loading them from the {@link EventStore}
         * @param aggregateIdType                  the concrete aggregate ID type
         * @param aggregateImplementationType      the concrete aggregate type (MUST be a subtype of {@link StatefulAggregate})
         */
        private <CONFIG extends AggregateTypeConfiguration> DefaultStatefulAggregateRepository(EventStore<CONFIG> eventStore,
                                                                                               CONFIG aggregateTypeConfiguration,
                                                                                               StatefulAggregateInstanceFactory statefulAggregateInstanceFactory,
                                                                                               Class<ID> aggregateIdType,
                                                                                               Class<AGGREGATE_TYPE> aggregateImplementationType) {
            this.eventStore = requireNonNull(eventStore, "You must supply an EventStore instance");
            this.aggregateTypeConfiguration = requireNonNull(aggregateTypeConfiguration, "You must supply an eventStreamConfiguration");
            this.aggregateRootInstanceFactory = requireNonNull(statefulAggregateInstanceFactory, "You must supply a AggregateRootFactory instance");
            this.aggregateImplementationType = requireNonNull(aggregateImplementationType, "You must supply an aggregateImplementationType");
            this.aggregateIdType = requireNonNull(aggregateIdType, "You must supply an aggregateIdType");
            unitOfWorkCallback = new StatefulAggregateRepositoryUnitOfWorkLifecycleCallback();
            eventStore.addAggregateTypeConfiguration(aggregateTypeConfiguration);
            eventStore.addSpecificInMemoryProjector(aggregateImplementationType, new StatefulAggregateInMemoryProjector(statefulAggregateInstanceFactory));
        }

        /**
         * The {@link EventStore} that's being used for support
         */
        protected EventStore eventStore() {
            return eventStore;
        }

        @Override
        public String toString() {
            return "AggregateRootRepository{" +
                    "aggregateType=" + aggregateTypeConfiguration.aggregateType +
                    "aggregateImplementationType=" + aggregateRootImplementationType() +
                    "aggregateIdType=" + aggregateIdType() +
                    '}';
        }

        @Override
        public Optional<AGGREGATE_TYPE> tryLoad(ID aggregateId, Optional<EventOrder> expectedLatestEventOrder) {
            log.trace("Trying to load {} with id '{}' and expectedLatestEventOrder {}", aggregateImplementationType.getName(), aggregateId, expectedLatestEventOrder);
            var unitOfWork = eventStore.getUnitOfWorkFactory().getRequiredUnitOfWork();
            var potentialPersistedEventStream = eventStore.fetchStream(aggregateTypeConfiguration.aggregateType,
                                                                       aggregateId,
                                                                       LongRange.from(EventOrder.FIRST_EVENT_ORDER.longValue())); // TODO: Support for looking up a snapshot version of the aggregate, where we only need to load events not included in the snapshot
            if (potentialPersistedEventStream.isEmpty()) {
                log.trace("Didn't find a {} with id '{}'", aggregateImplementationType.getName(), aggregateId);
                return Optional.empty();
            } else {
                var persistedEventsStream = potentialPersistedEventStream.get();
                if (expectedLatestEventOrder.isPresent()) {
                    PersistedEvent lastEventPersisted = persistedEventsStream.eventList().get(persistedEventsStream.eventList().size() - 1);
                    if (!lastEventPersisted.eventOrder().equals(expectedLatestEventOrder.get())) {
                        log.trace("Found {} with id '{}' but expectedLatestEventOrder {} != actualLatestEventOrder {}",
                                  aggregateImplementationType.getName(),
                                  aggregateId,
                                  expectedLatestEventOrder.get(),
                                  lastEventPersisted.eventOrder());
                        throw new OptimisticAggregateLoadException(aggregateId,
                                                                   aggregateImplementationType,
                                                                   expectedLatestEventOrder.get(),
                                                                   lastEventPersisted.eventOrder());
                    }
                }
                log.debug("Found {} with id '{}' and expectedLatestEventOrder {}", aggregateImplementationType.getName(), aggregateId, expectedLatestEventOrder);
                AGGREGATE_TYPE aggregate = aggregateRootInstanceFactory.create(aggregateId, aggregateImplementationType);
                return Optional.of(unitOfWork.registerLifecycleCallbackForResource(aggregate.rehydrate(persistedEventsStream),
                                                                                   unitOfWorkCallback));
            }
        }

        @Override
        public void persist(AGGREGATE_TYPE aggregate) {
            log.debug("Adding {} with id '{}' to the current UnitOfWork so it will be persisted at commit time", aggregateImplementationType.getName(), aggregate.aggregateId());
            eventStore.getUnitOfWorkFactory()
                      .getRequiredUnitOfWork()
                      .registerLifecycleCallbackForResource(aggregate, unitOfWorkCallback);
        }

        @Override
        public Class<ID> aggregateIdType() {
            return aggregateIdType;
        }

        @Override
        public Class<AGGREGATE_TYPE> aggregateRootImplementationType() {
            return aggregateImplementationType;
        }

        @Override
        public AggregateType aggregateType() {
            return aggregateTypeConfiguration.aggregateType;
        }

        /**
         * The {@link UnitOfWorkLifecycleCallback} that's responsible for persisting {@link Event}'s applied to {@link StatefulAggregate}'s during the {@link UnitOfWork} they were
         * {@link StatefulAggregateRepository#load(Object)}/{@link StatefulAggregateRepository#tryLoad(Object)}/.... and {@link StatefulAggregateRepository#persist(StatefulAggregate)}
         */
        private class StatefulAggregateRepositoryUnitOfWorkLifecycleCallback implements UnitOfWorkLifecycleCallback<AGGREGATE_TYPE> {

            @Override
            public void beforeCommit(UnitOfWork unitOfWork, List<AGGREGATE_TYPE> associatedResources) {
                log.trace("beforeCommit processing {} '{}' registered with the UnitOfWork being committed", associatedResources.size(), aggregateImplementationType.getName());
                associatedResources.forEach(aggregate -> {
                    log.trace("beforeCommit processing '{}' with id '{}'", aggregateImplementationType.getName(), aggregate.aggregateId());
                    var eventsToPersist = aggregate.getUncommittedChanges();
                    if (eventsToPersist.isEmpty()) {
                        log.trace("No changes detected for '{}' with id '{}'", aggregateImplementationType.getName(), aggregate.aggregateId());
                    } else {
                        if (log.isTraceEnabled()) {
                            log.trace("Persisting {} event(s) related to '{}' with id '{}': {}",
                                      eventsToPersist.events.size(),
                                      aggregateImplementationType.getName(),
                                      aggregate.aggregateId(),
                                      eventsToPersist.stream()
                                                     .map(persistableEvent -> persistableEvent.getClass().getName())
                                                     .reduce((s, s2) -> s + ", " + s2));
                        } else {
                            log.debug("Persisting {} event(s) related to '{}' with id '{}'",
                                      eventsToPersist.events.size(),
                                      aggregateImplementationType.getName(),
                                      aggregate.aggregateId());
                        }
                        eventStore.appendToStream(aggregateTypeConfiguration.aggregateType,
                                                  eventsToPersist.aggregateId,
                                                  eventsToPersist.eventOrderOfLastRehydratedEvent,
                                                  eventsToPersist.events);
                        aggregate.markChangesAsCommitted();
                    }
                });
            }

            @Override
            public void afterCommit(UnitOfWork unitOfWork, java.util.List<AGGREGATE_TYPE> associatedResources) {

            }

            @Override
            public void beforeRollback(UnitOfWork unitOfWork, java.util.List<AGGREGATE_TYPE> associatedResources, Exception causeOfTheRollback) {

            }

            @Override
            public void afterRollback(UnitOfWork unitOfWork, java.util.List<AGGREGATE_TYPE> associatedResources, Exception causeOfTheRollback) {

            }
        }
    }
}
