package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql;

import dk.cloudcreate.essentials.components.common.Lifecycle;
import dk.cloudcreate.essentials.components.common.types.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.GlobalEventOrder;

import java.util.Optional;

public interface EventStoreSubscription extends Lifecycle {
    /**
     * the unique id for the subscriber
     *
     * @return the unique id for the subscriber
     */
    SubscriberId subscriberId();

    /**
     * the type of aggregate that we're subscribing for {@link PersistedEvent}'s related to
     *
     * @return the type of aggregate that we're subscribing for {@link PersistedEvent}'s related to
     */
    AggregateType aggregateType();

    void unsubscribe();

    /**
     * Reset the subscription point.<br>
     *
     * @param subscribeFromAndIncludingGlobalOrder this {@link GlobalEventOrder} will become the starting point in the
     *                                             EventStream associated with the <code>aggregateType</code>
     */
    void resetFrom(GlobalEventOrder subscribeFromAndIncludingGlobalOrder);

    /**
     * Get the subscriptions resume point (if supported by the subscription)
     *
     * @return the subscriptions resume point
     */
    Optional<SubscriptionResumePoint> currentResumePoint();

    /**
     * If {@link Optional#isPresent()} then only include events that belong to the specified {@link Tenant}, otherwise all Events matching the criteria are returned
     */
    Optional<Tenant> onlyIncludeEventsForTenant();

    /**
     */
    boolean isActive();
}
