package dk.cloudcreate.essentials.components.common;

/**
 * Common process life cycle interface
 */
public interface Lifecycle {
    /**
     * Start the processing. This operation must be idempotent, such that duplicate calls
     * to {@link #start()} for an already started process (where {@link #isStarted()} returns true)
     * is ignored
     */
    void start();

    /**
     * Stop the processing. This operation must be idempotent, such that duplicate calls
     * to {@link #stop()} for an already stopped process (where {@link #isStarted()} returns false)
     * is ignored
     */
    void stop();

    /**
     * Returns true if the process is started
     *
     * @return true if the process is started otherwise false
     */
    boolean isStarted();
}
