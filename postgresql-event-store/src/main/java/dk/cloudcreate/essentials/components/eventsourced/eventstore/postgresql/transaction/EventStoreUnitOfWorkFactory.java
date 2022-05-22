package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction;

import dk.cloudcreate.essentials.components.common.transaction.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.PersistedEvent;

public interface EventStoreUnitOfWorkFactory extends UnitOfWorkFactory<EventStoreUnitOfWork> {
    /**
     * Register a {@link UnitOfWork} callback that will be called with any persisted {@link PersistedEvent}'s during the
     * life cycle of the {@link UnitOfWork}
     *
     * @param callback the callback to register
     * @return the {@link UnitOfWorkFactory} instance this method was called on
     */
    EventStoreUnitOfWorkFactory registerPersistedEventsCommitLifeCycleCallback(PersistedEventsCommitLifecycleCallback callback);
}
