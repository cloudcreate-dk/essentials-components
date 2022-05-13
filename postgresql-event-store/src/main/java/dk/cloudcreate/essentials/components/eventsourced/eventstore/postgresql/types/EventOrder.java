package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.types.LongType;

/**
 * Each event has its own unique position within the stream, also known as the event-order,
 * which defines the order, in which the events were added to the aggregates {@link AggregateEventStream}<br>
 * <br>
 * The first eventOrder should have value 0 - but ultimately it's a decision of the developer.<br>
 * This is also commonly called the version or sequenceNumber, and it's a sequential ever-growing number.<br>
 * related to a <b>specific</b> aggregate instance (as opposed to the {@link PersistedEvent#globalEventOrder()} which contains
 * the order of ALL events related to a specific {@link AggregateType})
 */
public class EventOrder extends LongType<EventOrder> {
    /**
     * Special value that signifies the no previous events have been persisted in relation to a given aggregate
     */
    public static final EventOrder NO_EVENTS_PERSISTED = EventOrder.of(-1);
    /**
     * Special value that contains the {@link EventOrder} of the FIRST Event persisted in context of a given aggregate id
     */
    public static final EventOrder FIRST_EVENT_ORDER   = EventOrder.of(0);

    public EventOrder(Long value) {
        super(value);
    }

    public static EventOrder of(long value) {
        return new EventOrder(value);
    }

    public EventOrder increaseAndGet() {
        return new EventOrder(value() + 1);
    }
}