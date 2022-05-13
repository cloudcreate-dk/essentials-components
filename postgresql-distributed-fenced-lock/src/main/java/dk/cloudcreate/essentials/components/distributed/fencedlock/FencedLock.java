package dk.cloudcreate.essentials.components.distributed.fencedlock;

import java.time.OffsetDateTime;

/**
 * Represents a named fenced lock, where the {@link #getCurrentToken()} can be passed on to down stream logic, which can keep track of the token value to identify if a timed out lock is being used to request logic.<br>
 * The fence locking concept is described here https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html
 */
public interface FencedLock {
    /**
     * The name of the Lock
     *
     * @return the lock name
     */
    LockName getName();

    /**
     * The current token value as of the {@link #getLockLastConfirmedTimestamp()} for this Lock across all {@link FencedLockManager} instances<br>
     * Every time a lock is acquired or confirmed a new token is issued (i.e. it's ever-growing value)
     */
    long getCurrentToken();

    /**
     * Which JVM/{@link FencedLockManager#getLockManagerInstanceId()} that has acquired this lock
     */
    String getLockedByLockManagerInstanceId();

    /**
     * At what time did the JVM/{@link FencedLockManager#getLockManagerInstanceId()} that currently has acquired the lock acquire it (at first acquiring the {@link #getLockLastConfirmedTimestamp()} is set to {@link #getLockAcquiredTimestamp()})
     */
    OffsetDateTime getLockAcquiredTimestamp();

    /**
     * At what time did the JVM/{@link FencedLockManager#getLockManagerInstanceId()}, that currently has acquired the lock, last confirm that it still has access to the lock
     */
    OffsetDateTime getLockLastConfirmedTimestamp();

    /**
     * Is this lock locked?
     */
    boolean isLocked();

    /**
     * Is this Lock locked by this JVM node<br>
     */
    boolean isLockedByThisLockManagerInstance();

    /**
     * Release/Unlock the lock. Only works if {@link #isLockedByThisLockManagerInstance()} is true
     */
    void release();

    void registerCallback(LockCallback lockCallback);
}
