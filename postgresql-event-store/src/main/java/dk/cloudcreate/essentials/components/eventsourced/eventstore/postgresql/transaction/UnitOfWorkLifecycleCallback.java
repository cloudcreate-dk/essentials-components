package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction;

import java.util.List;

/**
 * Callback that can be registered with a {@link UnitOfWork} in relation to
 * one of more Resources (can e.g. be an Aggregate). When the {@link UnitOfWork} is committed
 * or rolledback the {@link UnitOfWorkLifecycleCallback} will be called with all the Resources that have been associated with it through
 * the {@link UnitOfWork}
 */
public interface UnitOfWorkLifecycleCallback<RESOURCE_TYPE> {
    void beforeCommit(UnitOfWork unitOfWork, List<RESOURCE_TYPE> associatedResources);

    void afterCommit(UnitOfWork unitOfWork, List<RESOURCE_TYPE> associatedResources);

    void beforeRollback(UnitOfWork unitOfWork, List<RESOURCE_TYPE> associatedResources, Exception causeOfTheRollback);

    void afterRollback(UnitOfWork unitOfWork, List<RESOURCE_TYPE> associatedResources, Exception causeOfTheRollback);
}
