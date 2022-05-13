package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.types.LongType;

/**
 * The Global Order is a sequential ever-growing number, which that tracks the order in which events have been stored in the {@link EventStore} table
 * across all {@link AggregateEventStream}'s with the same {@link AggregateType}.<br>
 * The first global-event-order has value 1, since this is the initial value for a Postgresql BIGINT IDENTITY column.
 */
public class GlobalEventOrder extends LongType<GlobalEventOrder> {
    /**
     * Special value that contains the {@link GlobalEventOrder} of the FIRST Event persisted in context of a given {@link AggregateType}
     */
    public static final GlobalEventOrder FIRST_GLOBAL_EVENT_ORDER = GlobalEventOrder.of(1);

    public GlobalEventOrder(Long value) {
        super(value);
    }

    public static GlobalEventOrder of(long value) {
        return new GlobalEventOrder(value);
    }

    public GlobalEventOrder increment() {
        return new GlobalEventOrder(value + 1);
    }

    public GlobalEventOrder decrement() {
        return new GlobalEventOrder(value - 1);
    }
}