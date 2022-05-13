package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence;

import dk.cloudcreate.essentials.components.common.types.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.PostgresqlEventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.UnitOfWork;
import dk.cloudcreate.essentials.types.LongRange;

import java.util.*;
import java.util.stream.Stream;

/**
 * Represents the strategy that the {@link PostgresqlEventStore} will use to persist and load events related to a named Event Stream.<br>
 * The {@link AggregateEventStreamPersistenceStrategy} is managed by the {@link PostgresqlEventStore} and is <b>shared</b>
 * between all the {@link AggregateEventStream}'s it manages.
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public interface AggregateEventStreamPersistenceStrategy<CONFIG extends AggregateTypeConfiguration> {
    /**
     * Add Event stream configuration related to a specific {@link AggregateType} as indicated by {@link AggregateTypeConfiguration#aggregateType }
     *
     * @param eventStreamConfiguration the event stream configuration
     * @return this strategy instance
     */
    AggregateEventStreamPersistenceStrategy<CONFIG > addAggregateTypeConfiguration(CONFIG eventStreamConfiguration);

    /**
     * Persist a List of persistable events
     *
     * @param unitOfWork                  the current unitOfWork
     * @param aggregateType               the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId                 The id of the aggregate identifier (aka the stream id) that the <code>persistableEvents</code> are related to
     * @param appendEventsAfterEventOrder the {@link PersistedEvent#eventOrder()} of the last event persisted related to the given <code>aggregateId</code>.
     *                                    This means that events in <code>persistableEvents</code> will receive {@link PersistedEvent#eventOrder()} starting from and including <code>appendEventsAfterEventOrder + 1</code>
     * @param persistableEvents           the list of persistable events (i.e. events that haven't yet been persisted)
     * @return the {@link PersistedEvent}'s - each one corresponds 1-1 and IN-ORDER with the <code>persistableEvents</code>
     */
    <STREAM_ID> AggregateEventStream<STREAM_ID> persist(UnitOfWork unitOfWork, AggregateType aggregateType, STREAM_ID aggregateId, Optional<Long> appendEventsAfterEventOrder, List<?> persistableEvents);

    /**
     * Load the last {@link PersistedEvent} in relation to the specified <code>configuration</code> and <code>aggregateId</code>
     *
     * @param aggregateId   the identifier of the aggregate we want to find the last {@link PersistedEvent}
     * @param aggregateType               the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param <STREAM_ID>   the id type for the aggregate
     * @return an {@link Optional} with the last {@link PersistedEvent} related to the <code>aggregateId</code> instance or
     * {@link Optional#empty()} if no events have been persisted before
     */
    <STREAM_ID> Optional<PersistedEvent> loadLastPersistedEventRelatedTo(UnitOfWork unitOfWork, AggregateType aggregateType, STREAM_ID aggregateId);

    /**
     * Load all events related to the given <code>configuration</code>, sharing the same <code>aggregateId</code> and having a {@link PersistedEvent#eventOrder()} within the <code>eventOrderRange</code>
     *
     * @param unitOfWork                            the current unit of work
     * @param aggregateType                         the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param aggregateId                           The id of the aggregate identifier (aka the stream id) that the events is related to
     * @param eventOrderRange                       the range of {@link PersistedEvent#globalEventOrder()}'s we want Events for
     * @param onlyIncludeEventsIfTheyBelongToTenant Matching events will only be returned if the {@link PersistedEvent#tenant()} matches
     *                                              the given tenant specified in this parameter OR if the Event doesn't specify ANY tenant
     * @return the {@link PersistedEvent} related to the event stream's
     */
    <STREAM_ID> Optional<AggregateEventStream<STREAM_ID>> loadAggregateEvents(UnitOfWork unitOfWork, AggregateType aggregateType, STREAM_ID aggregateId, LongRange eventOrderRange, Optional<Tenant> onlyIncludeEventsIfTheyBelongToTenant);

    /**
     * Load all events related to the given <code>configuration</code> and having a {@link PersistedEvent#globalEventOrder()} within the <code>globalOrderRange</code>
     *
     * @param unitOfWork                            the current unit of work
     * @param aggregateType                         the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param globalOrderRange                      the range of {@link PersistedEvent#globalEventOrder()}'s we want Events for
     * @param onlyIncludeEventsIfTheyBelongToTenant Matching events will only be returned if the {@link PersistedEvent#tenant()} matches
     *                                              the given tenant specified in this parameter OR if the Event doesn't specify ANY tenant
     * @return the {@link PersistedEvent}'s
     */
    Stream<PersistedEvent> loadEventsByGlobalOrder(UnitOfWork unitOfWork, AggregateType aggregateType, LongRange globalOrderRange, Optional<Tenant> onlyIncludeEventsIfTheyBelongToTenant);

    /**
     * Load the event belonging to the given <code>configuration</code> and having the specified <code>eventId</code>
     *
     * @param unitOfWork    the current unit of work
     * @param aggregateType the aggregate type that the underlying {@link AggregateEventStream} is associated with
     * @param eventId       the identifier of the {@link PersistedEvent}
     * @return an {@link Optional} with the {@link PersistedEvent} or {@link Optional#empty()} if the event couldn't be found
     */
    Optional<PersistedEvent> loadEvent(UnitOfWork unitOfWork, AggregateType aggregateType, EventId eventId);
}
