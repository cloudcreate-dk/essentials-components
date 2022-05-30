package dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful;

import dk.cloudcreate.essentials.components.common.transaction.UnitOfWork;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.*;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.flex.FlexAggregate;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;

/**
 * A stateful {@link Aggregate} is the most common form of Aggregate design in Object Oriented languages.<br>
 * What makes an {@link Aggregate} stateful is the fact that any changes, i.e. Events applied as the result of calling command methods on the aggregate instance, are stored
 * within the {@link StatefulAggregate} and can be queried using {@link #getUncommittedChanges()} and reset (e.g. after a transaction/{@link UnitOfWork} has completed)
 * using {@link #markChangesAsCommitted()}<br>
 * <br>
 * See {@link FlexAggregate} for an immutable {@link Aggregate} design
 *
 * @param <ID>             the type of id
 * @param <EVENT_TYPE>     the type of event
 * @param <AGGREGATE_TYPE> the aggregate type
 */
public interface StatefulAggregate<ID, EVENT_TYPE, AGGREGATE_TYPE extends StatefulAggregate<ID, EVENT_TYPE, AGGREGATE_TYPE>> extends Aggregate<ID, AGGREGATE_TYPE> {
    /**
     * Query any changes to the Aggregate,  i.e. Events applied as the result of calling command methods on the aggregate instance,
     *
     * @return the changes to the aggregate
     */
    EventsToPersist<ID, EVENT_TYPE> getUncommittedChanges();

    /**
     * Resets the {@link #getUncommittedChanges()} - effectively marking them as having been persisted
     * and committed to the underlying {@link EventStore}
     */
    void markChangesAsCommitted();
}
