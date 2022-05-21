package dk.cloudcreate.essentials.components.distributed.fencedlock.postgresql;

import dk.cloudcreate.essentials.components.distributed.fencedlock.*;
import dk.cloudcreate.essentials.shared.FailFast;
import dk.cloudcreate.essentials.shared.concurrent.ThreadFactoryBuilder;
import dk.cloudcreate.essentials.shared.network.Network;
import org.jdbi.v3.core.*;
import org.slf4j.*;
import reactor.core.publisher.Mono;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * Postgresql version of the {@link FencedLockManager} interface
 */
public class PostgresqlFencedLockManager implements FencedLockManager {
    private static final Logger log                      = LoggerFactory.getLogger(PostgresqlFencedLockManager.class);
    public static final  long   FIRST_TOKEN              = 1L;
    public static final  long   UNINITIALIZED_LOCK_TOKEN = -1L;

    /**
     * Entries only exist if this lock manager instance believes that it has acquired the given lock<br>
     * Key: Lock name<br>
     * Value: The acquired {@link FencedLock}
     */
    private final ConcurrentMap<LockName, PostgresqlFencedLock> locksAcquiredByThisLockManager;
    /**
     * Entries only exist if acquiring the lock is being performed asynchronously
     * Key: lock name<br>
     * Value: the {@link ScheduledFuture} returned from {@link ScheduledExecutorService#scheduleAtFixedRate(Runnable, long, long, TimeUnit)}
     */
    private final ConcurrentMap<LockName, ScheduledFuture<?>>   asyncLockAcquirings;
    private final String                                        fencedLocksTableName;
    private final Duration                                      lockTimeOut;
    private final Duration                                      lockConfirmationInterval;
    private final Jdbi                                          jdbi;
    private final String                                        lockManagerInstanceId;

    private volatile boolean started;
    private volatile boolean stopping;
    /**
     * Paused is used for testing purposes to pause async lock acquiring and confirmation
     */
    private volatile boolean paused;

    private ScheduledExecutorService lockConfirmationExecutor;
    private ScheduledExecutorService asyncLockAcquiringExecutor;

    /**
     * Create a locking manager using default values for lockManagerInstanceId and fencedLocksTableName
     * @param jdbi
     * @param lockTimeOut              the period between {@link FencedLock#getLockLastConfirmedTimestamp()} and the current time before the lock is marked as timed out
     * @param lockConfirmationInterval how often should the locks be confirmed. MUST is less than the <code>lockTimeOut</code>
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public PostgresqlFencedLockManager(Jdbi jdbi,
                                       Duration lockTimeOut,
                                       Duration lockConfirmationInterval) {
        this(jdbi,
             Optional.empty(),
             Optional.empty(),
             lockTimeOut,
             lockConfirmationInterval);
    }

    /**
     * @param jdbi
     * @param lockManagerInstanceId    The unique name for this lock manager instance. If left {@link Optional#empty()} then the machines hostname is used
     * @param fencedLocksTableName     the name of the table where the fenced locks will be maintained
     * @param lockTimeOut              the period between {@link FencedLock#getLockLastConfirmedTimestamp()} and the current time before the lock is marked as timed out
     * @param lockConfirmationInterval how often should the locks be confirmed. MUST is less than the <code>lockTimeOut</code>
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public PostgresqlFencedLockManager(Jdbi jdbi,
                                       Optional<String> lockManagerInstanceId,
                                       Optional<String> fencedLocksTableName,
                                       Duration lockTimeOut,
                                       Duration lockConfirmationInterval) {
        requireNonNull(lockManagerInstanceId, "No lockManagerInstanceId option provided");
        requireNonNull(fencedLocksTableName, "No fencedLocksTableName option provided");

        this.jdbi = requireNonNull(jdbi, "You must supply a jdbi instance");
        this.lockManagerInstanceId = lockManagerInstanceId.orElseGet(Network::hostName);
        this.fencedLocksTableName = fencedLocksTableName.orElse("fenced_locks");
        this.lockTimeOut = requireNonNull(lockTimeOut, "No lockTimeOut value provided");
        this.lockConfirmationInterval = requireNonNull(lockConfirmationInterval, "No lockConfirmationInterval value provided");
        if (lockConfirmationInterval.compareTo(lockTimeOut) >= 1) {
            throw new IllegalArgumentException(msg("lockConfirmationInterval {} duration MUST not be larger than the lockTimeOut {} duration, because locks will then always timeout", lockConfirmationInterval, lockTimeOut));
        }

        locksAcquiredByThisLockManager = new ConcurrentHashMap<>();
        asyncLockAcquirings = new ConcurrentHashMap<>();

        jdbi.registerArgument(new LockNameArgumentFactory());
        initializeLockTable();
    }

    protected void initializeLockTable() {
        jdbi.useTransaction(handle -> {
            var rowsUpdated = handle.execute("CREATE TABLE IF NOT EXISTS " + fencedLocksTableName + " (\n" +
                                                     "lock_name TEXT NOT NULL,\n" +      // The name of the lock
                                                     "last_issued_fence_token bigint,\n" +    // The token issued at lock_last_confirmed_ts. Every time a lock is acquired or confirmed a new token is issued (ever growing value)
                                                     "locked_by_lockmanager_instance_id TEXT,\n" + // which JVM/Bus instance acquired the lock
                                                     "lock_acquired_ts TIMESTAMP WITH TIME ZONE,\n" + // at what time did the JVM/Bus instance acquire the lock (at first acquiring the lock_last_confirmed_ts is set to lock_acquired_ts)
                                                     "lock_last_confirmed_ts TIMESTAMP WITH TIME ZONE,\n" + // when did the JVM/Bus instance that acquired the lock last confirm that it still has access to the lock
                                                     "PRIMARY KEY (lock_name)\n" +
                                                     ")");
            if (rowsUpdated == 1) {
                log.info("[{}] Created the '{}' fenced locks table", lockManagerInstanceId, fencedLocksTableName);
            }

            // -------------------------------------------------------------------------------
            var indexName = fencedLocksTableName + "_current_token_index";
            rowsUpdated = handle.execute("CREATE INDEX IF NOT EXISTS " + indexName + " ON " + fencedLocksTableName + " (lock_name, last_issued_fence_token)");
            if (rowsUpdated == 1) {
                log.debug("[{}] Created the '{}' index on fenced locks table '{}'", lockManagerInstanceId, indexName, fencedLocksTableName);
            }
        });
    }

    @Override
    public void start() {
        if (!started) {
            log.info("[{}] Starting lock manager", lockManagerInstanceId);
            stopping = false;
            lockConfirmationExecutor = Executors.newScheduledThreadPool(1,
                                                                        new ThreadFactoryBuilder()
                                                                                .nameFormat(lockManagerInstanceId + "-FencedLock-Confirmation-%d")
                                                                                .daemon(true)
                                                                                .build());

            asyncLockAcquiringExecutor = Executors.newScheduledThreadPool(2,
                                                                          ThreadFactoryBuilder.builder()
                                                                                              .nameFormat(lockManagerInstanceId + "-Lock-Acquiring-%d")
                                                                                              .daemon(true)
                                                                                              .build());

            lockConfirmationExecutor.scheduleAtFixedRate(this::confirmAllLocallyAcquiredLocks,
                                                         lockConfirmationInterval.toMillis(),
                                                         lockConfirmationInterval.toMillis(),
                                                         TimeUnit.MILLISECONDS);


            started = true;
            log.info("[{}] Started lock manager", lockManagerInstanceId);
        } else {
            log.debug("[{}] Lock Manager was already started", lockManagerInstanceId);
        }
    }

    void pause() {
        log.info("[{}] Pausing async lock acquiring and lock confirmation", lockManagerInstanceId);
        paused = true;
    }

    void resume() {
        log.info("[{}] Resuming async lock acquiring and lock confirmation", lockManagerInstanceId);
        paused = false;
    }

    private void confirmAllLocallyAcquiredLocks() {
        if (stopping) {
            log.debug("[{}] Shutting down, skipping confirmAllLocallyAcquiredLocks", lockManagerInstanceId);
            return;
        }
        if (locksAcquiredByThisLockManager.size() == 0) {
            log.debug("[{}] No locks to confirm for this Lock Manager instance", lockManagerInstanceId);
            return;
        }

        if (paused) {
            log.info("[{}] Lock Manager is paused, skipping confirmAllLocallyAcquiredLocks", lockManagerInstanceId);
            return;
        }

        if (log.isTraceEnabled()) {
            log.trace("[{}] Confirming {} locks acquired by this Lock Manager Instance: {}", lockManagerInstanceId, locksAcquiredByThisLockManager.size(), locksAcquiredByThisLockManager.keySet());
        } else {
            log.debug("[{}] Confirming {} locks acquired by this Lock Manager Instance", lockManagerInstanceId, locksAcquiredByThisLockManager.size());
        }
        var confirmedTimestamp = OffsetDateTime.now(Clock.systemUTC());
        jdbi.useTransaction(handle -> {
            locksAcquiredByThisLockManager.forEach((lockName, lockCallback) -> {
                long nextToken = lockCallback.getCurrentToken() + 1L;
                var rowsUpdated = handle.createUpdate("UPDATE " + fencedLocksTableName + " SET " +
                                                              "last_issued_fence_token=:last_issued_fence_token, lock_last_confirmed_ts=:lock_last_confirmed_ts\n" +
                                                              "WHERE lock_name=:lock_name AND last_issued_fence_token=:previous_last_issued_fence_token AND locked_by_lockmanager_instance_id=:locked_by_lockmanager_instance_id")
                                        .bind("lock_name", lockName)
                                        .bind("last_issued_fence_token", nextToken)
                                        .bind("locked_by_lockmanager_instance_id", requireNonNull(lockCallback.getLockedByLockManagerInstanceId(), msg("[{}] getLockedByLockManagerInstanceId was NULL. Details: {}", lockManagerInstanceId, lockCallback)))
                                        .bind("lock_last_confirmed_ts", confirmedTimestamp)
                                        .bind("previous_last_issued_fence_token", lockCallback.getCurrentToken())
                                        .execute();
                if (rowsUpdated == 0) {
                    // We failed to confirm this lock, someone must have taken over the lock in the meantime
                    log.info("[{}] Failed to confirm lock '{}', someone has taken over the lock: {}", lockManagerInstanceId, lockCallback.getName(), lockCallback);
                    lockCallback.release();
                } else {
                    lockCallback.markAsConfirmed(nextToken, confirmedTimestamp);
                    log.debug("[{}] Confirmed lock '{}': {}", lockManagerInstanceId, lockCallback.getName(), lockCallback);
                }
            });
        });
        if (log.isTraceEnabled()) {
            log.trace("[{}] Completed confirmation of locks acquired by this Lock Manager Instance. Number of Locks acquired locally after confirmation {}: {}", lockManagerInstanceId, locksAcquiredByThisLockManager.size(), locksAcquiredByThisLockManager.keySet());
        } else {
            log.debug("[{}] Completed confirmation of locks acquired by this Lock Manager Instance. Number of Locks acquired locally after confirmation {}", lockManagerInstanceId, locksAcquiredByThisLockManager.size());
        }


    }

    private void releaseLock(PostgresqlFencedLock lock) {
        if (!lock.isLockedByThisLockManagerInstance()) {
            throw new IllegalArgumentException(msg("[{}] Cannot release Lock '{}' since it isn't locked by the current Lock Manager Node. Details: {}",
                                                   lockManagerInstanceId, lock.lockName, lock));
        }
        jdbi.useTransaction(handle -> {
            var rowsUpdated = handle.createUpdate("UPDATE " + fencedLocksTableName + " SET " +
                                                          "locked_by_lockmanager_instance_id=NULL\n" +
                                                          "WHERE lock_name=:lock_name AND last_issued_fence_token=:lock_last_issued_token")
                                    .bind("lock_name", lock.lockName)
                                    .bind("lock_last_issued_token", lock.currentToken)
                                    .execute();
            lock.markAsUnlocked();
            locksAcquiredByThisLockManager.remove(lock.lockName);
            if (rowsUpdated == 0) {
                // We didn't release the lock after all, someone else acquired the lock in the meantime
                lookupLockInDB(handle, lock.lockName).ifPresent(lockAcquiredByAnotherLockManager -> {
                    log.debug("[{}] Couldn't release Lock '{}' as it was already acquired by another JVM Node: {}", lockManagerInstanceId, lock.lockName, lockAcquiredByAnotherLockManager.lockedByLockManagerInstanceId);
                });
            } else {
                log.debug("[{}] Released Lock '{}': {}", lockManagerInstanceId, lock.lockName, lock);
            }
        });
    }

    @Override
    public Optional<FencedLock> lookupLock(LockName lockName) {
        return lookupLockInDB(lockName).map(FencedLock.class::cast);
    }

    private Optional<PostgresqlFencedLock> lookupLockInDB(LockName lockName) {
        return jdbi.inTransaction(handle -> lookupLockInDB(handle, lockName));
    }

    private Optional<PostgresqlFencedLock> lookupLockInDB(Handle handle, LockName lockName) {
        var lock = handle.createQuery("SELECT * FROM " + fencedLocksTableName + " WHERE lock_name=:lock_name")
                         .bind("lock_name", lockName)
                         .map(row -> new PostgresqlFencedLock(lockName,
                                                              row.getColumn("last_issued_fence_token", Long.class),
                                                              row.getColumn("locked_by_lockmanager_instance_id", String.class),
                                                              row.getColumn("lock_acquired_ts", OffsetDateTime.class),
                                                              row.getColumn("lock_last_confirmed_ts", OffsetDateTime.class)))
                         .findOne();
        log.trace("[{}] Looking up lock '{}': {}", lockManagerInstanceId, lockName, lock);
        return lock;
    }

    @Override
    public void stop() {
        if (started) {
            log.debug("[{}] Stopping down lock manager", lockManagerInstanceId);
            stopping = true;
            if (asyncLockAcquiringExecutor != null) {
                asyncLockAcquiringExecutor.shutdownNow();
                asyncLockAcquiringExecutor = null;
            }
            if (lockConfirmationExecutor != null) {
                lockConfirmationExecutor.shutdownNow();
                lockConfirmationExecutor = null;
            }
            locksAcquiredByThisLockManager.values().forEach(lockCallback -> lockCallback.release());
            started = false;
            stopping = false;
            log.debug("[{}] Stopped lock manager", lockManagerInstanceId);
        } else {
            log.debug("[{}] Lock Manager was already stopped", lockManagerInstanceId);
        }
    }


    @Override
    public boolean isStarted() {
        return started;
    }

    @Override
    public Optional<FencedLock> tryAcquireLock(LockName lockName) {
        var result = _tryAcquireLock(lockName).block();
        return Optional.ofNullable(result);

    }

    @Override
    public Optional<FencedLock> tryAcquireLock(LockName lockName, Duration timeout) {
        requireNonNull(timeout, "No timeout value provided");
        return Optional.ofNullable(_tryAcquireLock(lockName)
                                           .repeatWhenEmpty(longFlux -> longFlux.doOnNext(aLong -> {
                                           }))
                                           .block(timeout));
    }

    private Mono<PostgresqlFencedLock> _tryAcquireLock(LockName lockName) {
        requireNonNull(lockName, "No lockName provided");
        log.debug("[{}] Handling request to acquire lock '{}'", lockManagerInstanceId, lockName);
        var alreadyAcquiredLock = locksAcquiredByThisLockManager.get(lockName);
        if (alreadyAcquiredLock != null && alreadyAcquiredLock.isLocked() && !isLockTimedOut(alreadyAcquiredLock)) {
            log.debug("[{}] Returned cached locally acquired lock '{}", lockManagerInstanceId, lockName);
            return Mono.just(alreadyAcquiredLock);
        }
        return jdbi.inTransaction(handle -> {
            var lock = lookupLockInDB(lockName)
                    .orElseGet(() -> new PostgresqlFencedLock(lockName,
                                                              UNINITIALIZED_LOCK_TOKEN,
                                                              null,
                                                              null,
                                                              null));
            return resolveLock(handle, lock);
        });
    }

    private Mono<PostgresqlFencedLock> resolveLock(Handle handle, PostgresqlFencedLock existingLock) {
        requireNonNull(handle, "No handle provided");
        requireNonNull(existingLock, "No existingLock provided");

        if (existingLock.isLocked()) {
            if (existingLock.isLockedByThisLockManagerInstance()) {
                // TODO: Should we confirm it to ensure that lack_confirmed is updated??
                log.debug("[{}] lock '{}' was already acquired by this JVM node: {}", lockManagerInstanceId, existingLock.lockName, existingLock);
                locksAcquiredByThisLockManager.put(existingLock.lockName, existingLock);
                return Mono.just(existingLock);
            }
            if (isLockTimedOut(existingLock)) {
                // Timed out - let us acquire the lock
                var now = OffsetDateTime.now(Clock.systemUTC());
                var newLock = new PostgresqlFencedLock(existingLock.lockName,
                                                       existingLock.currentToken + 1L,
                                                       lockManagerInstanceId,
                                                       now,
                                                       now);
                log.debug("[{}] Found a TIMED-OUT lock '{}', that was acquired by Lock Manager '{}'. Will attempt to acquire the lock. Timed-out lock: {} - New lock: {}",
                          lockManagerInstanceId, existingLock.lockName, existingLock.lockedByLockManagerInstanceId, existingLock, newLock);
                return updateLockInDB(handle, existingLock, newLock);
            } else {
                return Mono.empty();
            }
        } else {
            if (existingLock.currentToken == UNINITIALIZED_LOCK_TOKEN) {
                return insertLockIntoDB(handle, existingLock);
            } else {
                var now = OffsetDateTime.now(Clock.systemUTC());
                var newLock = new PostgresqlFencedLock(existingLock.lockName,
                                                       existingLock.currentToken + 1L,
                                                       lockManagerInstanceId,
                                                       now,
                                                       now);
                log.debug("[{}] Found un-acquired lock '{}'. Have Acquired lock. Existing lock: {} - New lock: {}", lockManagerInstanceId, existingLock.lockName, existingLock, newLock);
                return updateLockInDB(handle, existingLock, newLock);
            }
        }
    }

    private Mono<PostgresqlFencedLock> insertLockIntoDB(Handle handle, PostgresqlFencedLock initialLock) {
        requireNonNull(handle, "No handle provided");
        requireNonNull(initialLock, "No initialLock provided");
        var now = OffsetDateTime.now(Clock.systemUTC());
        var rowsUpdated = handle.createUpdate("INSERT INTO " + fencedLocksTableName + " (" +
                                                      "lock_name, last_issued_fence_token, locked_by_lockmanager_instance_id ,\n" +
                                                      "lock_acquired_ts, lock_last_confirmed_ts)\n" +
                                                      " VALUES (\n" +
                                                      ":lock_name, :last_issued_fence_token, :locked_by_lockmanager_instance_id,\n" +
                                                      ":lock_acquired_ts, :lock_last_confirmed_ts)")
                                .bind("lock_name", initialLock.lockName)
                                .bind("last_issued_fence_token", FIRST_TOKEN)
                                .bind("locked_by_lockmanager_instance_id", lockManagerInstanceId)
                                .bind("lock_acquired_ts", now)
                                .bind("lock_last_confirmed_ts", now)
                                .execute();
        if (rowsUpdated == 0) {
            // We didn't acquire the lock after all
            log.debug("[{}] Failed to acquire lock '{}' for the first time (insert)", lockManagerInstanceId, initialLock.lockName);
            return Mono.empty();
        } else {
            initialLock.markAsLocked(now, lockManagerInstanceId, FIRST_TOKEN);
            log.debug("[{}] Acquired lock '{}' for the first time (insert): {}", lockManagerInstanceId, initialLock.lockName, initialLock);
            locksAcquiredByThisLockManager.put(initialLock.lockName, initialLock);
            return Mono.just(initialLock);
        }
    }

    private Mono<PostgresqlFencedLock> updateLockInDB(Handle handle, PostgresqlFencedLock timedOutLock, PostgresqlFencedLock newLockReadyToBeAcquiredLocally) {
        requireNonNull(handle, "No handle provided");
        requireNonNull(timedOutLock, "No timedOutLock provided");
        requireNonNull(newLockReadyToBeAcquiredLocally, "No newLockReadyToBeAcquiredLocally provided");

        var rowsUpdated = handle.createUpdate("UPDATE " + fencedLocksTableName + " SET " +
                                                      "last_issued_fence_token=:last_issued_fence_token, locked_by_lockmanager_instance_id=:locked_by_lockmanager_instance_id,\n" +
                                                      "lock_acquired_ts=:lock_acquired_ts, lock_last_confirmed_ts=:lock_last_confirmed_ts\n" +
                                                      "WHERE lock_name=:lock_name AND last_issued_fence_token=:previous_last_issued_fence_token")
                                .bind("lock_name", timedOutLock.lockName)
                                .bind("last_issued_fence_token", newLockReadyToBeAcquiredLocally.currentToken)
                                .bind("locked_by_lockmanager_instance_id", newLockReadyToBeAcquiredLocally.lockedByLockManagerInstanceId)
                                .bind("lock_acquired_ts", newLockReadyToBeAcquiredLocally.lockAcquiredTimestamp)
                                .bind("lock_last_confirmed_ts", newLockReadyToBeAcquiredLocally.lockLastConfirmedTimestamp)
                                .bind("previous_last_issued_fence_token", timedOutLock.currentToken)
                                .execute();
        if (rowsUpdated == 0) {
            // We didn't acquire the lock after all
            log.debug("[{}] Didn't acquire timed out lock '{}', someone else acquired it in the mean time(update): {}", lockManagerInstanceId, timedOutLock.lockName, lookupLockInDB(handle, timedOutLock.lockName));
            return Mono.empty();
        } else {
            log.debug("[{}] Acquired lock '{}' (update): {}", lockManagerInstanceId, timedOutLock.lockName, newLockReadyToBeAcquiredLocally);
            locksAcquiredByThisLockManager.put(timedOutLock.lockName, newLockReadyToBeAcquiredLocally);
            newLockReadyToBeAcquiredLocally.markAsLocked(newLockReadyToBeAcquiredLocally.lockAcquiredTimestamp, newLockReadyToBeAcquiredLocally.lockedByLockManagerInstanceId, newLockReadyToBeAcquiredLocally.currentToken);
            return Mono.just(newLockReadyToBeAcquiredLocally);
        }
    }

    private boolean isLockTimedOut(PostgresqlFencedLock lock) {
        requireNonNull(lock, "No lock provided");
        var durationSinceLastConfirmation = lock.getDurationSinceLastConfirmation();
        return durationSinceLastConfirmation.compareTo(lockTimeOut) >= 1;
    }

    @Override
    public FencedLock acquireLock(LockName lockName) {
        return _tryAcquireLock(lockName)
                .repeatWhenEmpty(longFlux -> longFlux.doOnNext(aLong -> {
                }))
                .block();
    }

    @Override
    public boolean isLockAcquired(LockName lockName) {
        var lock = lookupLockInDB(lockName);
        if (lock.isEmpty()) {
            return false;
        }
        return lock.get().isLocked();
    }

    @Override
    public boolean isLockedByThisLockManagerInstance(LockName lockName) {
        var lock = lookupLockInDB(lockName);
        if (lock.isEmpty()) {
            return false;
        }
        return lock.get().isLockedByThisLockManagerInstance();
    }

    @Override
    public boolean isLockAcquiredByAnotherLockManagerInstance(LockName lockName) {
        var lock = lookupLockInDB(lockName);
        if (lock.isEmpty()) {
            return false;
        }
        return lock.get().isLocked() && !lock.get().isLockedByThisLockManagerInstance();
    }

    @Override
    public void acquireLockAsync(LockName lockName, LockCallback lockCallback) {
        requireNonNull(lockName, "You must supply a lockName");
        requireNonNull(lockCallback, "You must supply a lockCallback");
        asyncLockAcquirings.computeIfAbsent(lockName, _lockName -> {
            log.debug("[{}] Starting async Lock acquiring for lock '{}'", lockManagerInstanceId, lockName);
            return asyncLockAcquiringExecutor.scheduleAtFixedRate(() -> {
                                                                      var existingLock = locksAcquiredByThisLockManager.get(lockName);
                                                                      if (existingLock == null) {
                                                                          if (paused) {
                                                                              log.info("[{}] Lock Manager is paused, skipping async acquiring for lock '{}'", lockManagerInstanceId, lockName);
                                                                              return;
                                                                          }

                                                                          var lock = tryAcquireLock(lockName);
                                                                          if (lock.isPresent()) {
                                                                              log.debug("[{}] Async Acquired lock '{}'", lockManagerInstanceId, lockName);
                                                                              var fencedLock = (PostgresqlFencedLock) lock.get();
                                                                              fencedLock.registerCallback(lockCallback);
                                                                              locksAcquiredByThisLockManager.put(lockName, fencedLock);
                                                                              lockCallback.lockAcquired(lock.get());
                                                                          } else {
                                                                              if (log.isTraceEnabled()) {
                                                                                  log.trace("[{}] Couldn't async Acquire lock '{}' as it is acquired by another Lock Manager instance: {}", lockManagerInstanceId, lockName, lookupLockInDB(lockName));
                                                                              }
                                                                          }
                                                                      } else if (!existingLock.isLockedByThisLockManagerInstance()) {
                                                                          log.debug("[{}] Noticed that lock '{}' isn't locked by this Lock Manager instance anymore. Releasing the lock", lockManagerInstanceId, lockName);
                                                                          locksAcquiredByThisLockManager.remove(lockName);
                                                                          lockCallback.lockReleased(existingLock);
                                                                      }
                                                                  },
                                                                  0,
                                                                  lockConfirmationInterval.toMillis(),
                                                                  TimeUnit.MILLISECONDS);
        });
    }

    @Override
    public void cancelAsyncLockAcquiring(LockName lockName) {
        requireNonNull(lockName, "You must supply a lockName");
        final ScheduledFuture<?> scheduledFuture = asyncLockAcquirings.remove(lockName);
        if (scheduledFuture != null) {
            log.debug("[{}] Canceling async Lock acquiring for lock '{}'", lockManagerInstanceId, lockName);
            scheduledFuture.cancel(true);
            var acquiredLock = locksAcquiredByThisLockManager.remove(lockName);
            if (acquiredLock.isLockedByThisLockManagerInstance()) {
                log.debug("[{}] Releasing Lock due to cancelling the lock acquiring '{}'", lockManagerInstanceId, lockName);
                acquiredLock.release();
            }
        }
    }

    @Override
    public String getLockManagerInstanceId() {
        return lockManagerInstanceId;
    }

    @Override
    public String toString() {
        return "PostgresqlFencedLockManager{" +
                "lockManagerInstanceId='" + lockManagerInstanceId + '\'' +
                '}';
    }

    private class PostgresqlFencedLock implements FencedLock {
        /**
         * The name of the lock
         */
        private LockName           lockName;
        /**
         * The current token value as of the {@link #lockLastConfirmedTimestamp} for this Lock across all {@link FencedLockManager} instances<br>
         * Every time a lock is acquired or confirmed a new token is issued (i.e. it's ever-growing value)
         */
        private long               currentToken;
        /**
         * Which JVM/{@link FencedLockManager#getLockManagerInstanceId()} that has acquired this lock
         */
        private String             lockedByLockManagerInstanceId;
        /**
         * At what time did the JVM/{@link FencedLockManager#getLockManagerInstanceId()} that currently has acquired the lock acquire it (at first acquiring the lock_last_confirmed_ts is set to lock_acquired_ts)
         */
        private OffsetDateTime     lockAcquiredTimestamp;
        /**
         * At what time did the JVM/{@link FencedLockManager}, that currently has acquired the lock, last confirm that it still has access to the lock
         */
        private OffsetDateTime     lockLastConfirmedTimestamp;
        private List<LockCallback> lockCallbacks;

        public PostgresqlFencedLock(LockName lockName,
                                    long currentToken,
                                    String lockedByBusInstanceId,
                                    OffsetDateTime lockAcquiredTimestamp,
                                    OffsetDateTime lockLastConfirmedTimestamp) {
            this.lockName = requireNonNull(lockName, "lockName is missing");
            this.currentToken = currentToken;
            this.lockedByLockManagerInstanceId = lockedByBusInstanceId;
            this.lockAcquiredTimestamp = lockAcquiredTimestamp;
            this.lockLastConfirmedTimestamp = lockLastConfirmedTimestamp;
            lockCallbacks = new ArrayList<>();
        }

        @Override
        public LockName getName() {
            return lockName;
        }

        @Override
        public long getCurrentToken() {
            return currentToken;
        }

        @Override
        public String getLockedByLockManagerInstanceId() {
            return lockedByLockManagerInstanceId;
        }

        @Override
        public OffsetDateTime getLockAcquiredTimestamp() {
            return lockAcquiredTimestamp;
        }

        @Override
        public OffsetDateTime getLockLastConfirmedTimestamp() {
            return lockLastConfirmedTimestamp;
        }

        @Override
        public boolean isLocked() {
            return lockedByLockManagerInstanceId != null;
        }

        @Override
        public boolean isLockedByThisLockManagerInstance() {
            return isLocked() && Objects.equals(lockedByLockManagerInstanceId, lockManagerInstanceId);
        }

        @Override
        public void release() {
            if (isLockedByThisLockManagerInstance()) {
                releaseLock(this);
            }
        }

        @Override
        public void registerCallback(LockCallback lockCallback) {
            lockCallbacks.add(lockCallback);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PostgresqlFencedLock that = (PostgresqlFencedLock) o;
            return lockName.equals(that.lockName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(lockName);
        }

        public Duration getDurationSinceLastConfirmation() {
            FailFast.requireNonNull(lockLastConfirmedTimestamp, msg("FencedLock '{}' doesn't have a lockLastConfirmedTimestamp", lockName));
            return Duration.between(lockLastConfirmedTimestamp, ZonedDateTime.now()).abs();
        }

        private void markAsUnlocked() {
            lockedByLockManagerInstanceId = null;
            lockCallbacks.forEach(lockCallback -> lockCallback.lockReleased(this));
        }

        PostgresqlFencedLock markAsConfirmed(long newToken, OffsetDateTime confirmedTimestamp) {
            lockLastConfirmedTimestamp = confirmedTimestamp;
            currentToken = newToken;
            return this;
        }

        public PostgresqlFencedLock markAsLocked(OffsetDateTime lockTime, String lockedByLockManagerInstanceId, long currentToken) {
            this.lockAcquiredTimestamp = lockTime;
            this.lockLastConfirmedTimestamp = lockTime;
            this.lockedByLockManagerInstanceId = lockedByLockManagerInstanceId;
            this.currentToken = currentToken;
            lockCallbacks.forEach(lockCallback -> lockCallback.lockAcquired(this));
            return this;
        }

        @Override
        public String toString() {
            return "PostgresqlFencedLock{" +
                    "lockName=" + lockName +
                    ", currentTokenIssuedToThisLockInstance=" + currentToken +
                    ", lockedByLockManagerInstanceId='" + lockedByLockManagerInstanceId + '\'' +
                    ", lockAcquiredTimestamp=" + lockAcquiredTimestamp +
                    ", lockLastConfirmedTimestamp=" + lockLastConfirmedTimestamp +
                    '}';
        }
    }
}
