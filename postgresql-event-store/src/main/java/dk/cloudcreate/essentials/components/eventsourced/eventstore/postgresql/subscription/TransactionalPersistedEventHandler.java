package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription;

import dk.cloudcreate.essentials.components.common.transaction.UnitOfWork;
import dk.cloudcreate.essentials.components.common.types.SubscriberId;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;

import java.util.Optional;

/**
 * {@link PersistedEvent} Event handler interface for use with the {@link EventStoreSubscriptionManager}'s:
 * <ul>
 *     <li>{@link EventStoreSubscriptionManager#subscribeToAggregateEventsInTransaction(SubscriberId, AggregateType, Optional, TransactionalPersistedEventHandler)}</li>
 * </ul>
 *
 * @see PatternMatchingTransactionalPersistedEventHandler
 */
public interface TransactionalPersistedEventHandler {
    /**
     * This method will be called when ever a {@link PersistedEvent} is published with in a {@link UnitOfWork}
     *
     * @param event      the event published
     * @param unitOfWork the {@link UnitOfWork} associated with the <code>event</code>
     */
    void handle(PersistedEvent event, UnitOfWork unitOfWork);
}
