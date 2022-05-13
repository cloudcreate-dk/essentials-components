package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.AggregateIdSerializer.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json.JSONSerializer;

import java.util.Objects;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Configuration for the persistence of an {@link AggregateEventStream} belonging to a given {@link AggregateType}
 */
public class AggregateTypeConfiguration {
    /**
     * The type of Aggregate this configuration relates to
     */
    public final AggregateType         aggregateType;
    /**
     * The SQL fetch size for Queries
     */
    public final int                   queryFetchSize;
    /**
     * The {@link JSONSerializer} used to serialize and deserialize {@link PersistedEvent#event()}
     * and {@link PersistedEvent#metaData()}
     */
    public final JSONSerializer        jsonSerializer;
    public final AggregateIdSerializer aggregateIdSerializer;
    /**
     * We've deliberately split {@link AggregateIdSerializer} and {@link #aggregateIdColumnType}
     * as it's possible to use e.g. a {@link StringIdSerializer} or {@link CharSequenceTypeIdSerializer}
     * which purely contains {@link java.util.UUID} string values, in which case it should be possible to
     * map these to {@link IdentifierColumnType#UUID}
     */
    public final IdentifierColumnType  aggregateIdColumnType;
    public final IdentifierColumnType  eventIdColumnType;
    public final IdentifierColumnType  correlationIdColumnType;
    public final JSONColumnType        eventJsonColumnType;
    public final JSONColumnType        eventMetadataJsonColumnType;
    public final TenantSerializer      tenantSerializer;

    public AggregateTypeConfiguration(AggregateType aggregateType,
                                      int queryFetchSize,
                                      JSONSerializer jsonSerializer,
                                      AggregateIdSerializer aggregateIdSerializer,
                                      IdentifierColumnType aggregateIdColumnType,
                                      IdentifierColumnType eventIdColumnType,
                                      IdentifierColumnType correlationIdColumnType,
                                      JSONColumnType eventJsonColumnType,
                                      JSONColumnType eventMetadataJsonColumnType,
                                      TenantSerializer tenantSerializer) {
        this.aggregateType = requireNonNull(aggregateType, "No aggregateType provided");
        this.queryFetchSize = queryFetchSize;
        this.jsonSerializer = requireNonNull(jsonSerializer, "No jsonSerializer provided");
        this.aggregateIdSerializer = requireNonNull(aggregateIdSerializer, "No aggregateIdSerializer provided");
        this.aggregateIdColumnType = requireNonNull(aggregateIdColumnType, "No aggregateIdColumnType provided");
        this.eventIdColumnType = requireNonNull(eventIdColumnType, "No eventIdColumnType provided");
        this.correlationIdColumnType = requireNonNull(correlationIdColumnType, "No correlationIdColumnType provided");
        this.eventJsonColumnType = requireNonNull(eventJsonColumnType, "No eventJsonColumnType provided");
        this.eventMetadataJsonColumnType = requireNonNull(eventMetadataJsonColumnType, "No eventMetadataJsonColumnType provided");
        this.tenantSerializer = requireNonNull(tenantSerializer, "No tenantSerializer provided");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AggregateTypeConfiguration)) return false;
        AggregateTypeConfiguration that = (AggregateTypeConfiguration) o;
        return aggregateType.equals(that.aggregateType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(aggregateType);
    }

    @Override
    public String toString() {
        return "AggregateEventStreamConfiguration{" +
                "aggregateType=" + aggregateType +
                '}';
    }
}
