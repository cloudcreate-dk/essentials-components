package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type;

import dk.cloudcreate.essentials.components.common.types.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStoreException;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.PersistedEvent;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.EventMetaData;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.*;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.*;
import java.time.OffsetDateTime;
import java.util.Optional;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

class PersistedEventRowMapper implements RowMapper<PersistedEvent> {
    private final SeparateTablePerAggregateTypePersistenceStrategy persistenceStrategy;
    private final SeparateTablePerAggregateTypeConfiguration       config;

    public PersistedEventRowMapper(SeparateTablePerAggregateTypePersistenceStrategy persistenceStrategy, SeparateTablePerAggregateTypeConfiguration configuration) {
        this.persistenceStrategy = requireNonNull(persistenceStrategy, "No persistenceStrategy provided");
        this.config = requireNonNull(configuration, "No EventStream configuration provided");
    }


    @Override
    public PersistedEvent map(ResultSet rs, StatementContext ctx) throws SQLException {
        return PersistedEvent.from(EventId.of(rs.getString(config.eventStreamTableColumnNames.eventIdColumn)),
                                   config.aggregateType,
                                   config.aggregateIdSerializer.deserialize(rs.getString(config.eventStreamTableColumnNames.aggregateIdColumn)),
                                   resolveEventJSON(rs),
                                   EventOrder.of(rs.getLong(config.eventStreamTableColumnNames.eventOrderColumn)),
                                   EventRevision.of(rs.getInt(config.eventStreamTableColumnNames.eventRevisionColumn)),
                                   GlobalEventOrder.of(rs.getLong(config.eventStreamTableColumnNames.globalOrderColumn)),
                                   resolveEventMetaDataJSON(rs),
                                   rs.getObject(config.eventStreamTableColumnNames.timestampColumn, OffsetDateTime.class),
                                   EventId.optionalFrom(rs.getString(config.eventStreamTableColumnNames.causedByEventIdColumn)),
                                   CorrelationId.optionalFrom(rs.getString(config.eventStreamTableColumnNames.correlationIdColumn)),
                                   resolveTenant(rs));
    }

    private Optional<Tenant> resolveTenant(ResultSet rs) {
        return config.tenantSerializer.deserialize(getString(rs, config.eventStreamTableColumnNames.tenantColumn));
    }

    private EventJSON resolveEventJSON(ResultSet resultSet) throws SQLException {
        var jsonPayload          = getString(resultSet, config.eventStreamTableColumnNames.eventPayloadColumn);
        var eventTypeOrNameValue = getString(resultSet, config.eventStreamTableColumnNames.eventTypeColumn);

        if (eventTypeOrNameValue == null || eventTypeOrNameValue.isBlank()) {
            throw new IllegalStateException(msg("[{}] Row: {} - Column '{}' column was empty or blank",
                                                config.aggregateType,
                                                resultSet.getRow(),
                                                config.eventStreamTableColumnNames.eventTypeColumn));
        }
        if (EventType.isSerializedEventType(eventTypeOrNameValue)) {
            return new EventJSON(config.jsonSerializer,
                                 EventType.of(eventTypeOrNameValue),
                                 jsonPayload);
        } else {
            return new EventJSON(config.jsonSerializer,
                                 EventName.of(eventTypeOrNameValue),
                                 jsonPayload);
        }
    }

    private EventMetaDataJSON resolveEventMetaDataJSON(ResultSet resultSet) {
        var jsonPayload = getString(resultSet, config.eventStreamTableColumnNames.eventMetaDataColumn);
        return new EventMetaDataJSON(config.jsonSerializer,
                                     EventMetaData.class.getName(),
                                     jsonPayload);
    }


    private String getString(ResultSet resultSet, String columnName) {
        try {
            return resultSet.getString(columnName);
        } catch (SQLException e) {
            throw new EventStoreException(msg("Failed to getString from ResultSet in relation to columnName '{}'", columnName), e);
        }
    }

}
