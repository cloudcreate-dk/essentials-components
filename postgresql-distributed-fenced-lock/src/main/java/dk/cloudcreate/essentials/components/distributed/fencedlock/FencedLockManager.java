package dk.cloudcreate.essentials.components.distributed.fencedlock;

import dk.cloudcreate.essentials.components.common.Lifecycle;

import java.time.Duration;
import java.util.Optional;

/**
 * Is responsible for obtaining {@link FencedLock}'s, which are named exclusive locks<br>
 * Only one {@link FencedLockManager} instance can acquire a {@link FencedLock} at a time.<br>
 * To coordinate this properly it's important that each {@link FencedLockManager#getLockManagerInstanceId()}
 * is unique across the cluster.<br>
 * <br>
 * This is a variant of the concept described here https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html
 */
public interface FencedLockManager extends Lifecycle {
    /**
     * Lookup a lock
     *
     * @param lockName the name of the lock
     * @return an {@link Optional} with the locks current state or {@link Optional#empty()} if the lock doesn't exist
     */
    Optional<FencedLock> lookupLock(LockName lockName);

    /**
     * Try to acquire the lock using this Lock Manager instance
     *
     * @param lockName the name of the lock
     * @return An Optional with the {@link FencedLock} IF it was acquired by the {@link FencedLockManager} on this JVM Node, otherwise it return an empty Optional
     */
    Optional<FencedLock> tryAcquireLock(LockName lockName);

    /**
     * Try to acquire the lock on this JVM Node
     *
     * @param lockName the name of the lock
     * @param timeout  timeout for the lock acquiring
     * @return An Optional with the {@link FencedLock} IF it was acquired by the {@link FencedLockManager} on this JVM Node, otherwise it return an empty Optional
     */
    Optional<FencedLock> tryAcquireLock(LockName lockName, Duration timeout);

    /**
     * Acquire the lock on this JVM Node. If the lock already is acquired by another JVM Node,
     * then this method will wait until the lock can be acquired by this JVM node
     *
     * @param lockName the name of the lock
     * @return The {@link FencedLock} when can be acquired by this JVM Node
     */
    FencedLock acquireLock(LockName lockName);

    /**
     * Is the lock acquired
     * @param lockName the name of the lock
     * @return true if the lock is acquired, otherwise false
     */
    boolean isLockAcquired(LockName lockName);

    /**
     * Is the lock already acquired by this JVM node
     *
     * @param lockName the lock name
     * @return true of the lock is acquired by this JVM node
     */
    boolean isLockedByThisLockManagerInstance(LockName lockName);

    /**
     * Is the lock already acquired by another JVM node
     *
     * @param lockName the name of the lock
     * @return true of the lock is acquired by this JVM node
     */
    boolean isLockAcquiredByAnotherLockManagerInstance(LockName lockName);

    /**
     * Asynchronously try to acquire a lock by the given name and call the {@link LockCallback#lockAcquired(FencedLock)}
     * when the lock is acquired<br>
     * To stop the background acquiring process, you need to call {@link #cancelAsyncLockAcquiring(LockName)} with the same
     * lockName
     *
     * @param lockName     the name of the lock
     * @param lockCallback the callback that's notified when a lock is acquired
     */
    void acquireLockAsync(LockName lockName, LockCallback lockCallback);

    /**
     * Cancel a previously started asynchronous lock acquiring background process<br>
     * IF this JVM node had acquired a {@link FencedLock} then this lock
     * will be released AND the {@link LockCallback#lockReleased(FencedLock)} will be called on the {@link LockCallback} instance
     * that was supplied to the {@link #acquireLockAsync(LockName, LockCallback)}<br>
     * Otherwise only the background lock acquiring process will be stopped.
     *
     * @param lockName the name of the lock
     */
    void cancelAsyncLockAcquiring(LockName lockName);

    /**
     * Get the instance id that distinguishes different {@link FencedLockManager} instances from each other<br>
     * For local JVM testing you can assign a unique instance id to allow multiple {@link FencedLockManager}'s to compete for Locks.<br>
     * In production the hostname should be returned
     *
     * @return the instance id
     */
    String getLockManagerInstanceId();
}
