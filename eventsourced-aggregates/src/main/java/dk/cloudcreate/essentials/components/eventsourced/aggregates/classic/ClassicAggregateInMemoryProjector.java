package dk.cloudcreate.essentials.components.eventsourced.aggregates.classic;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.UnitOfWork;

import java.util.Optional;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * {@link AggregateRoot} specific {@link InMemoryProjector}<br>
 * Note: An in memory projection is never associated with a {@link UnitOfWork} and any changes to the aggregate
 * won't automatically be persisted. Use the {@link AggregateRootRepository} for transactional cases.
 */
public class ClassicAggregateInMemoryProjector implements InMemoryProjector {
    private final AggregateRootInstanceFactory aggregateRootInstanceFactory;

    public ClassicAggregateInMemoryProjector(AggregateRootInstanceFactory aggregateRootInstanceFactory) {
        this.aggregateRootInstanceFactory = requireNonNull(aggregateRootInstanceFactory, "No aggregateRootFactory instance provided");
    }

    @Override
    public boolean supports(Class<?> projectionType) {
        return AggregateRoot.class.isAssignableFrom(requireNonNull(projectionType, "No aggregateType provided"));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <ID, PROJECTION> Optional<PROJECTION> projectEvents(AggregateType aggregateEventStreamName, ID aggregateId, Class<PROJECTION> projectionType, EventStore<?> eventStore) {
        requireNonNull(aggregateEventStreamName, "No eventStreamName provided");
        requireNonNull(aggregateId, "No aggregateId provided");
        requireNonNull(projectionType, "No aggregateType provided");
        requireNonNull(eventStore, "No eventStore instance provided");
        if (!supports(projectionType)) {
            throw new IllegalArgumentException(msg("The provided aggregateType '{}' isn't supported", projectionType.getName()));
        }
        // TODO: Add support for Aggregate snapshot's

        var aggregate = (AggregateRoot<ID, ?>) aggregateRootInstanceFactory.create(aggregateId.getClass(), projectionType);
        var possibleEventStream = eventStore.fetchStream(aggregateEventStreamName,
                                                         aggregateId);

        return (Optional<PROJECTION>) possibleEventStream.map(aggregate::rehydrate);
    }
}
