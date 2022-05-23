package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql;

import dk.cloudcreate.essentials.components.common.transaction.UnitOfWork;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;

import java.util.Optional;

/**
 * Responsible for performing an in memory projection of a given aggregate instances event stream.<br>
 * Note: Unless the {@link InMemoryProjector} explicitly states otherwise, an
 * in memory projection is not automatically associated with a {@link UnitOfWork} and any changes to the projection
 * won't automatically be persisted.
 */
public interface InMemoryProjector {
    /**
     * Check if this {@link InMemoryProjector} supports the given <code>projectionType</code>
     *
     * @param projectionType the projection result type
     * @return true if this projector supports the given <code>projectionType</code>, otherwise false
     */
    boolean supports(Class<?> projectionType);

    /**
     * Perform an in memory projection of a given aggregate instances event stream.<br>
     * Note: Unless the {@link InMemoryProjector} explicitly states otherwise, an
     * in memory projection is not automatically associated with a {@link UnitOfWork} and any changes to the projection
     * won't automatically be persisted.<br>
     *
     * @param aggregateType  the name of the aggregate' event stream
     * @param aggregateId    the identifier for the aggregate instance
     * @param projectionType the type of projection result
     * @param <ID>           the aggregate id type
     * @param <PROJECTION>   the projection type
     * @return an {@link Optional} with the underlying {@link AggregateEventStream} projected using a matching {@link InMemoryProjector}
     * or {@link Optional#empty()} in case there the {@link AggregateEventStream} doesn't exist
     */
    <ID, PROJECTION> Optional<PROJECTION> projectEvents(AggregateType aggregateType, ID aggregateId, Class<PROJECTION> projectionType, EventStore<?> eventStore);
}
