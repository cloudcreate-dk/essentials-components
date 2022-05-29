package dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.modern;

import dk.cloudcreate.essentials.components.eventsourced.aggregates.EventHandler;

/**
 * Marker interface that indicates that all state and all {@link EventHandler} annotated methods
 * will be hosted with the {@link AggregateState} object. The state object will be maintained by the {@link AggregateRoot}.<br>
 * You can use the {@link AggregateRoot#state()} or {@link AggregateRoot#state(Class)}  methodst o access the concrete State object.
 *
 * @param <ID>              the type of aggregate id
 * @param <EVENT_TYPE>      the type of event
 * @param <AGGREGATE_TYPE>  the type of aggregate
 * @param <AGGREGATE_STATE> the type of the aggregate-state
 * @see AggregateRoot#state()
 * @see AggregateRoot#state(Class)
 */
public interface WithState<ID, EVENT_TYPE, AGGREGATE_TYPE extends AggregateRoot<ID, EVENT_TYPE, AGGREGATE_TYPE>, AGGREGATE_STATE extends AggregateState<ID, EVENT_TYPE, AGGREGATE_TYPE>> {
}
