package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type;

import com.fasterxml.jackson.databind.ObjectMapper;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json.*;

import java.util.Objects;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Configuration for the persistence of an {@link AggregateEventStream} belonging to a given {@link AggregateType}
 */
public class SeparateTablePerAggregateTypeConfiguration extends AggregateTypeConfiguration {
    /**
     * The unique name of the Postgresql table name where ONLY {@link PersistedEvent}'s related to {@link #aggregateType} are stored<br>
     * <b>Note: The table name provided will automatically be converted to <u>lower case</u></b>
     */
    public final String                      eventStreamTableName;
    /**
     * The names of the {@link #eventStreamTableName} columns - The actual implementation must be compatible with the chosen {@link AggregateEventStreamPersistenceStrategy}
     */
    public final EventStreamTableColumnNames eventStreamTableColumnNames;

    public SeparateTablePerAggregateTypeConfiguration(AggregateType aggregateType,
                                                      String eventStreamTableName,
                                                      EventStreamTableColumnNames eventStreamTableColumnNames,
                                                      int queryFetchSize,
                                                      JSONSerializer jsonSerializer,
                                                      AggregateIdSerializer aggregateIdSerializer,
                                                      IdentifierColumnType aggregateIdColumnType,
                                                      IdentifierColumnType eventIdColumnType,
                                                      IdentifierColumnType correlationIdColumnType,
                                                      JSONColumnType eventJsonColumnType,
                                                      JSONColumnType eventMetadataJsonColumnType,
                                                      TenantSerializer tenantSerializer) {
        super(aggregateType,
              queryFetchSize,
              jsonSerializer,
              aggregateIdSerializer,
              aggregateIdColumnType,
              eventIdColumnType,
              correlationIdColumnType,
              eventJsonColumnType,
              eventMetadataJsonColumnType,
              tenantSerializer);
        this.eventStreamTableName = requireNonNull(eventStreamTableName, "No eventStreamTableName provided").toLowerCase();
        this.eventStreamTableColumnNames = requireNonNull(eventStreamTableColumnNames, "No eventStreamTableColumnNames provided");
    }

    public static SeparateTablePerAggregateTypeConfiguration standardSingleTenantConfigurationUsingJackson(AggregateType aggregateType,
                                                                                                           ObjectMapper objectMapper,
                                                                                                           AggregateIdSerializer aggregateIdSerializer,
                                                                                                           IdentifierColumnType identifierColumnTypeUsedForAllIdentifiers,
                                                                                                           JSONColumnType jsonColumnTypeUsedForAllJSONColumns) {
        return standardConfigurationUsingJackson(aggregateType,
                                                 objectMapper,
                                                 aggregateIdSerializer,
                                                 identifierColumnTypeUsedForAllIdentifiers,
                                                 jsonColumnTypeUsedForAllJSONColumns,
                                                 new TenantSerializer.NoSupportForMultiTenancySerializer());
    }

    public static SeparateTablePerAggregateTypeConfiguration standardConfigurationUsingJackson(AggregateType aggregateType,
                                                                                               ObjectMapper objectMapper,
                                                                                               AggregateIdSerializer aggregateIdSerializer,
                                                                                               IdentifierColumnType identifierColumnTypeUsedForAllIdentifiers,
                                                                                               JSONColumnType jsonColumnTypeUsedForAllJSONColumns,
                                                                                               TenantSerializer tenantSerializer) {
        requireNonNull(aggregateType, "No aggregateType provided");
        return new SeparateTablePerAggregateTypeConfiguration(aggregateType,
                                                              aggregateType.toString() + "_events",
                                                              EventStreamTableColumnNames.defaultColumnNames(),
                                                              100,
                                                              new JacksonJSONSerializer(objectMapper),
                                                              aggregateIdSerializer,
                                                              identifierColumnTypeUsedForAllIdentifiers,
                                                              identifierColumnTypeUsedForAllIdentifiers,
                                                              identifierColumnTypeUsedForAllIdentifiers,
                                                              jsonColumnTypeUsedForAllJSONColumns,
                                                              jsonColumnTypeUsedForAllJSONColumns,
                                                              tenantSerializer);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SeparateTablePerAggregateTypeConfiguration)) return false;
        SeparateTablePerAggregateTypeConfiguration that = (SeparateTablePerAggregateTypeConfiguration) o;
        return aggregateType.equals(that.aggregateType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(aggregateType);
    }

    @Override
    public String toString() {
        return "SeparateTablePerAggregateEventStreamConfiguration{" +
                "aggregateType=" + aggregateType +
                '}';
    }
}
