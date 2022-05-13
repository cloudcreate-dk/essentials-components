package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence;


import dk.cloudcreate.essentials.components.common.types.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.*;

import java.time.*;
import java.util.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Common interface for a Persistable Events, i.e. an Event that hasn't yet been persisted (as opposed to a {@link PersistedEvent}).<br>
 * A {@link PersistableEvent} forms a wrapper around the real business {@link #event()}.<br>
 * You can create a new instance using {@link #from(EventId, AggregateType, Object, EventTypeOrName, Object, EventOrder, EventRevision, EventMetaData, OffsetDateTime, EventId, CorrelationId, Tenant)}
 * or {@link DefaultPersistableEvent#DefaultPersistableEvent(EventId, AggregateType, Object, EventTypeOrName, Object, EventOrder, EventRevision, EventMetaData, OffsetDateTime, EventId, CorrelationId, Tenant)}
 * <br>
 * Two {@link PersistableEvent}'s are <b>equal</b> if the have the same {@link #eventId()} value<br>
 * If you want to compare the contents, please use {@link #eventId()}
 *
 * @see PersistedEvent
 */
public interface PersistableEvent {
    static PersistableEvent from(EventId eventId,
                                 AggregateType streamName,
                                 Object aggregateId,
                                 EventTypeOrName eventTypeOrName,
                                 Object event,
                                 EventOrder eventOrder,
                                 EventRevision eventRevision,
                                 EventMetaData metaData,
                                 OffsetDateTime timestamp,
                                 EventId causedByEventId,
                                 CorrelationId correlationId,
                                 Tenant tenant) {
        return new DefaultPersistableEvent(eventId,
                                           streamName,
                                           aggregateId,
                                           eventTypeOrName,
                                           event,
                                           eventOrder,
                                           eventRevision,
                                           metaData,
                                           timestamp,
                                           causedByEventId,
                                           correlationId,
                                           tenant);
    }

    /**
     * Unique id for this Event
     */
    EventId eventId();

    /**
     * Contains the name of the Event-Stream this aggregates events belongs to
     */
    AggregateType streamName();

    /**
     * The java type for the event or alternatively the name of the Event in case the {@link #event()} is a String containing the raw JSON
     */
    EventTypeOrName eventTypeOrName();

    /**
     * Contains the aggregate identifier that an event is related to.<br>
     * This is also known as the Stream-Id
     */
    Object aggregateId();

    /**
     * Contains the order of an event relative to the aggregate instance (the {@link #aggregateId})<br>
     * Each event has its own unique position within the stream, also known as the event-order,
     * which defines the order, in which the events were added to the aggregates {@link AggregateEventStream}<br>
     * <br>
     * The first eventOrder should have value 0 - but ultimately it's a decision of the developer.<br>
     * This is also commonly called the version or sequenceNumber, and it's a sequential ever-growing number.<br>
     * related to a <b>specific</b> aggregate instance (as opposed to the {@link PersistedEvent#globalEventOrder()} which contains
     * the order of ALL events related to a specific {@link AggregateType})
     */
    EventOrder eventOrder();

    /**
     * The timestamp for this Event. Will always be persisted as UTC. If empty the underlying {@link EventStore}
     * will provide a value
     */
    Optional<OffsetDateTime> timestamp();

    /**
     * Unique id of the Event that caused this Event to exist (aka the causal Event).<br>
     * This is useful for tracing the the causality of Events on top of the {@link #correlationId()}
     */
    Optional<EventId> causedByEventId();

    /**
     * The correlation id for this event (used for tracking all events related).<br>
     * Having this value is optional, but highly recommended
     */
    Optional<CorrelationId> correlationId();

    /**
     * The revision of the {@link #event()} - first revision has value 1
     */
    EventRevision eventRevision();

    /**
     * Additional user controlled event metadata
     */
    EventMetaData metaData();

    /**
     * The tenant that the event belongs to<br>
     * Having a {@link Tenant} value associated with an Event is optional.
     */
    Optional<Tenant> tenant();

    /**
     * The actual Event instance that's going to be persisted<br>
     * At this point the event hasn't been serialized yet - as opposed to the {@link PersistedEvent#event()} where the business event is Serialized
     */
    Object event();

    boolean valueEquals(PersistableEvent that);

    class DefaultPersistableEvent implements PersistableEvent {
        private final EventId                    eventId;
        private final Object          aggregateId;
        private final AggregateType   streamName;
        private final EventTypeOrName eventTypeOrName;
        private final Object                     event;
        private final EventOrder                 eventOrder;
        private final EventRevision              eventRevision;
        private final EventMetaData              metaData;
        private final Optional<OffsetDateTime>   timestamp;
        private final Optional<EventId>          causedByEventId;
        private final Optional<CorrelationId>    correlationId;
        private final Optional<Tenant> tenant;

        public DefaultPersistableEvent(EventId eventId,
                                       AggregateType streamName,
                                       Object aggregateId,
                                       EventTypeOrName eventTypeOrName,
                                       Object event,
                                       EventOrder eventOrder,
                                       EventRevision eventRevision,
                                       EventMetaData metaData,
                                       OffsetDateTime timestamp,
                                       EventId causedByEventId,
                                       CorrelationId correlationId,
                                       Tenant tenant) {
            this.eventId = requireNonNull(eventId, "You must supply a eventId");
            this.streamName = requireNonNull(streamName, "You must supply a streamName");
            this.aggregateId = requireNonNull(aggregateId, "You must supply a aggregateId");
            this.eventTypeOrName = requireNonNull(eventTypeOrName, "You must supply an eventTypeOrName");
            this.event = requireNonNull(event, "You must supply a event instance");
            this.eventOrder = requireNonNull(eventOrder, "You must supply an eventOrder instance");
            this.eventRevision = requireNonNull(eventRevision, "You must supply an eventRevision instance");
            this.timestamp = Optional.ofNullable(timestamp);
            this.causedByEventId = Optional.ofNullable(causedByEventId);
            this.correlationId = Optional.ofNullable(correlationId);
            this.metaData = requireNonNull(metaData, "You must supply a metaData instance");
            this.tenant = Optional.ofNullable(tenant);
        }

        @Override
        public Object aggregateId() {
            return aggregateId;
        }

        @Override
        public EventOrder eventOrder() {
            return eventOrder;
        }

        @Override
        public Optional<OffsetDateTime> timestamp() {
            return timestamp;
        }

        @Override
        public EventId eventId() {
            return eventId;
        }

        @Override
        public AggregateType streamName() {
            return streamName;
        }

        @Override
        public EventTypeOrName eventTypeOrName() {
            return eventTypeOrName;
        }

        @Override
        public Optional<EventId> causedByEventId() {
            return causedByEventId;
        }

        @Override
        public Optional<CorrelationId> correlationId() {
            return correlationId;
        }

        @Override
        public EventRevision eventRevision() {
            return eventRevision;
        }

        @Override
        public EventMetaData metaData() {
            return metaData;
        }

        @Override
        public Optional<Tenant> tenant() {
            return tenant;
        }

        @Override
        public Object event() {
            return event;
        }

        /**
         * Compare each individual property in <code>this</code> {@link PersistableEvent} and compare them individually
         * with the provided <code>that</code> {@link PersistableEvent}
         *
         * @param that the {@link PersistableEvent} to compare this instance against
         * @return true if ALL properties in <code>this</code> instance match those in the <code>that</code> instance
         */
        @Override
        public boolean valueEquals(PersistableEvent that) {
            if (this == that) return true;
            if (that == null) return false;
            return aggregateId.equals(that.aggregateId()) &&
                    streamName.equals(that.streamName()) &&
                    eventTypeOrName.equals(that.eventTypeOrName()) &&
                    event.equals(that.event()) &&
                    eventOrder.equals(that.eventOrder()) &&
                    eventId.equals(that.eventId()) &&
                    eventRevision.equals(that.eventRevision()) &&
                    metaData.equals(that.metaData()) &&
                    timestamp.map(zonedDateTime -> zonedDateTime.toInstant().toEpochMilli()).equals(that.timestamp().map(zonedDateTime -> zonedDateTime.toInstant().toEpochMilli())) &&
                    causedByEventId.equals(that.causedByEventId()) &&
                    correlationId.equals(that.correlationId()) &&
                    tenant.equals(that.tenant());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PersistableEvent)) return false;
            var that = (PersistableEvent) o;
            return eventId.equals(that.eventId());
        }

        @Override
        public int hashCode() {
            return Objects.hash(eventId);
        }

        @Override
        public String toString() {
            return "PersistableEvent{" +
                    "eventId=" + eventId +
                    ", aggregateId=" + aggregateId +
                    ", streamName=" + streamName +
                    ", eventTypeOrName=" + eventTypeOrName +
                    ", eventOrder=" + eventOrder +
                    ", eventRevision=" + eventRevision +
                    ", event (payload type)=" + event.getClass().getName() +
                    ", metaData=" + metaData +
                    ", timestamp=" + timestamp +
                    ", causedByEventId=" + causedByEventId +
                    ", correlationId=" + correlationId +
                    ", tenant=" + tenant +
                    '}';
        }
    }
}
