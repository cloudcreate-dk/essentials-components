package dk.cloudcreate.essentials.components.common.transaction;

import org.jdbi.v3.core.Handle;

/**
 * Version of UnitOfWork that's aware of the {@link Handle} associated with the current {@link UnitOfWork}
 */
public interface HandleAwareUnitOfWork extends UnitOfWork {
    /**
     * Get the {@link org.jdbi.v3.core.Jdbi} handle<br>
     * @return the {@link org.jdbi.v3.core.Jdbi} handle
     * @throws UnitOfWorkException If the transaction isn't active
     */
    Handle handle();
}
