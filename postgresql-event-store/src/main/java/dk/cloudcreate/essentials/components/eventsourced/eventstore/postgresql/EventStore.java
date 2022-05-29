package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql;

import dk.cloudcreate.essentials.components.common.transaction.*;
import dk.cloudcreate.essentials.components.common.types.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.bus.PersistedEvents;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.interceptor.EventStoreInterceptor;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.AggregateTypeConfiguration;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.*;
import dk.cloudcreate.essentials.reactive.LocalEventBus;
import dk.cloudcreate.essentials.types.*;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;

import static dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventOrder.FIRST_EVENT_ORDER;
import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * The {@link EventStore} interface providing capabilities to persist and load events, such as {@link #appendToStream(AggregateType, Object, Optional, List)}/
 * {@link #fetchStream(AggregateType, Object, LongRange, Optional)}/{@link #loadEvent(AggregateType, EventId)}/
 * {@link #loadLastPersistedEventRelatedTo(AggregateType, Object)}
 *
 * @see PostgresqlEventStore
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public interface EventStore<CONFIG extends AggregateTypeConfiguration> {
    /**
     * Get the {@link UnitOfWorkFactory} associated with the {@link EventStore}
     *
     * @return the {@link UnitOfWorkFactory} associated with the {@link EventStore}
     */
    UnitOfWorkFactory getUnitOfWorkFactory();

    /**
     * Add Event stream configuration related to a specific {@link AggregateType} as indicated by {@link AggregateTypeConfiguration#aggregateType }
     *
     * @param eventStreamConfiguration the event stream configuration
     * @return this strategy instance
     */
    EventStore<CONFIG> addAggregateTypeConfiguration(CONFIG eventStreamConfiguration);

    /**
     * Get the {@link LocalEventBus}, which can be used for transactionally listening for {@link EventStore} changes
     *
     * @return the local event bus
     */
    LocalEventBus<PersistedEvents> localEventBus();

    /**
     * Add a generic {@link InMemoryProjector}. When {@link #inMemoryProjection(AggregateType, Object, Class)} is called
     * it will first check for any {@link InMemoryProjector}'s registered using {@link #addSpecificInMemoryProjector(Class, InMemoryProjector)}<br>
     * After that it will try and find the first {@link InMemoryProjector#supports(Class)}, registered using this method, which reports true for the given projection type
     *
     * @param inMemoryProjector the in memory projector
     * @return this event store instance
     */
    EventStore<CONFIG> addGenericInMemoryProjector(InMemoryProjector inMemoryProjector);

    /**
     * Add a projection-type specific {@link InMemoryProjector}. When {@link #inMemoryProjection(AggregateType, Object, Class)} is called
     * it will first check for any {@link InMemoryProjector}'s registered using {@link #addSpecificInMemoryProjector(Class, InMemoryProjector)}<br>
     * After that it will try and find the first {@link InMemoryProjector#supports(Class)}, registered using {@link #addGenericInMemoryProjector(InMemoryProjector)}, which reports true for the given projection type
     *
     * @param projectionType    the projection type
     * @param inMemoryProjector the in memory projector
     * @return this event store instance
     */
    EventStore<CONFIG> addSpecificInMemoryProjector(Class<?> projectionType, InMemoryProjector inMemoryProjector);

    /**
     * Add an {@link EventStoreInterceptor} to the {@link EventStore}
     *
     * @param eventStoreInterceptor the interceptor to add
     * @return this event store instance
     */
    EventStore<CONFIG> addEventStoreInterceptor(EventStoreInterceptor eventStoreInterceptor);

    /**
     * Append the <code>events</code> to the {@link AggregateEventStream} related to the aggregate with id <code>aggregateId</code> and which
     * is associated with the <code>aggregateType</code>. This method will call {@link #loadLastPersistedEventRelatedTo(AggregateType, Object)}
     * to resolve the {@link EventOrder} of the last persisted event for this aggregate instance.<br>
     * If you know the {@link EventOrder} of the last persisted event, then please use either {@link #appendToStream(AggregateType, Object, Optional, List)}
     * or {@link #appendToStream(AggregateType, Object, EventOrder, List)} as this involves one less event store query
     *
     * @param aggregateType the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId   the identifier of the aggregate we want to persist events related to
     * @param events        the events to persist
     * @param <ID>          the id type for the aggregate
     * @return an {@link AggregateEventStream} instance containing the {@link PersistedEvent}'s that correspond to the provided <code>events</code>
     */
    default <ID> AggregateEventStream<ID> appendToStream(AggregateType aggregateType,
                                                         ID aggregateId,
                                                         List<?> events) {
        return appendToStream(aggregateType,
                              aggregateId,
                              Optional.empty(),
                              events);
    }

    /**
     * Append the <code>events</code> to the {@link AggregateEventStream} related to the aggregate with id <code>aggregateId</code> and which
     * is associated with the <code>aggregateType</code>
     *
     * @param aggregateType               the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId                 the identifier of the aggregate we want to persist events related to
     * @param appendEventsAfterEventOrder append the <code>events</code> after this event order, i.e. the first event in the <code>events</code> list
     *                                    will receive an {@link PersistedEvent#eventOrder()} which is <code>appendEventsAfterEventOrder +  1</code><br>
     *                                    If it's the very first event to be appended, then you can provide {@link EventOrder#NO_EVENTS_PERSISTED}
     * @param events                      the events to persist
     * @param <ID>                        the id type for the aggregate
     * @return an {@link AggregateEventStream} instance containing the {@link PersistedEvent}'s that correspond to the provided <code>events</code>
     */
    default <ID> AggregateEventStream<ID> appendToStream(AggregateType aggregateType,
                                                         ID aggregateId,
                                                         EventOrder appendEventsAfterEventOrder,
                                                         List<?> events) {
        return appendToStream(aggregateType,
                              aggregateId,
                              Optional.ofNullable(appendEventsAfterEventOrder).map(NumberType::longValue),
                              events);
    }

    /**
     * Append the <code>events</code> to the {@link AggregateEventStream} related to the aggregate with id <code>aggregateId</code> and which
     * is associated with the <code>aggregateType</code>
     *
     * @param aggregateType               the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId                 the identifier of the aggregate we want to persist events related to
     * @param appendEventsAfterEventOrder append the <code>events</code> after this event order, i.e. the first event in the <code>events</code> list
     *                                    will receive {@link PersistedEvent#eventOrder()} which is <code>appendEventsAfterEventOrder +  1</code><br>
     *                                    Use the value of {@link EventOrder#NO_EVENTS_PERSISTED} in case no events have been persisted for this aggregate instance yet<br>
     *                                    Note: If the <code>appendEventsAfterEventOrder</code> is {@link Optional#empty() then the implementation will call
     *                                    {@link #loadLastPersistedEventRelatedTo(AggregateType, Object)}
     *                                    to resolve the {@link EventOrder} of the last persisted event for this aggregate instance.
     * @param events                      the events to persist
     * @param <ID>                        the id type for the aggregate
     * @return an {@link AggregateEventStream} instance containing the {@link PersistedEvent}'s that correspond to the provided <code>events</code>
     */
    <ID> AggregateEventStream<ID> appendToStream(AggregateType aggregateType,
                                                 ID aggregateId,
                                                 Optional<Long> appendEventsAfterEventOrder,
                                                 List<?> events);

    /**
     * Load the last {@link PersistedEvent} in relation to the specified <code>aggregateType</code> and <code>aggregateId</code>
     *
     * @param aggregateType the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId   the identifier of the aggregate we want to find the last {@link PersistedEvent}
     * @param <ID>          the id type for the aggregate
     * @return an {@link Optional} with the last {@link PersistedEvent} related to the <code>aggregateId</code> instance or
     * {@link Optional#empty()} if no events have been persisted before
     */
    <ID> Optional<PersistedEvent> loadLastPersistedEventRelatedTo(AggregateType aggregateType,
                                                                  ID aggregateId);

    /**
     * Load the event belonging to <code>aggregateType</code> and having the specified <code>eventId</code>
     *
     * @param aggregateType the aggregate type that the underlying {@link AggregateEventStream}, which should contain the {@link PersistedEvent} with the given <code>eventId</code>, is associated with
     * @param eventId       the identifier of the {@link PersistedEvent}
     * @return an {@link Optional} with the {@link PersistedEvent} or {@link Optional#empty()} if the event couldn't be found
     */
    Optional<PersistedEvent> loadEvent(AggregateType aggregateType,
                                       EventId eventId);

    /**
     * Fetch the {@link AggregateEventStream} related to the aggregate with id <code>aggregateId</code> and which
     * is associated with the <code>aggregateType</code><br>
     * The {@link AggregateEventStream} will include {@link PersistedEvent} from {@link EventOrder#FIRST_EVENT_ORDER}
     *
     * @param aggregateType the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId   the identifier of the aggregate we want to fetch the {@link AggregateEventStream} for
     * @param <ID>          the id type for the aggregate
     * @return an {@link Optional} with the {@link AggregateEventStream} or {@link Optional#empty()} if no Events had
     * been persisted related to the given <code>aggregateId</code> associated with the <code>aggregateType</code>
     */
    default <ID> Optional<AggregateEventStream<ID>> fetchStream(AggregateType aggregateType,
                                                                ID aggregateId) {
        return fetchStream(aggregateType,
                           aggregateId,
                           LongRange.from(FIRST_EVENT_ORDER.longValue()));
    }

    /**
     * Fetch the {@link AggregateEventStream} related to the aggregate with id <code>aggregateId</code> and which
     * is associated with the <code>aggregateType</code><br>
     * The {@link AggregateEventStream} will include {@link PersistedEvent} from the specified <code>eventOrderRange</code>
     *
     * @param aggregateType   the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId     the identifier of the aggregate we want to fetch the {@link AggregateEventStream} for
     * @param eventOrderRange the range of {@link EventOrder}'s to include in the {@link AggregateEventStream}
     * @param <ID>            the id type for the aggregate
     * @return an {@link Optional} with the {@link AggregateEventStream} or {@link Optional#empty()} if no Events had
     * been persisted related to the given <code>aggregateId</code> associated with the <code>aggregateType</code>
     */
    default <ID> Optional<AggregateEventStream<ID>> fetchStream(AggregateType aggregateType,
                                                                ID aggregateId,
                                                                LongRange eventOrderRange) {
        return fetchStream(aggregateType,
                           aggregateId,
                           eventOrderRange,
                           Optional.empty());
    }

    /**
     * Fetch the {@link AggregateEventStream} related to the aggregate with id <code>aggregateId</code> and which
     * is associated with the <code>aggregateType</code><br>
     * Only {@link PersistedEvent}'s belonging to the specified <code>tenant</code> will be returned<br>
     * The {@link AggregateEventStream} will include {@link PersistedEvent} from {@link EventOrder#FIRST_EVENT_ORDER}
     *
     * @param aggregateType the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId   the identifier of the aggregate we want to fetch the {@link AggregateEventStream} for
     * @param tenant        only return events belonging to the specified tenant
     * @param <ID>          the id type for the aggregate
     * @return an {@link Optional} with the {@link AggregateEventStream} or {@link Optional#empty()} if no Events had
     * been persisted related to the given <code>aggregateId</code> associated with the <code>aggregateType</code>
     */
    default <ID> Optional<AggregateEventStream<ID>> fetchStream(AggregateType aggregateType,
                                                                ID aggregateId,
                                                                Tenant tenant) {
        return fetchStream(aggregateType,
                           aggregateId,
                           LongRange.from(FIRST_EVENT_ORDER.longValue()),
                           Optional.of(tenant));
    }

    /**
     * Fetch the {@link AggregateEventStream} related to the aggregate with id <code>aggregateId</code> and which
     * is associated with the <code>aggregateType</code><br>
     * Only {@link PersistedEvent}'s belonging to the specified <code>tenant</code> will be returned<br>
     * The {@link AggregateEventStream} will include {@link PersistedEvent} from the specified <code>eventOrderRange</code>
     *
     * @param aggregateType   the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId     the identifier of the aggregate we want to fetch the {@link AggregateEventStream} for
     * @param eventOrderRange the range of {@link EventOrder}'s to include in the {@link AggregateEventStream}
     * @param tenant          only return events belonging to the specified tenant
     * @param <ID>            the id type for the aggregate
     * @return an {@link Optional} with the {@link AggregateEventStream} or {@link Optional#empty()} if no Events had
     * been persisted related to the given <code>aggregateId</code> associated with the <code>aggregateType</code>
     */
    default <ID> Optional<AggregateEventStream<ID>> fetchStream(AggregateType aggregateType,
                                                                ID aggregateId,
                                                                LongRange eventOrderRange,
                                                                Tenant tenant) {
        return fetchStream(aggregateType,
                           aggregateId,
                           eventOrderRange,
                           Optional.of(tenant));
    }

    /**
     * Fetch the {@link AggregateEventStream} related to the aggregate with id <code>aggregateId</code> and which
     * is associated with the <code>aggregateType</code><br>
     * The {@link AggregateEventStream} will include {@link PersistedEvent} from the specified <code>eventOrderRange</code><br>
     * If the <code>tenant</code> arguments is {@link Optional#isPresent()}, then only {@link PersistedEvent}'s belonging to the specified <code>tenant</code> and matching the specified <code>eventOrderRange</code> will be returned<br>
     * Otherwise all {@link PersistedEvent}'s matching the specified <code>eventOrderRange</code> will be returned
     *
     * @param aggregateType   the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId     the identifier of the aggregate we want to fetch the {@link AggregateEventStream} for
     * @param eventOrderRange the range of {@link EventOrder}'s to include in the {@link AggregateEventStream}
     * @param tenant          only return events belonging to the specified tenant (if {@link Optional#isPresent()})
     * @param <ID>            the id type for the aggregate
     * @return an {@link Optional} with the {@link AggregateEventStream} or {@link Optional#empty()} if no Events had
     * been persisted related to the given <code>aggregateId</code> associated with the <code>aggregateType</code>
     */
    <ID> Optional<AggregateEventStream<ID>> fetchStream(AggregateType aggregateType,
                                                        ID aggregateId,
                                                        LongRange eventOrderRange,
                                                        Optional<Tenant> tenant);

    /**
     * Perform an in memory projection of a given aggregate instances event stream.<br>
     * Note: Unless the {@link InMemoryProjector} explicitly states otherwise, an
     * in memory projection is not automatically associated with a {@link UnitOfWork} and any changes to the projection
     * won't automatically be persisted.<br>
     * A matching {@link InMemoryProjector} must be registered in advance, either using {@link #addSpecificInMemoryProjector(Class, InMemoryProjector)}
     * or {@link #addGenericInMemoryProjector(InMemoryProjector)}
     *
     * @param aggregateType  the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId    the identifier for the aggregate instance
     * @param projectionType the type of projection result
     * @param <ID>           the aggregate id type
     * @param <PROJECTION>   the projection type
     * @return an {@link Optional} with the underlying {@link AggregateEventStream} projected using a matching {@link InMemoryProjector}
     * or {@link Optional#empty()} in case there the {@link AggregateEventStream} doesn't exist
     */
    <ID, PROJECTION> Optional<PROJECTION> inMemoryProjection(AggregateType aggregateType,
                                                             ID aggregateId,
                                                             Class<PROJECTION> projectionType);

    /**
     * Perform an in memory projection of a given aggregate instances event stream.<br>
     * Note: Unless the {@link InMemoryProjector} explicitly states otherwise, an
     * in memory projection is not automatically associated with a {@link UnitOfWork} and any changes to the projection
     * won't automatically be persisted.<br>
     *
     * @param aggregateType     the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId       the identifier for the aggregate instance
     * @param projectionType    the type of projection result
     * @param inMemoryProjector the projector to use
     * @param <ID>              the aggregate id type
     * @param <PROJECTION>      the projection type
     * @return an {@link Optional} with the underlying {@link AggregateEventStream} projected using the supplied {@link InMemoryProjector}
     * or {@link Optional#empty()} in case there the {@link AggregateEventStream} doesn't exist
     */
    <ID, PROJECTION> Optional<PROJECTION> inMemoryProjection(AggregateType aggregateType,
                                                             ID aggregateId,
                                                             Class<PROJECTION> projectionType,
                                                             InMemoryProjector inMemoryProjector);

    /**
     * Load all events relating to the provided <code>aggregateType</code> by their {@link PersistedEvent#globalEventOrder()}
     *
     * @param aggregateType         the aggregate type that the underlying events are associated with
     * @param globalEventOrderRange the range on {@link GlobalEventOrder}'s to include in the stream
     * @return a stream of all {@link PersistedEvent}'s matching the <code>aggregateType</code> and the <code>globalEventOrderRange</code>
     */
    default Stream<PersistedEvent> loadEventsByGlobalOrder(AggregateType aggregateType,
                                                           LongRange globalEventOrderRange) {
        return loadEventsByGlobalOrder(aggregateType,
                                       globalEventOrderRange,
                                       Optional.empty());
    }

    /**
     * Load all events, belonging to the specified <code>onlyIncludeEventIfItBelongsToTenant</code> option, and which are relating to the provided <code>aggregateType</code> by their {@link PersistedEvent#globalEventOrder()}
     *
     * @param aggregateType                       the aggregate type that the underlying events are associated with
     * @param globalEventOrderRange               the range on {@link GlobalEventOrder}'s to include in the stream
     * @param onlyIncludeEventIfItBelongsToTenant if {@link Optional#isPresent()} then only include events that belong to the specified {@link Tenant}, otherwise all Events matching the criteria are returned
     * @return a stream of all {@link PersistedEvent}'s matching the <code>aggregateType</code> and the <code>globalEventOrderRange</code>
     */
    Stream<PersistedEvent> loadEventsByGlobalOrder(AggregateType aggregateType,
                                                   LongRange globalEventOrderRange,
                                                   Optional<Tenant> onlyIncludeEventIfItBelongsToTenant);

    /**
     * Asynchronously poll for new events related to the given <code>aggregateType</code>
     *
     * @param aggregateType                       the aggregate type that the underlying events are associated with
     * @param fromInclusiveGlobalOrder            the first {@link GlobalEventOrder}'s to include in the returned {@link Flux}
     * @param loadEventsByGlobalOrderBatchSize    how many events should we maximum return from every call to {@link #loadEventsByGlobalOrder(AggregateType, LongRange, Optional)}. Default value is 100.
     * @param pollingInterval                     how often should the {@link EventStore} be polled for new events
     * @param onlyIncludeEventIfItBelongsToTenant if {@link Optional#isPresent()} then only include events that belong to the specified {@link Tenant}, otherwise all Events matching the criteria are returned
     * @param subscriptionId                      unique subscriber id which is used for creating a unique logger name. If {@link Optional#empty()} then a UUID value is generated and used
     * @return a {@link Flux} that asynchronously will publish events associated with the provided <code>aggregateType</code>
     */
    default Flux<PersistedEvent> pollEvents(AggregateType aggregateType,
                                            GlobalEventOrder fromInclusiveGlobalOrder,
                                            Optional<Integer> loadEventsByGlobalOrderBatchSize,
                                            Optional<Duration> pollingInterval,
                                            Optional<Tenant> onlyIncludeEventIfItBelongsToTenant,
                                            Optional<SubscriberId> subscriptionId) {
        requireNonNull(fromInclusiveGlobalOrder, "No fromInclusiveGlobalOrder value provided");
        return pollEvents(aggregateType,
                          fromInclusiveGlobalOrder.longValue(),
                          loadEventsByGlobalOrderBatchSize,
                          pollingInterval,
                          onlyIncludeEventIfItBelongsToTenant,
                          subscriptionId);
    }

    /**
     * Asynchronously poll for new events related to the given <code>aggregateType</code>
     *
     * @param aggregateType                       the aggregate type that the underlying events are associated with
     * @param fromInclusiveGlobalOrder            the first {@link GlobalEventOrder}'s to include in the returned {@link Flux}
     * @param loadEventsByGlobalOrderBatchSize    how many events should we maximum return from every call to {@link #loadEventsByGlobalOrder(AggregateType, LongRange, Optional)}. Default value is 100.
     * @param pollingInterval                     how often should the {@link EventStore} be polled for new events. Default is every 500 milliseconds
     * @param onlyIncludeEventIfItBelongsToTenant if {@link Optional#isPresent()} then only include events that belong to the specified {@link Tenant}, otherwise all Events matching the criteria are returned
     * @param subscriptionId                      unique subscriber id which is used for creating a unique logger name. If {@link Optional#empty()} then a UUID value is generated and used
     * @return a {@link Flux} that asynchronously will publish events associated with the provided <code>aggregateType</code>
     */
    Flux<PersistedEvent> pollEvents(AggregateType aggregateType,
                                    long fromInclusiveGlobalOrder,
                                    Optional<Integer> loadEventsByGlobalOrderBatchSize,
                                    Optional<Duration> pollingInterval,
                                    Optional<Tenant> onlyIncludeEventIfItBelongsToTenant,
                                    Optional<SubscriberId> subscriptionId);
}
