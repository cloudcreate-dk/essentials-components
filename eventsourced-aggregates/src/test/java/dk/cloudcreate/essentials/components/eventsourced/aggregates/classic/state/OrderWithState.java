package dk.cloudcreate.essentials.components.eventsourced.aggregates.classic.state;

import dk.cloudcreate.essentials.components.eventsourced.aggregates.*;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.StatefulAggregateInstanceFactory.ReflectionBasedAggregateInstanceFactory;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.StatefulAggregateRepository;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.classic.*;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.classic.state.AggregateRootWithState;

import static dk.cloudcreate.essentials.components.eventsourced.aggregates.classic.OrderEvents.*;
import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Example Order aggregate for testing {@link AggregateRoot} and {@link StatefulAggregateRepository}
 */
public class OrderWithState extends AggregateRootWithState<OrderId, Event<OrderId>, OrderState, OrderWithState> {

    /**
     * Needed if we use {@link ReflectionBasedAggregateInstanceFactory}
     */
    public OrderWithState() {
    }

    public OrderWithState(OrderId orderId,
                          CustomerId orderingCustomerId,
                          int orderNumber) {
        // Normally you will ensure that orderId is never NULL, but to perform certain tests we need to option to allow this to be null
        //FailFast.requireNonNull(orderId, "You must provide an orderId");
        requireNonNull(orderingCustomerId, "You must provide an orderingCustomerId");

        apply(new OrderAdded(orderId,
                             orderingCustomerId,
                             orderNumber));
    }

    public void addProduct(ProductId productId, int quantity) {
        requireNonNull(productId, "You must provide a productId");
        if (state.accepted) {
            throw new IllegalStateException("Order is already accepted");
        }
        apply(new ProductAddedToOrder(productId, quantity));
    }

    public void adjustProductQuantity(ProductId productId, int newQuantity) {
        requireNonNull(productId, "You must provide a productId");
        if (state.accepted) {
            throw new IllegalStateException("Order is already accepted");
        }
        if (state.productAndQuantity.containsKey(productId)) {
            apply(new ProductOrderQuantityAdjusted(productId, newQuantity));
        }
    }

    public void removeProduct(ProductId productId) {
        requireNonNull(productId, "You must provide a productId");
        if (state.accepted) {
            throw new IllegalStateException("Order is already accepted");
        }
        if (state.productAndQuantity.containsKey(productId)) {
            apply(new ProductRemovedFromOrder(productId));
        }
    }

    public void accept() {
        if (state.accepted) {
            return;
        }
        apply(new OrderAccepted());
    }

    /**
     * For test purpose
     */
    public OrderState state() {
        return state;
    }
}
