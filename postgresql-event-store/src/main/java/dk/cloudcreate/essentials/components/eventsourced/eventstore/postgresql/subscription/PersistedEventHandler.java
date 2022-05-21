package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription;

import dk.cloudcreate.essentials.components.common.types.SubscriberId;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStoreSubscription;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.GlobalEventOrder;

import java.util.Optional;

/**
 * {@link PersistedEvent} Event handler interface for use with the {@link EventStoreSubscriptionManager}'s:
 * <ul>
 *     <li>{@link EventStoreSubscriptionManager#exclusivelySubscribeToAggregateEventsAsynchronously(SubscriberId, AggregateType, GlobalEventOrder, Optional, PersistedEventHandler)}</li>
 *     <li>{@link EventStoreSubscriptionManager#subscribeToAggregateEventsAsynchronously(SubscriberId, AggregateType, GlobalEventOrder, Optional, PersistedEventHandler)}</li>
 * </ul>
 *
 * @see PatternMatchingPersistedEventHandler
 */
public interface PersistedEventHandler {
    /**
     * This method will be called if {@link EventStoreSubscription#resetFrom(GlobalEventOrder)} is called
     *
     * @param globalEventOrder the value provided to {@link EventStoreSubscription#resetFrom(GlobalEventOrder)}
     */
    void onResetFrom(GlobalEventOrder globalEventOrder);

    /**
     * This method will be called when ever a {@link PersistedEvent} is published
     *
     * @param event the event published
     */
    void handle(PersistedEvent event);
}