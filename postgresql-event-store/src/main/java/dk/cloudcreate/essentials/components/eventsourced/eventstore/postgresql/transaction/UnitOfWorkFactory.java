package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.PersistedEvent;
import dk.cloudcreate.essentials.shared.functional.*;

import java.util.Optional;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * This interface creates a {@link UnitOfWork}
 */
public interface UnitOfWorkFactory {
    /**
     * Get a required active {@link UnitOfWork}
     *
     * @return the active {@link UnitOfWork}
     * @throws NoActiveUnitOfWorkException if the is no active {@link UnitOfWork}
     */
    UnitOfWork getRequiredUnitOfWork();

    /**
     * Get the current {@link UnitOfWork} or create a new {@link UnitOfWork}
     * if one is missing
     *
     * @return a {@link UnitOfWork}
     */
    UnitOfWork getOrCreateNewUnitOfWork();

    /**
     * Register a {@link UnitOfWork} callback that will be called with any persisted {@link PersistedEvent}'s during the
     * life cycle of the {@link UnitOfWork}
     *
     * @param callback the callback to register
     * @return the {@link UnitOfWorkFactory} instance this method was called on
     */
    UnitOfWorkFactory registerPersistedEventsCommitLifeCycleCallback(PersistedEventsCommitLifecycleCallback callback);

    default void usingUnitOfWork(CheckedConsumer<UnitOfWork> unitOfWorkConsumer) {
        requireNonNull(unitOfWorkConsumer, "No unitOfWorkConsumer provided");
        var unitOfWork = getOrCreateNewUnitOfWork();
        try {
            unitOfWorkConsumer.accept(unitOfWork);
            unitOfWork.commit();
        } catch (Exception e) {
            unitOfWork.rollback(e);
            throw new UnitOfWorkException(e);
        }
    }

    default <R> R withUnitOfWork(CheckedFunction<UnitOfWork, R> unitOfWorkFunction) {
        requireNonNull(unitOfWorkFunction, "No unitOfWorkFunction provided");
        var unitOfWork = getOrCreateNewUnitOfWork();
        try {
            var result = unitOfWorkFunction.apply(unitOfWork);
            unitOfWork.commit();
            return result;
        } catch (Exception e) {
            unitOfWork.rollback(e);
            throw new UnitOfWorkException(e);
        }
    }

    Optional<UnitOfWork> getCurrentUnitOfWork();
}
