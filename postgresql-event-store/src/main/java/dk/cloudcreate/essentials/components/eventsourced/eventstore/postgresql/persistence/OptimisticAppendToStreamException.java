package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence;

public class OptimisticAppendToStreamException extends AppendToStreamException {
    public OptimisticAppendToStreamException(String msg, RuntimeException cause) {
        super(msg, cause);
    }
}
