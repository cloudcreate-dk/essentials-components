package dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.classic;

import dk.cloudcreate.essentials.components.eventsourced.aggregates.AggregateException;

public class InitialEventIsMissingAggregateIdException extends AggregateException {
    public InitialEventIsMissingAggregateIdException(String msg) {
        super(msg);
    }
}
