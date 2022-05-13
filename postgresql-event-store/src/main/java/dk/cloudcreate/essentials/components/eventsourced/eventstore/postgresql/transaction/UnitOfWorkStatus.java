package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction;

/**
 * The status of a {@link UnitOfWork}
 */
public enum UnitOfWorkStatus {
    /**
     * The {@link UnitOfWork} has just been created, but not yet {@link #Started}
     */
    Ready(false),
    /**
     * The {@link UnitOfWork} has been started (i.e. any underlying transaction has begun)
     */
    Started(false),
    /**
     * The {@link UnitOfWork} has been committed<br>
     */
    Committed(true),
    /**
     * The {@link UnitOfWork} has been rolled back<br>
     */
    RolledBack(true),
    /**
     * The {@link UnitOfWork} marked as it MUST be rolled back at the end of the transaction
     */
    MarkedForRollbackOnly(false);

    public final boolean isCompleted;

    UnitOfWorkStatus(boolean isCompleted) {
        this.isCompleted = isCompleted;
    }

    public boolean isCompleted() {
        return isCompleted;
    }
}
