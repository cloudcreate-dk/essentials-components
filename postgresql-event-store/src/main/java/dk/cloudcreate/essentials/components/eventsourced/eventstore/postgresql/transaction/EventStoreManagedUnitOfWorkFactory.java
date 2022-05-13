package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.PersistedEvent;
import dk.cloudcreate.essentials.shared.functional.*;
import org.jdbi.v3.core.*;
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
public class EventStoreManagedUnitOfWorkFactory implements UnitOfWorkFactory {
    private static final Logger log = LoggerFactory.getLogger(EventStoreManagedUnitOfWorkFactory.class);

    private final Jdbi                                         jdbi;
    private final List<PersistedEventsCommitLifecycleCallback> lifecycleCallbacks;

    /**
     * Contains the currently active {@link UnitOfWork}
     */
    private final ThreadLocal<EventStoreManagedUnitOfWork> unitOfWorks = new ThreadLocal();

    public EventStoreManagedUnitOfWorkFactory(Jdbi jdbi) {
        this.jdbi = requireNonNull(jdbi, "No jdbi instance provided");
        lifecycleCallbacks = new ArrayList<>();
    }

    @Override
    public UnitOfWork getRequiredUnitOfWork() {
        var unitOfWork = unitOfWorks.get();
        if (unitOfWork == null) {
            throw new NoActiveUnitOfWorkException();
        }
        return unitOfWork;
    }

    @Override
    public UnitOfWork getOrCreateNewUnitOfWork() {
        var unitOfWork = unitOfWorks.get();
        if (unitOfWork == null) {
            log.debug("Creating EventStore Managed UnitOfWork");
            unitOfWork = new EventStoreManagedUnitOfWork();
            unitOfWork.start();
            unitOfWorks.set(unitOfWork);
        }
        return unitOfWork;
    }

    @Override
    public UnitOfWorkFactory registerPersistedEventsCommitLifeCycleCallback(PersistedEventsCommitLifecycleCallback callback) {
        lifecycleCallbacks.add(requireNonNull(callback, "No callback provided"));
        return this;
    }

    private void removeUnitOfWork() {
        log.debug("Removing EventStore Managed UnitOfWork");
        unitOfWorks.remove();
    }

    @Override
    public Optional<UnitOfWork> getCurrentUnitOfWork() {
        return Optional.ofNullable(unitOfWorks.get());
    }

    private class EventStoreManagedUnitOfWork implements EventStoreUnitOfWork {
        private final Logger log = LoggerFactory.getLogger(EventStoreManagedUnitOfWork.class);

        private Map<UnitOfWorkLifecycleCallback<Object>, List<Object>> unitOfWorkLifecycleCallbackResources;
        private List<PersistedEvent>                                   eventsPersisted;
        private UnitOfWorkStatus                                       status;
        private Exception                                              causeOfRollback;
        private Handle                                                 handle;

        public EventStoreManagedUnitOfWork() {
            status = UnitOfWorkStatus.Ready;
            unitOfWorkLifecycleCallbackResources = new HashMap<>();
            eventsPersisted = new ArrayList<>();
        }

        @Override
        public void start() {
            if (status == UnitOfWorkStatus.Ready || status.isCompleted()) {
                log.debug("Starting EventStoreManaged UnitOfWork with initial status {}", status);
                log.trace("Opening JDBI handle and will begin a DB transaction");
                handle = jdbi.open();
                handle.begin();
                status = UnitOfWorkStatus.Started;
            } else if (status == UnitOfWorkStatus.Started) {
                log.warn("The EventStoreManaged UnitOfWork was already started");
            } else {
                close(handle);
                removeUnitOfWork();
                throw new UnitOfWorkException(msg("Cannot start an EventStoreManaged UnitOfWork as it has status {} and not the expected status {}, {} or {}", status, UnitOfWorkStatus.Started, UnitOfWorkStatus.Committed, UnitOfWorkStatus.RolledBack));
            }
        }

        private void close(Handle handle) {
            if (handle == null) {
                return;
            }
            log.trace("Closing JDBI handle");
            try {
                handle.close();
            } catch (Exception e) {
                log.error("Failed to close JDBI handle", e);
            }
            unitOfWorkLifecycleCallbackResources.clear();
        }


        @Override
        public void commit() {
            if (status == UnitOfWorkStatus.Started) {
                log.trace("Calling UnitOfWorkLifecycleCallbacks#beforeCommit prior to committing the EventStore Managed UnitOfWork");
                unitOfWorkLifecycleCallbackResources.forEach((key, resources) -> {
                    try {
                        log.trace("BeforeCommit: Calling {} with {} associated resource(s)",
                                  key.getClass().getName(),
                                  resources.size());
                        key.beforeCommit(this, resources);
                    } catch (RuntimeException e) {
                        UnitOfWorkException unitOfWorkException = new UnitOfWorkException(msg("{} failed during beforeCommit", key.getClass().getName()), e);
                        unitOfWorkException.fillInStackTrace();
                        rollback(unitOfWorkException);
                        throw unitOfWorkException;
                    }
                });

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

                log.trace("Committing EventStore Managed UnitOfWork");
                try {
                    handle.commit();
                } catch (Exception e) {
                    throw new UnitOfWorkException("Failed to persist Events", e);
                } finally {
                    close(handle);
                    removeUnitOfWork();
                }
                status = UnitOfWorkStatus.Committed;
                unitOfWorkLifecycleCallbackResources.forEach((key, resources) -> {
                    try {
                        log.trace("AfterCommit: Calling {} with {} associated resource(s)",
                                  key.getClass().getName(),
                                  resources.size());
                        key.afterCommit(this, resources);
                    } catch (RuntimeException e) {
                        log.error(msg("Failed {} failed during afterCommit", key.getClass().getName()), e);
                    }
                });

                for (PersistedEventsCommitLifecycleCallback callback : lifecycleCallbacks) {
                    try {
                        log.trace("AfterCommit PersistedEvents for {} with {} persisted events", callback.getClass().getName(), eventsPersisted.size());
                        callback.afterCommit(this, eventsPersisted);
                    } catch (RuntimeException e) {
                        log.error(msg("Failed {} failed during afterCommit PersistedEvents", callback.getClass().getName()), e);
                    }
                }
            } else if (status == UnitOfWorkStatus.MarkedForRollbackOnly) {
                rollback();
            } else {
                close(handle);
                removeUnitOfWork();
                throw new UnitOfWorkException(msg("Cannot commit EventStore Managed UnitOfWork as it has status {} and not the expected status {}", status, UnitOfWorkStatus.Started));
            }

        }

        @Override
        public void rollback(Exception cause) {
            if (status == UnitOfWorkStatus.Started || status == UnitOfWorkStatus.MarkedForRollbackOnly) {
                causeOfRollback = cause != null ? cause : causeOfRollback;
                final String description = msg("Rolling back EventStore Managed UnitOfWork with current status {}{}", status, cause != null ? " due to " + causeOfRollback.getMessage() : "");
                if (log.isTraceEnabled()) {
                    log.trace(description, causeOfRollback);
                } else {
                    log.debug(description);
                }

                unitOfWorkLifecycleCallbackResources.entrySet().forEach(unitOfWorkLifecycleCallbackListEntry -> {
                    try {
                        log.trace("BeforeRollback: Calling {} with {} associated resource(s)",
                                  unitOfWorkLifecycleCallbackListEntry.getKey().getClass().getName(),
                                  unitOfWorkLifecycleCallbackListEntry.getValue().size());
                        unitOfWorkLifecycleCallbackListEntry.getKey().beforeRollback(this, unitOfWorkLifecycleCallbackListEntry.getValue(), cause);
                    } catch (RuntimeException e) {
                        log.error(msg("Failed {} failed during beforeRollback", unitOfWorkLifecycleCallbackListEntry.getKey().getClass().getName()), e);
                    }
                });

                handle.rollback();
                status = UnitOfWorkStatus.RolledBack;
                close(handle);

                for (PersistedEventsCommitLifecycleCallback callback : lifecycleCallbacks) {
                    try {
                        log.trace("After Rollback {} with {} persisted events", callback.getClass().getName(), eventsPersisted.size());
                        callback.afterRollback(this, eventsPersisted);
                    } catch (RuntimeException e) {
                        log.error(msg("Failed {} failed during afterRollback PersistedEvents", callback.getClass().getName()), e);
                    }
                }

                unitOfWorkLifecycleCallbackResources.entrySet().forEach(unitOfWorkLifecycleCallbackListEntry -> {
                    try {
                        log.trace("AfterRollback: Calling {} with {} associated resource(s)",
                                  unitOfWorkLifecycleCallbackListEntry.getKey().getClass().getName(),
                                  unitOfWorkLifecycleCallbackListEntry.getValue().size());
                        unitOfWorkLifecycleCallbackListEntry.getKey().afterRollback(this, unitOfWorkLifecycleCallbackListEntry.getValue(), cause);
                    } catch (RuntimeException e) {
                        log.error(msg("Failed {} failed during afterRollback", unitOfWorkLifecycleCallbackListEntry.getKey().getClass().getName()), e);
                    }
                });
                removeUnitOfWork();
            } else {
                close(handle);
                removeUnitOfWork();
                throw new UnitOfWorkException(msg("Cannot Rollback EventStore Managed UnitOfWork as it has status {} and not the expected status {} or {}", status, UnitOfWorkStatus.Started, UnitOfWorkStatus.MarkedForRollbackOnly), cause);
            }
        }

        @Override
        public UnitOfWorkStatus status() {
            return status;
        }

        @Override
        public Exception getCauseOfRollback() {
            return causeOfRollback;
        }

        @Override
        public void markAsRollbackOnly(Exception cause) {
            if (status == UnitOfWorkStatus.Started) {
                final String description = msg("Marking EventStore Managed UnitOfWork for Rollback Only {}", cause != null ? "due to " + cause.getMessage() : "");
                if (log.isTraceEnabled()) {
                    log.trace(description, cause);
                } else {
                    log.debug(description);
                }

                status = UnitOfWorkStatus.MarkedForRollbackOnly;
                causeOfRollback = cause;
            } else if (status == UnitOfWorkStatus.MarkedForRollbackOnly) {
                log.debug("Cannot Mark current EventStore Managed UnitOfWork for Rollback Only as it has already been marked as such", cause);
            } else {
                close(handle);
                removeUnitOfWork();
                throw new UnitOfWorkException(msg("Cannot Mark EventStore Managed UnitOfWork for Rollback Only it has status {} and not the expected status {}", status, UnitOfWorkStatus.Started), cause);
            }
        }

        @Override
        public <T> T registerLifecycleCallbackForResource(T resource, UnitOfWorkLifecycleCallback<T> associatedUnitOfWorkCallback) {
            requireNonNull(resource, "You must provide a resource");
            requireNonNull(associatedUnitOfWorkCallback, "You must provide a UnitOfWorkLifecycleCallback");
            List<Object> resources = unitOfWorkLifecycleCallbackResources.computeIfAbsent((UnitOfWorkLifecycleCallback<Object>) associatedUnitOfWorkCallback, callback -> new LinkedList<>());
            resources.add(resource);
            return resource;

        }

        @Override
        public void registerEventsPersisted(List<PersistedEvent> eventsPersistedInThisUnitOfWork) {
            requireNonNull(eventsPersistedInThisUnitOfWork, "No eventsPersistedInThisUnitOfWork provided");
            this.eventsPersisted.addAll(eventsPersistedInThisUnitOfWork);
        }

        @Override
        public Handle handle() {
            if (handle == null) {
                throw new UnitOfWorkException("No active transaction");
            }
            return handle;
        }
    }
}
