package dk.cloudcreate.essentials.components.eventsourced.aggregates.classic;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;

/**
 * Based Event type that's built to work in combination with {@link AggregateRoot}<br>
 * All you need to do is to inherit from this class when building your own {@link Event} types.<p>
 * <b>Note:</b> You only have to supply the Aggregate ID, through {@link Event#aggregateId()}, for the FIRST/initial {@link Event}
 * that's being applied to the {@link AggregateRoot} using the {@link AggregateRoot#apply(Event)} method.<br>
 * Every consecutive {@link Event} applied will have its {@link Event#aggregateId(Object)} method called IF it doesn't already have a value. I.e. you can be lazy and skip setting the aggregate id
 * on the Event if you don't want to.<br>
 * The {@link AggregateRoot} also automatically keeps track of the {@link Event#eventOrder()} value and will set it for you and ensure that it's growing consecutively.
 *
 * @param <ID> the aggregate id type
 */
public abstract class Event<ID> {
    private ID   aggregateId;
    private long eventOrder;

    /**
     * Get the id of the aggregate this event relates to. As a developer you only need to supply this value yourself for the very FIRST {@link Event} that is being applied<br>
     * Every consecutive {@link Event} applied will have its {@link Event#aggregateId(Object)} method called IF it doesn't already have a value. I.e. you can be lazy and skip setting the aggregate id
     * on the Event if you don't want to.
     */
    public ID aggregateId() {
        return aggregateId;
    }

    /**
     * Set the id of the aggregate this event relates to. As a developer you only need to set this value yourself for the very FIRST {@link Event} that is being applied<br>
     * Every consecutive {@link Event} applied will have its {@link Event#aggregateId(Object)} method called IF it doesn't already have a value. I.e. you can be lazy and skip setting the aggregate id
     * on the Event if you don't want to.
     *
     * @param aggregateId the id of the aggregate this even relates to
     */
    public void aggregateId(ID aggregateId) {
        this.aggregateId = aggregateId;
    }

    /**
     * Contains the order of the event relative to the aggregate instance (the {@link #aggregateId}) it relates to<br>
     * This is also commonly called the sequenceNumber and it's a sequential ever growing number that tracks the order in which events have been stored in the {@link EventStore}
     * related to a <b>specific</b> aggregate instance (as opposed to the <b>globalOrder</b> that contains the order of ALL events related to a given {@link AggregateType})<p>
     * {@link #eventOrder()} is zero based, i.e. the first event has order value ZERO (0)<p>
     * The {@link AggregateRoot} automatically keeps track of the {@link Event#eventOrder()} value and will set it for you and ensure that it's growing consecutively
     */
    public long eventOrder() {
        return eventOrder;
    }

    /**
     * Set the event order - called by {@link AggregateRoot}
     *
     * @param eventOrder the order of the event for the aggregate instance the event relates to
     */
    public void eventOrder(long eventOrder) {
        this.eventOrder = eventOrder;
    }
}
