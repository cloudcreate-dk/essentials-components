package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.PersistedEvent;

import java.util.List;

/**
 * Callback that can be registered with the {@link UnitOfWorkFactory}.<br/>
 * This life cycle will be called when ever any {@link UnitOfWork} is committed with any persisted {@link PersistedEvent}'s
 *
 * @see UnitOfWorkLifecycleCallback
 */
public interface PersistedEventsCommitLifecycleCallback {
    /**
     * Before the {@link UnitOfWork} is committed.<br/>
     * This method is called AFTER {@link UnitOfWorkLifecycleCallback#beforeCommit(UnitOfWork, java.util.List)}!
     *
     * @param unitOfWork      the unit of work
     * @param persistedEvents ALL the {@link PersistedEvent}'s that were associated with the {@link UnitOfWork}
     */
    void beforeCommit(UnitOfWork unitOfWork, List<PersistedEvent> persistedEvents);

    /**
     * After the {@link UnitOfWork} was committed.<br/>
     * This method is called AFTER {@link UnitOfWorkLifecycleCallback#afterCommit(UnitOfWork, java.util.List)}
     *
     * @param unitOfWork      the unit of work
     * @param persistedEvents ALL the {@link PersistedEvent}'s that were associated with the {@link UnitOfWork}
     */
    void afterCommit(UnitOfWork unitOfWork, List<PersistedEvent> persistedEvents);

    void afterRollback(UnitOfWork unitOfWork, List<PersistedEvent> persistedEvents);
}
