package dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful;

import dk.cloudcreate.essentials.components.common.transaction.UnitOfWork;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.Aggregate;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.classic.AggregateRoot;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;

import java.util.Optional;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * {@link Aggregate} specific {@link InMemoryProjector}<br>
 * Note: An in memory projection is never associated with a {@link UnitOfWork} and any changes to the aggregate
 * won't automatically be persisted. Use the {@link StatefulAggregateRepository} for transactional cases.
 */
public class StatefulAggregateInMemoryProjector implements InMemoryProjector {
    private final StatefulAggregateInstanceFactory aggregateRootInstanceFactory;

    public StatefulAggregateInMemoryProjector(StatefulAggregateInstanceFactory aggregateRootInstanceFactory) {
        this.aggregateRootInstanceFactory = requireNonNull(aggregateRootInstanceFactory, "No aggregateRootFactory instance provided");
    }

    @Override
    public boolean supports(Class<?> projectionType) {
        return AggregateRoot.class.isAssignableFrom(requireNonNull(projectionType, "No aggregateType provided"));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <ID, PROJECTION> Optional<PROJECTION> projectEvents(AggregateType aggregateType, ID aggregateId, Class<PROJECTION> projectionType, EventStore<?> eventStore) {
        requireNonNull(aggregateType, "No aggregateType provided");
        requireNonNull(aggregateId, "No aggregateId provided");
        requireNonNull(projectionType, "No aggregateType provided");
        requireNonNull(eventStore, "No eventStore instance provided");
        if (!supports(projectionType)) {
            throw new IllegalArgumentException(msg("The provided aggregateType '{}' isn't supported", projectionType.getName()));
        }
        // TODO: Add support for Aggregate snapshot's

        var aggregate = (StatefulAggregate<ID, ?, ?>) aggregateRootInstanceFactory.create(aggregateId, projectionType);
        var possibleEventStream = eventStore.fetchStream(aggregateType,
                                                         aggregateId);

        return (Optional<PROJECTION>) possibleEventStream.map(aggregate::rehydrate);
    }
}
