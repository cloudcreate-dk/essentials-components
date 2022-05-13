package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types;

import dk.cloudcreate.essentials.types.IntegerType;

/**
 * The revision of an Event type- first revision has value 1
 */
public class EventRevision extends IntegerType<EventRevision> {

    public EventRevision(Integer value) {
        super(value);
    }

    public static EventRevision of(int value) {
        return new EventRevision(value);
    }
}