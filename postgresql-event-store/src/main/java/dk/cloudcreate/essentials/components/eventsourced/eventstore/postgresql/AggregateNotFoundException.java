package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql;


import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

public class AggregateNotFoundException extends EventStoreException {
    public final Object        aggregateId;
    public final Class<?>      aggregateRootImplementationType;
    public final AggregateType aggregateType;

    public AggregateNotFoundException(Object aggregateId, Class<?> aggregateRootImplementationType, AggregateType aggregateType) {
        super(generateMessage(aggregateId, aggregateRootImplementationType, aggregateType));
        this.aggregateId = requireNonNull(aggregateId, "You must supply an aggregateId");
        this.aggregateRootImplementationType = requireNonNull(aggregateRootImplementationType, "You must supply an aggregateRootImplementationType");
        this.aggregateType = requireNonNull(aggregateType, "You must supply an aggregateType");
    }

    private static String generateMessage(Object aggregateId, Class<?> aggregateRootImplementationType, AggregateType aggregateType) {
        return msg("Couldn't find a '{}' aggregate root with Id '{}' belonging to the aggregateType '{}'",
                   aggregateRootImplementationType.getName(),
                   aggregateId,
                   aggregateType);
    }

    public AggregateNotFoundException(Object aggregateId, Class<?> aggregateRootImplementationType, AggregateType aggregateType, Exception cause) {
        super(generateMessage(aggregateId, aggregateRootImplementationType, aggregateType), cause);
        this.aggregateId = requireNonNull(aggregateId, "You must supply an aggregateId");
        this.aggregateRootImplementationType = requireNonNull(aggregateRootImplementationType, "You must supply an aggregateRootImplementationType");
        this.aggregateType = requireNonNull(aggregateType, "You must supply an aggregateType");
    }
}
