package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.bus.EventStoreLocalEventBus;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.PersistedEvent;

import java.util.List;

/**
 * Variant of the {@link UnitOfWork} that allows the {@link EventStore}
 * to register any {@link PersistedEvent}'s persisted during a {@link UnitOfWork},
 * such that these events can be published on the {@link EventStoreLocalEventBus}
 */
public interface EventStoreUnitOfWork extends UnitOfWork {
    void registerEventsPersisted(List<PersistedEvent> eventsPersistedInThisUnitOfWork);
}
