package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStoreSubscription;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.PersistedEvent;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.GlobalEventOrder;

public interface PersistedEventHandler {
    /**
     * This method will be called if {@link EventStoreSubscription#resetFrom(GlobalEventOrder)} is called
     * @param globalEventOrder the value provided to {@link EventStoreSubscription#resetFrom(GlobalEventOrder)}
     */
    void onResetFrom(GlobalEventOrder globalEventOrder);

    /**
     * This method will be called when ever a {@link PersistedEvent} is published
     * @param event the event published
     */
    void handle(PersistedEvent event);
}