package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.AggregateTypeConfiguration;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventOrder;
import dk.cloudcreate.essentials.types.LongRange;

import java.util.*;
import java.util.function.Function;
import java.util.stream.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Contains all Events related to a given domain-object/aggregate instance, are stored in an
 * Event Stream instance.<br>
 * An {@link AggregateEventStream} returned from the {@link EventStore} is guaranteed to contain at least one event, otherwise
 * the {@link EventStore#fetchStream(AggregateType, Object)} will return an {@link Optional#empty()}<br>
 * <b>Multiple instances of a given domain-object/aggregate type will be persisted in multiple {@link AggregateEventStream}'s</b><br>
 * <br>
 * The {@link AggregateEventStream} is the single source of truth for the domain object as it contains
 * the entire history of changes, in the form of events, related to the given domain-object/aggregate instance.<br>
 * <br>
 * The identifier of the domain-object/aggregate that all events in the stream is related to is, also known
 * as the Stream-Id, is tracked in the {@link #aggregateId()}.<br>
 * <br>
 * Each event has its own unique position within the stream, also known as the event-order,
 * which defines the order, in which the events were added to the stream.<br>
 * A stream has a unique id, that represents the identity of the domain-object, for which
 * the stream stores events<br>
 * <br>
 * Finally, a stream also has a {@link AggregateType}, which is used for grouping/categorizing multiple {@link AggregateEventStream} instances related to
 * similar types of aggregates. This allows us to easily retrieve or be notified of new Events related to the same type of Aggregates.<br>
 * <b>Note: The aggregate type is only a name and shouldn't be confused with the Fully Qualified Class Name of an Aggregate implementation class. At the {@link EventStore}
 * this is supported as an <b>In Memory Projection</b> - see {@link InMemoryProjector}</b><br>
 */
public interface AggregateEventStream<STREAM_ID> {
    static <STREAM_ID> AggregateEventStream<STREAM_ID> of(AggregateTypeConfiguration configuration,
                                                          STREAM_ID aggregateId,
                                                          LongRange eventOrderRangeIncluded,
                                                          Stream<PersistedEvent> stream) {
        return new DefaultAggregateEventStream<>(configuration,
                                                 aggregateId,
                                                 eventOrderRangeIncluded,
                                                 stream);
    }

    /**
     * Returns true if this event stream doesn't contain all events (such as when the the {@link #eventOrderRangeIncluded()} doesn't contain the first or last events in the full event stream)
     *
     * @return true if this event stream doesn't contain all events
     */
    boolean isPartialEventStream();

    /**
     * Contains the Range of {@link EventOrder}'s included in this event stream
     *
     * @return the Range of {@link EventOrder}'s included in this event stream
     * @see #isPartialEventStream()
     */
    LongRange eventOrderRangeIncluded();

    /**
     * The aggregate type that the {@link AggregateEventStream} is associated with
     *
     * @return the aggregate type that the {@link AggregateEventStream} is associated with
     */
    AggregateType aggregateType();

    /**
     * Contains the shared aggregate identifier that all events in the stream is related to.<br>
     * This is also known as the Stream-Id
     *
     * @return the shared aggregate identifier that all event in the stream is related to
     */
    STREAM_ID aggregateId();

    /**
     * Map the {@link PersistedEvent}'s in the {@link AggregateEventStream} to a different type
     *
     * @param mappingFunction the mapping function
     * @param <R>             the return type from the mapping function
     * @return stream of mapped {@link PersistedEvent}'s
     */
    default <R> Stream<R> map(Function<PersistedEvent, R> mappingFunction) {
        requireNonNull(mappingFunction, "No mappingFunction provided");
        return events().map(mappingFunction);
    }

    /**
     * Get a {@link Stream} of all the {@link PersistedEvent} within the {@link AggregateEventStream}.<br>
     * Check {@link #isPartialEventStream()} to determine if the events included represents the full event stream<br>
     * If you need to acquire the stream multiple times you either have to cache
     * the result or use {@link #eventList()} instead
     *
     * @return a {@link Stream} of all the {@link PersistedEvent} within the {@link AggregateEventStream}
     */
    Stream<PersistedEvent> events();

    /**
     * Get the {@link #events()} as a List (convenience method).<br>
     * This method can be called multiple times, as long as the {@link #events()}
     * stream hasn't been used.
     *
     * @return list of all events in the stream
     */
    List<PersistedEvent> eventList();

    class DefaultAggregateEventStream<STREAM_ID> implements AggregateEventStream<STREAM_ID> {

        private final AggregateTypeConfiguration configuration;
        private final STREAM_ID                  aggregateId;
        private final LongRange                         eventOrderRangeIncluded;

        private Stream<PersistedEvent> stream;
        private List<PersistedEvent>   eventList;

        public DefaultAggregateEventStream(AggregateTypeConfiguration configuration, STREAM_ID aggregateId, LongRange eventOrderRangeIncluded, Stream<PersistedEvent> stream) {

            this.configuration = requireNonNull(configuration, "No configuration provided");
            this.aggregateId = requireNonNull(aggregateId, "No aggregateId provided");
            this.eventOrderRangeIncluded = requireNonNull(eventOrderRangeIncluded, "No eventOrderRangeIncluded provided");
            this.stream = requireNonNull(stream, "No stream provided");
        }

        @Override
        public boolean isPartialEventStream() {
            return eventOrderRangeIncluded.fromInclusive > 0 || eventOrderRangeIncluded.isClosedRange();
        }

        @Override
        public LongRange eventOrderRangeIncluded() {
            return eventOrderRangeIncluded;
        }

        @Override
        public AggregateType aggregateType() {
            return configuration.aggregateType;
        }

        @Override
        public STREAM_ID aggregateId() {
            return aggregateId;
        }

        @Override
        public Stream<PersistedEvent> events() {
            if (stream == null) {
                stream = eventList().stream();
            }
            return stream;
        }

        @Override
        public List<PersistedEvent> eventList() {
            if (eventList == null) {
                if (stream == null) {
                    throw new IllegalStateException("Both stream and eventList are null");
                }
                eventList = stream.collect(Collectors.toList());
                stream = null;
            }
            return eventList;
        }
    }
}
