package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.PersistedEvent;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.UnitOfWork;

public interface TransactionalPersistedEventHandler {
    /**
     * This method will be called when ever a {@link PersistedEvent} is published with in a {@link UnitOfWork}
     *
     * @param event      the event published
     * @param unitOfWork the {@link UnitOfWork} associated with the <code>event</code>
     */
    void handle(PersistedEvent event, UnitOfWork unitOfWork);
}
