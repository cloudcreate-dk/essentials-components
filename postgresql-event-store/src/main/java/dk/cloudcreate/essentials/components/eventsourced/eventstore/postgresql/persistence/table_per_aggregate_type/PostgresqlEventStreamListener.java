package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;

import java.util.Optional;

/**
 * Support for notification based async polling (where the {@link EventStore} only polls when necessary, is coming soon.<br>
 * Until then you have to use {@link EventStore#pollEvents(AggregateType, long, Optional, Optional, Optional, Optional)} which
 * uses periodic event store polling
 */
public class PostgresqlEventStreamListener /* implements Lifecycle */ {
}
