package dk.cloudcreate.essentials.components.eventsourced.aggregates;

import dk.cloudcreate.essentials.components.eventsourced.aggregates.classic.AggregateRoot;

/**
 * Common interface that all concrete (classical) {@link Aggregate}'s must implement. Most concrete implementations choose to extend the {@link AggregateRoot} class.
 *
 * @param <ID> the aggregate id (or stream-id) type
 * @see AggregateRoot
 */
public interface Aggregate<ID> {
    /**
     * The id of the aggregate (aka. the stream-id)
     */
    ID aggregateId();
}
