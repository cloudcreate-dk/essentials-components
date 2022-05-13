package dk.cloudcreate.essentials.components.eventsourced.aggregates.classic.state;

import dk.cloudcreate.essentials.components.eventsourced.aggregates.EventHandler;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.classic.*;
import dk.cloudcreate.essentials.shared.reflection.Reflector;
import dk.cloudcreate.essentials.shared.types.GenericType;

import java.util.List;
import java.util.stream.Stream;

/**
 * Variant of the {@link AggregateRoot} pattern where the aggregate's state and all {@link EventHandler} annotated methods
 * are placed within the concrete {@link AggregateState} object.<br>
 * When the {@link AggregateRootWithState} is combined with {@link AggregateState}, then the {@link AggregateRootWithState}
 * will contain the command methods and the {@link AggregateState} contains the state fields and the
 * {@link EventHandler} annotated methods.
 *
 * @param <ID>             the aggregate id type
 * @param <AGGREGATE_TYPE> the aggregate self type (i.e. your concrete aggregate type)
 * @param <STATE>          the aggregate state type (i.e. your concrete aggregate state)
 */
public class AggregateRootWithState<ID, STATE extends AggregateState<ID>, AGGREGATE_TYPE extends AggregateRootWithState<ID, STATE, AGGREGATE_TYPE>> extends AggregateRoot<ID, AGGREGATE_TYPE> {
    protected STATE state;

    public AggregateRootWithState() {
    }

    /**
     * Override this method to initialize the {@link #state} variable in case
     * the {@link #resolveStateImplementationClass()} doesn't fit the requirements
     */
    @Override
    protected void initialize() {
        var stateType = resolveStateImplementationClass();
        state = Reflector.reflectOn(stateType).newInstance();
    }

    @SuppressWarnings("unchecked")
    @Override
    public AGGREGATE_TYPE rehydrate(Stream<Event<ID>> previousEvents) {
        if (state == null) {
            // Instance was created by Objenesis
            initialize();
        }
        state.rehydrate(previousEvents);
        return (AGGREGATE_TYPE) this;
    }

    @Override
    protected void apply(Event<ID> event) {
        state.apply(event);
    }

    @Override
    public ID aggregateId() {
        return state.aggregateId();
    }

    @Override
    public long eventOrderOfLastAppliedEvent() {
        return state.eventOrderOfLastAppliedEvent();
    }

    @Override
    public List<Event<ID>> uncommittedChanges() {
        return state.uncommittedChanges();
    }

    @Override
    public void markChangesAsCommitted() {
        state.markChangesAsCommitted();
    }

    /**
     * Override this method to provide a non reflection based look up of the Type Argument
     * provided to the {@link AggregateRootWithState} class
     *
     * @return the {@link AggregateState} implementation to use for the {@link #state} instance
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected Class<AggregateState> resolveStateImplementationClass() {
        return (Class<AggregateState>) GenericType.resolveGenericType(this.getClass(), 1)
                                                  .orElseThrow(() -> new IllegalStateException("Couldn't resolve the concrete AggregateState type used for " + this.getClass().getName()));
    }
}
