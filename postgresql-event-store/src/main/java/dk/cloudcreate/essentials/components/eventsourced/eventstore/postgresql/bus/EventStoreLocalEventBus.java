package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.bus;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.PersistedEvent;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.*;
import dk.cloudcreate.essentials.reactive.LocalEventBus;
import org.slf4j.*;

import java.util.List;
import java.util.function.Consumer;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

public class EventStoreLocalEventBus {
    private static final Logger log = LoggerFactory.getLogger("EventStoreLocalEventBus");

    private LocalEventBus<PersistedEvents> localEventBus = new LocalEventBus<PersistedEvents>("EventStoreLocalBus",
                                                                                              3,
                                                                                              this::onErrorHandler);

    public EventStoreLocalEventBus(UnitOfWorkFactory unitOfWorkFactory) {
        requireNonNull(unitOfWorkFactory, "No unitOfWorkFactory was supplied");
        unitOfWorkFactory.registerPersistedEventsCommitLifeCycleCallback(new PersistedEventsCommitLifecycleCallback() {
            @Override
            public void beforeCommit(UnitOfWork unitOfWork, List<PersistedEvent> persistedEvents) {
                localEventBus.publish(new PersistedEvents(CommitStage.BeforeCommit, unitOfWork, persistedEvents));
            }

            @Override
            public void afterCommit(UnitOfWork unitOfWork, List<PersistedEvent> persistedEvents) {
                localEventBus.publish(new PersistedEvents(CommitStage.AfterCommit, unitOfWork, persistedEvents));
            }

            @Override
            public void afterRollback(UnitOfWork unitOfWork, List<PersistedEvent> persistedEvents) {
                localEventBus.publish(new PersistedEvents(CommitStage.AfterRollback, unitOfWork, persistedEvents));
            }
        });
    }

    public LocalEventBus<PersistedEvents> localEventBus() {
        return localEventBus;
    }

    private void onErrorHandler(Consumer<PersistedEvents> persistedEventsConsumer, PersistedEvents persistedEvents, Exception e) {
        log.error(msg("Failed to publish PersistedEvents to consumer {}", persistedEventsConsumer.getClass().getName()), e);
    }

    public LocalEventBus<PersistedEvents> addAsyncSubscriber(Consumer<PersistedEvents> subscriber) {
        return localEventBus.addAsyncSubscriber(subscriber);
    }

    public LocalEventBus<PersistedEvents> removeAsyncSubscriber(Consumer<PersistedEvents> subscriber) {
        return localEventBus.removeAsyncSubscriber(subscriber);
    }

    public LocalEventBus<PersistedEvents> addSyncSubscriber(Consumer<PersistedEvents> subscriber) {
        return localEventBus.addSyncSubscriber(subscriber);
    }

    public LocalEventBus<PersistedEvents> removeSyncSubscriber(Consumer<PersistedEvents> subscriber) {
        return localEventBus.removeSyncSubscriber(subscriber);
    }
}
