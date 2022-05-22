package dk.cloudcreate.essentials.components.common.transaction;

import dk.cloudcreate.essentials.shared.functional.*;
import org.slf4j.*;

import java.util.Optional;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * This interface creates a {@link UnitOfWork}
 * @param <UOW> the {@link UnitOfWork} sub-type returned by the {@link UnitOfWorkFactory}
 */
public interface UnitOfWorkFactory<UOW extends UnitOfWork> {
    Logger unitOfWorkLog = LoggerFactory.getLogger(UnitOfWorkFactory.class);

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

        var existingUnitOfWork = getCurrentUnitOfWork();
        var unitOfWork = existingUnitOfWork.orElseGet(() -> {
            unitOfWorkLog.debug("Creating a new UnitOfWork for this usingUnitOfWork(CheckedConsumer) method call as there wasn't an existing UnitOfWork");
            return getOrCreateNewUnitOfWork();
        });
        existingUnitOfWork.ifPresent(uow -> unitOfWorkLog.debug("NestedUnitOfWork: Reusing existing UnitOfWork for this usingUnitOfWork(CheckedConsumer) method call"));
        try {
            unitOfWorkConsumer.accept(unitOfWork);
            if (existingUnitOfWork.isEmpty()) {
                unitOfWorkLog.debug("Committing the UnitOfWork created by this usingUnitOfWork(CheckedConsumer) method call");
                unitOfWork.commit();
            } else {
                unitOfWorkLog.debug("NestedUnitOfWork: Won't commit the UnitOfWork as it wasn't created by this usingUnitOfWork(CheckedConsumer) method call");
            }
        } catch (Exception e) {
            if (existingUnitOfWork.isEmpty()) {
                unitOfWorkLog.debug("Rolling back the UnitOfWork created by this usingUnitOfWork(CheckedConsumer) method call");
                unitOfWork.rollback(e);
            } else {
                unitOfWorkLog.debug("NestedUnitOfWork: Marking UnitOfWork as rollback only as it wasn't created by this usingUnitOfWork(CheckedConsumer) method call");
                unitOfWork.markAsRollbackOnly(e);
            }
            throw new UnitOfWorkException(e);
        }
    }

    default <R> R withUnitOfWork(CheckedFunction<UOW, R> unitOfWorkFunction) {
        requireNonNull(unitOfWorkFunction, "No unitOfWorkFunction provided");
        var existingUnitOfWork = getCurrentUnitOfWork();
        var unitOfWork = existingUnitOfWork.orElseGet(() -> {
            unitOfWorkLog.debug("Creating a new UnitOfWork for this withUnitOfWork(CheckedFunction) method call as there wasn't an existing UnitOfWork");
            return getOrCreateNewUnitOfWork();
        });
        existingUnitOfWork.ifPresent(uow -> unitOfWorkLog.debug("NestedUnitOfWork: Reusing existing UnitOfWork for this withUnitOfWork(CheckedFunction) method call"));
        try {
            var result = unitOfWorkFunction.apply(unitOfWork);
            if (existingUnitOfWork.isEmpty()) {
                unitOfWorkLog.debug("Committing the UnitOfWork created by this withUnitOfWork(CheckedFunction) method call");
                unitOfWork.commit();
            } else {
                unitOfWorkLog.debug("NestedUnitOfWork: Won't commit the UnitOfWork as it wasn't created by this withUnitOfWork(CheckedFunction) method call");
            }

            return result;
        } catch (Exception e) {
            if (existingUnitOfWork.isEmpty()) {
                unitOfWorkLog.debug("Rolling back the UnitOfWork created by this withUnitOfWork(CheckedFunction) method call");
                unitOfWork.rollback(e);
            } else {
                unitOfWorkLog.debug("NestedUnitOfWork: Marking UnitOfWork as rollback only as it wasn't created by this withUnitOfWork(CheckedFunction) method call");
                unitOfWork.markAsRollbackOnly(e);
            }
            throw new UnitOfWorkException(e);
        }
    }

    Optional<UOW> getCurrentUnitOfWork();
}
