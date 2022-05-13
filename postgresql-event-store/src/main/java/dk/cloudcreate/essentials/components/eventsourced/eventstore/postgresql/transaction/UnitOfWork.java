package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction;

import org.jdbi.v3.core.Handle;

public interface UnitOfWork {
    /**
     * Start the {@link UnitOfWork} and any underlying transaction
     */
    void start();

    /**
     * Commit the {@link UnitOfWork} and any underlying transaction - see {@link UnitOfWorkStatus#Committed}
     */
    void commit();

    /**
     * Roll back the {@link UnitOfWork} and any underlying transaction - see {@link UnitOfWorkStatus#RolledBack}
     *
     * @param cause the cause of the rollback
     */
    void rollback(Exception cause);

    /**
     * Get the status of the {@link UnitOfWork}
     */
    UnitOfWorkStatus status();

    /**
     * Get the {@link org.jdbi.v3.core.Jdbi} handle<br>
     * @return the {@link org.jdbi.v3.core.Jdbi} handle
     * @throws UnitOfWorkException If the transaction isn't active
     */
    Handle handle();

    /**
     * The cause of a Rollback or a {@link #markAsRollbackOnly(Exception)}
     */
    Exception getCauseOfRollback();

    default void markAsRollbackOnly() {
        markAsRollbackOnly(null);
    }

    void markAsRollbackOnly(Exception cause);

    /**
     * Roll back the {@link UnitOfWork} and any underlying transaction - see {@link UnitOfWorkStatus#RolledBack}
     */
    default void rollback() {
        // Use any exception saved using #markAsRollbackOnly(Exception)
        rollback(getCauseOfRollback());
    }

    /**
     * TODO: Adjust example to the new API
     * Register a resource (e.g. an Aggregate) that should have its {@link UnitOfWorkLifecycleCallback} called during {@link UnitOfWork} operation.<br>
     * Example:
     * <pre>{@code
     * Aggregate aggregate = unitOfWork.registerLifecycleCallbackForResource(aggregate.loadFromEvents(event),
     *                                                                       new AggregateRootRepositoryUnitOfWorkLifecycleCallback()));
     * }</pre>
     * Where the Aggreg
     * <pre>{@code
     * class AggregateRootRepositoryUnitOfWorkLifecycleCallback implements UnitOfWorkLifecycleCallback<AGGREGATE_TYPE> {
     *     @Override
     *     public void beforeCommit(UnitOfWork unitOfWork, java.util.List<AGGREGATE_TYPE> associatedResources) {
     *         log.trace("beforeCommit processing {} '{}' registered with the UnitOfWork being committed", associatedResources.size(), aggregateType.getName());
     *         associatedResources.forEach(aggregate -> {
     *             log.trace("beforeCommit processing '{}' with id '{}'", aggregateType.getName(), aggregate.aggregateId());
     *             List<Object> persistableEvents = aggregate.uncommittedChanges();
     *             if (persistableEvents.isEmpty()) {
     *                 log.trace("No changes detected for '{}' with id '{}'", aggregateType.getName(), aggregate.aggregateId());
     *             } else {
     *                 if (log.isTraceEnabled()) {
     *                     log.trace("Persisting {} event(s) related to '{}' with id '{}': {}", persistableEvents.size(), aggregateType.getName(), aggregate.aggregateId(), persistableEvents.map(persistableEvent -> persistableEvent.event().getClass().getName()).reduce((s, s2) -> s + ", " + s2));
     *                 } else {
     *                     log.debug("Persisting {} event(s) related to '{}' with id '{}'", persistableEvents.size(), aggregateType.getName(), aggregate.aggregateId());
     *                 }
     *                 eventStore.persist(unitOfWork, persistableEvents);
     *                 aggregate.markChangesAsCommitted();
     *             }
     *         });
     *     }
     *
     *     @Override
     *     public void afterCommit(UnitOfWork unitOfWork, java.util.List<AGGREGATE_TYPE> associatedResources) {
     *
     *     }
     *
     *     @Override
     *     public void beforeRollback(UnitOfWork unitOfWork, java.util.List<AGGREGATE_TYPE> associatedResources, Exception causeOfTheRollback) {
     *
     *     }
     *
     *     @Override
     *     public void afterRollback(UnitOfWork unitOfWork, java.util.List<AGGREGATE_TYPE> associatedResources, Exception causeOfTheRollback) {
     *
     *     }
     * }
     * }
     * </pre>
     *
     * @param resource                     the resource that should be tracked
     * @param associatedUnitOfWorkCallback the callback instance for the given resource
     * @param <T>                          the type of resource
     * @return the <code>resource</code> or a proxy to it
     */
    <T> T registerLifecycleCallbackForResource(T resource, UnitOfWorkLifecycleCallback<T> associatedUnitOfWorkCallback);
}
