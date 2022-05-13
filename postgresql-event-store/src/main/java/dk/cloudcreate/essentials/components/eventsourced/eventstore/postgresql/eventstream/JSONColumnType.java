package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream;

/**
 * The Postgresql column type to use for a given {@link PersistedEvent} JSON field
 */
public enum JSONColumnType {
    JSON,
    JSONB
}
