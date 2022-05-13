package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql;

import dk.cloudcreate.essentials.components.common.types.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.bus.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.interceptor.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.*;
import dk.cloudcreate.essentials.reactive.LocalEventBus;
import dk.cloudcreate.essentials.types.LongRange;
import org.jdbi.v3.core.ConnectionException;
import org.slf4j.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class PostgresqlEventStore<CONFIG extends AggregateTypeConfiguration> implements EventStore<CONFIG> {
    private static final Logger log = LoggerFactory.getLogger(PostgresqlEventStore.class);

    private final UnitOfWorkFactory                               unitOfWorkFactory;
    private final AggregateEventStreamPersistenceStrategy<CONFIG> aggregateTypeConfigurations;


    /**
     * Cache of specific a {@link InMemoryProjector} instance that support rehydrating/projecting a specific projection/aggregate type<br>
     * Key: Projection/Aggregate type<br>
     * Value: The specific {@link InMemoryProjector} that supports the given projection type (if provided to {@link #addSpecificInMemoryProjector(Class, InMemoryProjector)})
     * or the first {@link InMemoryProjector#supports(Class)} that reports true for the given projection type
     */
    private final ConcurrentMap<Class<?>, InMemoryProjector> inMemoryProjectorPerProjectionType;
    private final HashSet<InMemoryProjector>                 inMemoryProjectors;
    private final List<EventStoreInterceptor>                eventStoreInterceptors;
    private final EventStoreLocalEventBus                    eventStoreLocalEventBus;

    public <STRATEGY extends AggregateEventStreamPersistenceStrategy<CONFIG>> PostgresqlEventStore(UnitOfWorkFactory unitOfWorkFactory,
                                                                                                   STRATEGY aggregateTypeConfiguration) {
        this.unitOfWorkFactory = requireNonNull(unitOfWorkFactory, "No unitOfWorkFactory provided");
        this.aggregateTypeConfigurations = requireNonNull(aggregateTypeConfiguration, "No eventStreamPersistenceStrategy provided");
        eventStoreInterceptors = new ArrayList<>();
        inMemoryProjectors = new HashSet<>();
        inMemoryProjectorPerProjectionType = new ConcurrentHashMap<>();
        eventStoreLocalEventBus = new EventStoreLocalEventBus(unitOfWorkFactory);
    }

    @Override
    public LocalEventBus<PersistedEvents> localEventBus() {
        return eventStoreLocalEventBus.localEventBus();
    }

    @Override
    public EventStore<CONFIG> addGenericInMemoryProjector(InMemoryProjector inMemoryProjector) {
        inMemoryProjectors.add(requireNonNull(inMemoryProjector, "No inMemoryProjection"));
        return this;
    }


    @Override
    public EventStore<CONFIG> addSpecificInMemoryProjector(Class<?> projectionType,
                                                           InMemoryProjector inMemoryProjector) {
        inMemoryProjectorPerProjectionType.put(requireNonNull(projectionType, "No projectionType provided"),
                                               requireNonNull(inMemoryProjector, "No inMemoryProjection"));
        return this;
    }

    @Override
    public EventStore<CONFIG> addEventStoreInterceptor(EventStoreInterceptor eventStoreInterceptor) {
        this.eventStoreInterceptors.add(requireNonNull(eventStoreInterceptor, "No eventStoreInterceptor provided"));
        return this;
    }

    @Override
    public <ID> AggregateEventStream<ID> appendToStream(AggregateType aggregateType,
                                                        ID aggregateId,
                                                        Optional<Long> appendEventsAfterEventOrder,
                                                        List<?> events) {
        var unitOfWork = unitOfWorkFactory.getRequiredUnitOfWork();

        var operation = new EventStoreInterceptor.AppendToStream<>(aggregateType,
                                                                   aggregateId,
                                                                   appendEventsAfterEventOrder,
                                                                   events);
        var aggregateEventStream = EventStoreInterceptorChain.newChainForOperation(operation,
                                                                                   eventStoreInterceptors,
                                                                                   (eventStoreInterceptor, eventStoreInterceptorChain) -> eventStoreInterceptor.intercept(operation, eventStoreInterceptorChain),
                                                                                   () -> aggregateTypeConfigurations.persist(unitOfWork,
                                                                                                                             aggregateType,
                                                                                                                             aggregateId,
                                                                                                                             appendEventsAfterEventOrder,
                                                                                                                             events))
                                                             .proceed();
        if (unitOfWork instanceof EventStoreUnitOfWork) {
            ((EventStoreUnitOfWork) unitOfWork).registerEventsPersisted(aggregateEventStream.eventList());
        }
        return aggregateEventStream;
    }


    @Override
    public <ID> Optional<PersistedEvent> loadLastPersistedEventRelatedTo(AggregateType aggregateType,
                                                                         ID aggregateId) {
        var operation = new EventStoreInterceptor.LoadLastPersistedEventRelatedTo<>(aggregateType,
                                                                                    aggregateId);
        return EventStoreInterceptorChain.newChainForOperation(operation,
                                                               eventStoreInterceptors,
                                                               (eventStoreInterceptor, eventStoreInterceptorChain) -> eventStoreInterceptor.intercept(operation, eventStoreInterceptorChain),
                                                               () -> aggregateTypeConfigurations.loadLastPersistedEventRelatedTo(unitOfWorkFactory.getRequiredUnitOfWork(),
                                                                                                                                 aggregateType,
                                                                                                                                 aggregateId))
                                         .proceed();

    }

    @Override
    public Optional<PersistedEvent> loadEvent(AggregateType aggregateType,
                                              EventId eventId) {
        var operation = new EventStoreInterceptor.LoadEvent(aggregateType,
                                                            eventId);
        return EventStoreInterceptorChain.newChainForOperation(operation,
                                                               eventStoreInterceptors,
                                                               (eventStoreInterceptor, eventStoreInterceptorChain) -> eventStoreInterceptor.intercept(operation, eventStoreInterceptorChain),
                                                               () -> aggregateTypeConfigurations.loadEvent(unitOfWorkFactory.getRequiredUnitOfWork(),
                                                                                                           aggregateType,
                                                                                                           eventId))
                                         .proceed();
    }

    @Override
    public <ID> Optional<AggregateEventStream<ID>> fetchStream(AggregateType aggregateType,
                                                               ID aggregateId,
                                                               LongRange eventOrderRange,
                                                               Optional<Tenant> tenant) {
        var operation = new EventStoreInterceptor.FetchStream<>(aggregateType,
                                                                aggregateId,
                                                                eventOrderRange,
                                                                tenant);
        return EventStoreInterceptorChain.newChainForOperation(operation,
                                                               eventStoreInterceptors,
                                                               (eventStoreInterceptor, eventStoreInterceptorChain) -> eventStoreInterceptor.intercept(operation, eventStoreInterceptorChain),
                                                               () -> aggregateTypeConfigurations.loadAggregateEvents(unitOfWorkFactory.getRequiredUnitOfWork(),
                                                                                                                     aggregateType,
                                                                                                                     aggregateId,
                                                                                                                     eventOrderRange,
                                                                                                                     tenant))
                                         .proceed();
    }

    @Override
    public <ID, AGGREGATE> Optional<AGGREGATE> inMemoryProjection(AggregateType aggregateType,
                                                                  ID aggregateId,
                                                                  Class<AGGREGATE> projectionType) {
        requireNonNull(projectionType, "No projectionType provided");
        var inMemoryProjector = inMemoryProjectorPerProjectionType.computeIfAbsent(projectionType, _aggregateType -> inMemoryProjectors.stream().filter(_inMemoryProjection -> _inMemoryProjection.supports(projectionType))
                                                                                                                                       .findFirst()
                                                                                                                                       .orElseThrow(() -> new EventStoreException(msg("Couldn't find an {} that supports projection-type '{}'",
                                                                                                                                                                                      InMemoryProjector.class.getSimpleName(),
                                                                                                                                                                                      projectionType.getName()))));
        return inMemoryProjection(aggregateType,
                                  aggregateId,
                                  projectionType,
                                  inMemoryProjector);
    }

    @Override
    public <ID, AGGREGATE> Optional<AGGREGATE> inMemoryProjection(AggregateType aggregateType,
                                                                  ID aggregateId,
                                                                  Class<AGGREGATE> projectionType,
                                                                  InMemoryProjector inMemoryProjector) {
        requireNonNull(aggregateType, "No aggregateEventStreamName provided");
        requireNonNull(aggregateId, "No aggregateId provided");
        requireNonNull(projectionType, "No projectionType provided");
        requireNonNull(inMemoryProjector, "No inMemoryProjector provided");

        if (!inMemoryProjector.supports(projectionType)) {
            throw new IllegalArgumentException(msg("The provided {} '{}' does not support projection type '{}'",
                                                   InMemoryProjector.class.getName(),
                                                   inMemoryProjector.getClass().getName(),
                                                   projectionType.getName()));
        }
        return inMemoryProjector.projectEvents(aggregateType,
                                               aggregateId,
                                               projectionType,
                                               this);
    }

    @Override
    public Stream<PersistedEvent> loadEventsByGlobalOrder(AggregateType aggregateType,
                                                          LongRange globalEventOrderRange,
                                                          Optional<Tenant> onlyIncludeEventIfItBelongsToTenant) {
        requireNonNull(aggregateType, "No aggregateEventStreamName provided");
        requireNonNull(globalEventOrderRange, "You must specify a globalOrderRange");
        requireNonNull(onlyIncludeEventIfItBelongsToTenant, "You must specify an onlyIncludeEventIfItBelongsToTenant option");

        var operation = new EventStoreInterceptor.LoadEventsByGlobalOrder(aggregateType,
                                                                          globalEventOrderRange,
                                                                          onlyIncludeEventIfItBelongsToTenant);
        return EventStoreInterceptorChain.newChainForOperation(operation,
                                                               eventStoreInterceptors,
                                                               (eventStoreInterceptor, eventStoreInterceptorChain) -> eventStoreInterceptor.intercept(operation, eventStoreInterceptorChain),
                                                               () -> aggregateTypeConfigurations.loadEventsByGlobalOrder(unitOfWorkFactory.getRequiredUnitOfWork(),
                                                                                                                         aggregateType,
                                                                                                                         globalEventOrderRange,
                                                                                                                         onlyIncludeEventIfItBelongsToTenant))
                                         .proceed();
    }

    @Override
    public Flux<PersistedEvent> pollEvents(AggregateType aggregateType,
                                           long fromInclusiveGlobalOrder,
                                           Optional<Integer> loadEventsByGlobalOrderBatchSize,
                                           Optional<Duration> pollingInterval,
                                           Optional<Tenant> onlyIncludeEventIfItBelongsToTenant,
                                           Optional<SubscriberId> subscriberId) {
        requireNonNull(aggregateType, "You must supply an aggregateType");
        requireNonNull(pollingInterval, "You must supply a pollingInterval option");
        requireNonNull(onlyIncludeEventIfItBelongsToTenant, "You must supply a onlyIncludeEventIfItBelongsToTenant option");
        requireNonNull(subscriberId, "You must supply a subscriberId option");

        var eventStreamLogName  = "EventStream:" + aggregateType + ":" + subscriberId.orElseGet(SubscriberId::random);
        var eventStoreStreamLog = LoggerFactory.getLogger(EventStore.class.getName() + ".PollingEventStream");

        long batchFetchSize = loadEventsByGlobalOrderBatchSize.orElse(100);
        eventStoreStreamLog.debug("[{}] Creating polling reactive '{}' EventStream with fromInclusiveGlobalOrder {} and batch size {}",
                                  eventStreamLogName,
                                  aggregateType,
                                  fromInclusiveGlobalOrder,
                                  batchFetchSize);
        final AtomicLong nextFromInclusiveGlobalOrder = new AtomicLong(fromInclusiveGlobalOrder);
        var persistedEventsFlux = Flux.defer(() -> {
            UnitOfWork unitOfWork;
            try {
                unitOfWork = unitOfWorkFactory.getOrCreateNewUnitOfWork();
            } catch (ConnectionException e) {
                eventStoreStreamLog.debug(msg("[{}] Experienced a Postgresql Connection issue, will return an empty Flux",
                                              eventStreamLogName), e);
                return Flux.empty();
            }

            try {
                var persistedEvents = loadEventsByGlobalOrder(aggregateType, LongRange.from(nextFromInclusiveGlobalOrder.get(), batchFetchSize), onlyIncludeEventIfItBelongsToTenant).collect(Collectors.toList());
                unitOfWork.rollback();
                if (persistedEvents.size() > 0) {
                    eventStoreStreamLog.debug("[{}] loadEventsByGlobalOrder using fromInclusiveGlobalOrder {} returned {} events",
                                              eventStreamLogName,
                                              nextFromInclusiveGlobalOrder.get(),
                                              persistedEvents.size());
                } else {
                    eventStoreStreamLog.trace("[{}] loadEventsByGlobalOrder using fromInclusiveGlobalOrder {} returned no events",
                                              eventStreamLogName,
                                              nextFromInclusiveGlobalOrder.get());
                }
                return Flux.fromIterable(persistedEvents);
            } catch (RuntimeException e) {
                log.error(msg("[{}] Polling failed", eventStreamLogName), e);
                if (unitOfWork != null) {
                    try {
                        unitOfWork.rollback(e);
                    } catch (Exception rollbackException) {
                        log.error(msg("[{}] Failed to rollback unit of work", eventStreamLogName), rollbackException);
                    }
                }
                eventStoreStreamLog.error(msg("[{}] Returning Error for '{}' EventStream with nextFromInclusiveGlobalOrder {}",
                                              eventStreamLogName,
                                              aggregateType,
                                              nextFromInclusiveGlobalOrder.get()),
                                          e);
                return Flux.error(e);
            }
        }).doOnNext(event -> {
            final long nextGlobalOrder = event.globalEventOrder().longValue() + 1L;
            eventStoreStreamLog.trace("[{}] Updating nextFromInclusiveGlobalOrder from {} to {}",
                                      eventStreamLogName,
                                      nextFromInclusiveGlobalOrder.get(),
                                      nextGlobalOrder);
            nextFromInclusiveGlobalOrder.set(nextGlobalOrder);
        }).doOnError(throwable -> {
            eventStoreStreamLog.error(msg("[{}] Failed {}",
                                          eventStreamLogName,
                                          throwable.getMessage()),
                                      throwable);
        });

        return persistedEventsFlux
                .repeatWhen(longFlux -> Flux.interval(pollingInterval.orElse(Duration.ofMillis(500))));
    }

    @Override
    public UnitOfWorkFactory getUnitOfWorkFactory() {
        return unitOfWorkFactory;
    }

    @Override
    public EventStore<CONFIG> addAggregateTypeConfiguration(CONFIG aggregateTypeConfiguration) {
        aggregateTypeConfigurations.addAggregateTypeConfiguration(aggregateTypeConfiguration);
        return this;
    }
}
