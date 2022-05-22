package dk.cloudcreate.essentials.components.common.transaction;

import dk.cloudcreate.essentials.shared.functional.*;

import java.util.Optional;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * This interface creates a {@link UnitOfWork}
 * @param <UOW> the {@link UnitOfWork} sub-type returned by the {@link UnitOfWorkFactory}
 */
public interface UnitOfWorkFactory<UOW extends UnitOfWork> {
    /**
     * Get a required active {@link UnitOfWork}
     *
     * @return the active {@link UnitOfWork}
     * @throws NoActiveUnitOfWorkException if the is no active {@link UnitOfWork}
     */
    UOW getRequiredUnitOfWork();

    /**
     * Get the current {@link UnitOfWork} or create a new {@link UnitOfWork}
     * if one is missing
     *
     * @return a {@link UnitOfWork}
     */
    UOW getOrCreateNewUnitOfWork();


    default void usingUnitOfWork(CheckedConsumer<UOW> unitOfWorkConsumer) {
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

    default <R> R withUnitOfWork(CheckedFunction<UOW, R> unitOfWorkFunction) {
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

    Optional<UOW> getCurrentUnitOfWork();
}
