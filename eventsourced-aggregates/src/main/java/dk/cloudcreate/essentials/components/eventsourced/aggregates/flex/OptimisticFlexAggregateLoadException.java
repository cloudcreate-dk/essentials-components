package dk.cloudcreate.essentials.components.eventsourced.aggregates.flex;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStoreException;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventOrder;

import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

public class OptimisticFlexAggregateLoadException extends EventStoreException {
    public final Object                               aggregateId;
    public final Class<? extends FlexAggregate<?, ?>> aggregateType;
    public final EventOrder                           expectedLatestEventOrder;
    public final EventOrder                           actualLatestEventOrder;

    public OptimisticFlexAggregateLoadException(Object aggregateId, Class<? extends FlexAggregate<?, ?>> aggregateType, EventOrder expectedLatestEventOrder, EventOrder actualLatestEventOrder) {
        super(msg("Expected expectedLatestEventOrder '{}' for '{}' with id '{}' but found '{}' (actualLatestEventOrder) in the EventStore",
                  expectedLatestEventOrder,
                  aggregateType.getName(),
                  aggregateId,
                  actualLatestEventOrder));
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.expectedLatestEventOrder = expectedLatestEventOrder;
        this.actualLatestEventOrder = actualLatestEventOrder;
    }
}
