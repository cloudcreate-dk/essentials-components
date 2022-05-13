package dk.cloudcreate.essentials.components.eventsourced.aggregates.classic;

public class InitialEventIsMissingAggregateIdException extends AggregateRootException {
    public InitialEventIsMissingAggregateIdException(String msg) {
        super(msg);
    }
}
