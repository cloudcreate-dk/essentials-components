package dk.cloudcreate.essentials.components.distributed.fencedlock;

public interface LockCallback {
    /**
     * This method is called when the lock in question was owned by the {@link FencedLockManager}
     * instance belonging to this JVM instance and the lock has been released<br>
     *
     * @param lock the lock that was released
     */
    void lockReleased(FencedLock lock);

    /**
     * This method is called when the lock in question is acquired by the {@link FencedLockManager}
     * on this JVM instance
     *
     * @param lock the lock that was acquired
     */
    void lockAcquired(FencedLock lock);
}

