package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction;

import dk.cloudcreate.essentials.components.common.transaction.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.PersistedEvent;
import org.jdbi.v3.core.Jdbi;
import org.slf4j.*;

import java.util.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * {@link UnitOfWorkFactory} variant where the {@link EventStore} is manually
 * managing the {@link UnitOfWork} and the underlying database Transaction.<br>
 * If you need to have the {@link EventStore} {@link UnitOfWork} join in with an
 * existing <b>Spring</b> Managed transaction then please use the Spring specific {@link UnitOfWorkFactory},
 * <code>SpringManagedUnitOfWorkFactory</code>, provided with the <b>spring-postgresql-event-store</b> module.
 */
public class EventStoreManagedUnitOfWorkFactory extends GenericHandleAwareUnitOfWorkFactory<EventStoreUnitOfWork> implements EventStoreUnitOfWorkFactory {
    private static final Logger log = LoggerFactory.getLogger(EventStoreManagedUnitOfWorkFactory.class);

    private final List<PersistedEventsCommitLifecycleCallback> lifecycleCallbacks;


    public EventStoreManagedUnitOfWorkFactory(Jdbi jdbi) {
        super(jdbi);
        lifecycleCallbacks = new ArrayList<>();
    }

    @Override
    protected EventStoreUnitOfWork createNewUnitOfWorkInstance(GenericHandleAwareUnitOfWorkFactory<EventStoreUnitOfWork> unitOfWorkFactory) {
        return new EventStoreManagedUnitOfWork(unitOfWorkFactory, lifecycleCallbacks);
    }

    @Override
    public EventStoreUnitOfWorkFactory registerPersistedEventsCommitLifeCycleCallback(PersistedEventsCommitLifecycleCallback callback) {
        lifecycleCallbacks.add(requireNonNull(callback, "No callback provided"));
        return this;
    }

    private static class EventStoreManagedUnitOfWork extends GenericHandleAwareUnitOfWork implements EventStoreUnitOfWork {
        private final Logger                                       log = LoggerFactory.getLogger(EventStoreManagedUnitOfWork.class);
        /**
         * The list is maintained by the {@link EventStoreManagedUnitOfWorkFactory} and provided in the constructor
         */
        private final List<PersistedEventsCommitLifecycleCallback> lifecycleCallbacks;
        private final List<PersistedEvent> eventsPersisted;

        public EventStoreManagedUnitOfWork(GenericHandleAwareUnitOfWorkFactory<?> unitOfWorkFactory, List<PersistedEventsCommitLifecycleCallback> lifecycleCallbacks) {
            super(unitOfWorkFactory);
            this.lifecycleCallbacks = requireNonNull(lifecycleCallbacks, "No lifecycleCallbacks provided");
            this.eventsPersisted = new ArrayList<>();
        }

        @Override
        public void registerEventsPersisted(List<PersistedEvent> eventsPersistedInThisUnitOfWork) {
            requireNonNull(eventsPersistedInThisUnitOfWork, "No eventsPersistedInThisUnitOfWork provided");
            this.eventsPersisted.addAll(eventsPersistedInThisUnitOfWork);
        }

        @Override
        protected void beforeCommitting() {
            for (PersistedEventsCommitLifecycleCallback callback : lifecycleCallbacks) {
                try {
                    log.trace("BeforeCommit PersistedEvents for {} with {} persisted events", callback.getClass().getName(), eventsPersisted.size());
                    callback.beforeCommit(this, eventsPersisted);
                } catch (RuntimeException e) {
                    UnitOfWorkException unitOfWorkException = new UnitOfWorkException(msg("{} failed during beforeCommit PersistedEvents", callback.getClass().getName()), e);
                    unitOfWorkException.fillInStackTrace();
                    rollback(unitOfWorkException);
                    throw unitOfWorkException;
                }
            }
        }


        @Override
        protected void afterCommitting() {
            for (PersistedEventsCommitLifecycleCallback callback : lifecycleCallbacks) {
                try {
                    log.trace("AfterCommit PersistedEvents for {} with {} persisted events", callback.getClass().getName(), eventsPersisted.size());
                    callback.afterCommit(this, eventsPersisted);
                } catch (RuntimeException e) {
                    log.error(msg("Failed {} failed during afterCommit PersistedEvents", callback.getClass().getName()), e);
                }
            }
        }

        @Override
        protected void afterRollback(Exception cause) {
            for (PersistedEventsCommitLifecycleCallback callback : lifecycleCallbacks) {
                try {
                    log.trace("AfterCommit PersistedEvents for {} with {} persisted events", callback.getClass().getName(), eventsPersisted.size());
                    callback.afterCommit(this, eventsPersisted);
                } catch (RuntimeException e) {
                    log.error(msg("Failed {} failed during afterCommit PersistedEvents", callback.getClass().getName()), e);
                }
            }
        }
    }
}
