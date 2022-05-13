package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStoreException;

public class JSONSerializationException extends EventStoreException {
    public JSONSerializationException(String msg, Exception cause) {
        super(msg, cause);
    }
}
