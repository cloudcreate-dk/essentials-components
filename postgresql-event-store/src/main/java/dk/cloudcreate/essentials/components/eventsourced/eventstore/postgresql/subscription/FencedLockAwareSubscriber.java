package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription;

import dk.cloudcreate.essentials.components.common.types.SubscriberId;
import dk.cloudcreate.essentials.components.distributed.fencedlock.FencedLock;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.GlobalEventOrder;

import java.util.Optional;

/**
 * Must be supplied when creating an exclusive subscription using
 * {@link EventStoreSubscriptionManager#exclusivelySubscribeToAggregateEventsAsynchronously(SubscriberId, AggregateType, GlobalEventOrder, Optional, FencedLockAwareSubscriber, PersistedEventHandler)}
 */
public interface FencedLockAwareSubscriber {
    void onLockAcquired(FencedLock fencedLock, SubscriptionResumePoint resumeFromAndIncluding);
    void onLockReleased(FencedLock fencedLock);
}
