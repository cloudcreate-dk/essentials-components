package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream;

/**
 * The type of Postgresql column type to use for a given identifier {@link PersistedEvent} field
 */
public enum IdentifierColumnType {
    TEXT,
    UUID
}
