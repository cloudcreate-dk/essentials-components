package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.bus;

/**
 * Commit stage
 */
public enum CommitStage {
    BeforeCommit,
    AfterCommit,
    AfterRollback;
}
