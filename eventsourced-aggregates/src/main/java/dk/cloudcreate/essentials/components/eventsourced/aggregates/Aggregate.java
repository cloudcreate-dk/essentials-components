package dk.cloudcreate.essentials.components.eventsourced.aggregates;

import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.classic.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateEventStream;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventOrder;

/**
 * Common interface that all concrete (classical) {@link Aggregate}'s must implement. Most concrete implementations choose to extend the {@link AggregateRoot} class.
 *
 * @param <ID> the aggregate id (or stream-id) type
 * @see AggregateRoot
 */
public interface Aggregate<ID, AGGREGATE_TYPE extends Aggregate<ID, AGGREGATE_TYPE>> {
    /**
     * The id of the aggregate (aka. the stream-id)
     */
    ID aggregateId();

    /**
     * Has the aggregate been initialized using previously recorded/persisted events (aka. historic events) using the {@link #rehydrate(AggregateEventStream)} method
     */
    boolean hasBeenRehydrated();

    /**
     * Effectively performs a leftFold over all the previously persisted events related to this aggregate instance
     *
     * @param persistedEvents the previous persisted events related to this aggregate instance, aka. the aggregates history
     * @return the same aggregate instance (self)
     */
    @SuppressWarnings("unchecked")
    AGGREGATE_TYPE rehydrate(AggregateEventStream<ID> persistedEvents);

    /**
     * Get the eventOrder of the last event during aggregate hydration (using the {@link #rehydrate(AggregateEventStream)} method)
     *
     * @return the event order of the last applied {@link Event} or {@link EventOrder#NO_EVENTS_PERSISTED} in case no
     * events has ever been applied to the aggregate
     */
    EventOrder eventOrderOfLastRehydratedEvent();
}
