package dk.cloudcreate.essentials.components.eventsourced.aggregates.flex;


import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventOrder;

import java.util.*;

import static dk.cloudcreate.essentials.shared.FailFast.*;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * Wrapper object that captures the results of any command handling (e.g. static constructor method or instance command methods in {@link FlexAggregate} sub classes).<br>
 * The purpose of this wrapper is to wrap information about the aggregate-id, event-order of last applied historic event (if any) and any events that were a side effect of the command method invocation
 *
 * @param <ID> the aggregate id type
 */
public class EventsToPersist<ID> {
    /**
     * Special value that signifies the no previous events have been persisted in relation to a given aggregate
     */
    public static EventOrder NO_EVENTS_HAVE_BEEN_PERSISTED = EventOrder.NO_EVENTS_PERSISTED;

    /**
     * The id of the aggregate this relates to
     */
    public final ID           aggregateId;
    /**
     * (Zero based event order) contains the eventOrder for the last stored event relates to this event. See {@link #NO_EVENTS_HAVE_BEEN_PERSISTED}<br>
     * A null value is only allow if the {@link #eventsToPersist} is empty
     */
    public final EventOrder   eventOrderOfLastAppliedHistoricEvent;
    public final List<Object> eventsToPersist;
    private      boolean      committed;

    /**
     * @param aggregateId                          the aggregate id this relates to
     * @param eventOrderOfLastAppliedHistoricEvent (Zero based event order) contains the eventOrder for the last stored event relates to this event. See {@link #NO_EVENTS_HAVE_BEEN_PERSISTED}<br>
     *                                             A null value is only allow if the <code>eventsToPersist</code> is empty
     * @param eventsToPersist                      the events to persist, which will be the result/side-effect of a command method invocation in an {@link FlexAggregate}).
     */
    private EventsToPersist(ID aggregateId,
                            EventOrder eventOrderOfLastAppliedHistoricEvent,
                            List<Object> eventsToPersist) {
        this.aggregateId = requireNonNull(aggregateId, "You must supply an aggregate id");
        this.eventOrderOfLastAppliedHistoricEvent = eventOrderOfLastAppliedHistoricEvent;
        this.eventsToPersist = eventsToPersist;
    }

    /**
     * @param aggregateId                          the aggregate id this relates to
     * @param eventOrderOfLastAppliedHistoricEvent (Zero based event order) contains the eventOrder for the last stored event relates to this event. See {@link #NO_EVENTS_HAVE_BEEN_PERSISTED}<br>
     *                                             A null value is only allow if the <code>eventsToPersist</code> is empty
     * @param eventsToPersist                      the events to persist, which will be the result/side-effect of a command method invocation in an {@link FlexAggregate}).
     */
    private EventsToPersist(ID aggregateId,
                            EventOrder eventOrderOfLastAppliedHistoricEvent,
                            Object... eventsToPersist) {
        this.aggregateId = requireNonNull(aggregateId, "You must supply an aggregate id");
        this.eventOrderOfLastAppliedHistoricEvent = eventOrderOfLastAppliedHistoricEvent;
        this.eventsToPersist = List.of(eventsToPersist);
    }

    /**
     * Wrap the events that should be persisted for this new aggregate
     *
     * @param aggregateId     the aggregate id this relates to
     * @param eventsToPersist the events to persist, which will be the result/side-effect of a command method invocation in an {@link FlexAggregate}).
     *                        May be empty if the command method invocation didn't result in any events (e.g. due to idempotency checks)
     * @param <ID>            the aggregate id type
     */
    public static <ID> EventsToPersist<ID> initialAggregateEvents(ID aggregateId,
                                                                  Object... eventsToPersist) {
        return new EventsToPersist<>(requireNonNull(aggregateId, "You must supply an aggregateId"),
                                     NO_EVENTS_HAVE_BEEN_PERSISTED,
                                     requireNonEmpty(eventsToPersist, "A new aggregate instance must contain at least one event to persist"));
    }

    /**
     * Wrap the events that should be persisted for this existing aggregate
     *
     * @param aggregateId                          the aggregate id this relates to
     * @param eventOrderOfLastAppliedHistoricEvent (Zero based event order) contains the eventOrder for the last stored event relates to this event. See {@link #NO_EVENTS_HAVE_BEEN_PERSISTED}<br>
     *                                             A null value is only allow if the <code>eventsToPersist</code> is empty
     * @param eventsToPersist                      the events to persist, which will be the result/side-effect of a command method invocation in an {@link FlexAggregate}).
     *                                             May be empty if the command method invocation didn't result in any events (e.g. due to idempotency checks)
     * @param <ID>                                 the aggregate id type
     */
    public static <ID> EventsToPersist<ID> events(ID aggregateId,
                                                  EventOrder eventOrderOfLastAppliedHistoricEvent,
                                                  Object... eventsToPersist) {
        return new EventsToPersist<>(aggregateId,
                                     eventOrderOfLastAppliedHistoricEvent,
                                     eventsToPersist);
    }

    /**
     * Wrap the events that should be persisted relates to the given aggregate's command handling
     *
     * @param aggregate        the easy aggregate instance that the <code>eventsToPersist</code>
     * @param eventsToPersist  the events to persist, which will be the result/side-effect of a command method invocation in an {@link FlexAggregate}).
     *                         May be empty if the command method invocation didn't result in any events (e.g. due to idempotency checks)
     * @param <ID>             the aggregate id type
     * @param <AGGREGATE_TYPE> the aggregate type
     */
    public static <ID, AGGREGATE_TYPE extends FlexAggregate<ID, AGGREGATE_TYPE>> EventsToPersist<ID> events(FlexAggregate<ID, AGGREGATE_TYPE> aggregate,
                                                                                                            Object... eventsToPersist) {
        return new EventsToPersist<>(requireNonNull(aggregate.aggregateId(), "Aggregate doesn't have an aggregateId, please use initialAggregateEvents(id, events)"),
                                     aggregate.eventOrderOfLastAppliedHistoricEvent(),
                                     eventsToPersist);
    }

    /**
     * Creates an empty {@link EventsToPersist} which is handy for a commanded method didn't have a side-effect (e.g. due to idempotent handling)
     *
     * @param aggregateId the aggregate id this relates to
     * @param <ID>        the aggregate id type
     */
    public static <ID> EventsToPersist<ID> noEvents(ID aggregateId) {
        return new EventsToPersist<>(aggregateId, null);
    }

    /**
     * Creates an empty {@link EventsToPersist} which is handy for a commanded method didn't have a side-effect (e.g. due to idempotent handling)
     *
     * @param aggregate        the easy aggregate instance
     * @param <ID>             the aggregate id type
     * @param <AGGREGATE_TYPE> the aggregate type
     */
    public static <ID, AGGREGATE_TYPE extends FlexAggregate<ID, AGGREGATE_TYPE>> EventsToPersist<ID> noEvents(FlexAggregate<ID, AGGREGATE_TYPE> aggregate) {
        return new EventsToPersist<>(aggregate.aggregateId(),
                                     aggregate.eventOrderOfLastAppliedHistoricEvent());
    }

    public void markEventsAsCommitted() {
        committed = true;
    }

    public boolean isCommitted() {
        return committed;
    }

    /**
     * Immutable method that appends all events in <code>appendEventsToPersist</code>
     * to the {@link #eventsToPersist} in this instance. The result is returned as a NEW
     * {@link EventsToPersist} instance that contains the combined list of events
     *
     * @param appendEventsToPersist the events to append to this instance
     * @return The result is returned as a NEW
     * {@link EventsToPersist} instance that contains the combined list of events. The new {@link EventsToPersist}
     * retains the original value of the {@link EventsToPersist#eventOrderOfLastAppliedHistoricEvent}
     */
    public EventsToPersist<ID> append(EventsToPersist<ID> appendEventsToPersist) {
        requireNonNull(appendEventsToPersist, "You must supply an appendEventsToPersist instance");

        if (!this.aggregateId.equals(appendEventsToPersist.aggregateId)) {
            throw new IllegalArgumentException(msg("Cannot append appendEventsToPersist since aggregate id's are not the same. " +
                                                           "this.aggregateId='{}', appendEventsToPersist.aggregateId='{}'",
                                                   this.aggregateId,
                                                   appendEventsToPersist.aggregateId));
        }

        if (eventOrderOfLastAppliedHistoricEvent == null && !eventsToPersist.isEmpty()) {
            throw new IllegalStateException("this.eventOrderOfLastAppliedHistoricEvent is null and therefore doesn't support appending");
        }
        if (appendEventsToPersist.eventOrderOfLastAppliedHistoricEvent == null) {
            throw new IllegalStateException("appendEventsToPersist.eventOrderOfLastAppliedHistoricEvent is null");
        }

        long expectedEventOrderOfLastAppliedHistoricEvent = eventOrderOfLastAppliedHistoricEvent.longValue() + eventsToPersist.size();
        if (expectedEventOrderOfLastAppliedHistoricEvent != appendEventsToPersist.eventOrderOfLastAppliedHistoricEvent.longValue()) {
            throw new IllegalArgumentException(msg("Cannot append appendEventsToPersist as appendEventsToPersist.eventOrderOfLastAppliedHistoricEvent was " +
                                                           "{} but it was expected to be {}",
                                                   appendEventsToPersist.eventOrderOfLastAppliedHistoricEvent,
                                                   expectedEventOrderOfLastAppliedHistoricEvent));
        }

        var allEventsToPersist = new ArrayList<>(eventsToPersist);
        allEventsToPersist.addAll(appendEventsToPersist.eventsToPersist);
        return new EventsToPersist<>(aggregateId,
                                     eventOrderOfLastAppliedHistoricEvent,
                                     allEventsToPersist);
    }

    @Override
    public String toString() {
        return "EventsToPersist{" +
                "aggregateId=" + aggregateId +
                ", eventOrderOfLastAppliedHistoricEvent=" + eventOrderOfLastAppliedHistoricEvent +
                ", eventsToPersist=" + eventsToPersist.size() +
                ", committed=" + committed +
                '}';
    }
}
