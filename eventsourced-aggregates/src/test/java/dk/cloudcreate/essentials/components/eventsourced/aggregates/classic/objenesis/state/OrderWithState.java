package dk.cloudcreate.essentials.components.eventsourced.aggregates.classic.objenesis.state;

import dk.cloudcreate.essentials.components.eventsourced.aggregates.*;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.classic.*;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.classic.AggregateRootInstanceFactory.DefaultConstructorAggregateRootInstanceFactory;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.classic.objenesis.NoDefaultConstructorOrderEvents.*;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.classic.state.AggregateRootWithState;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Example Order aggregate for testing {@link AggregateRoot} and {@link AggregateRootRepository}
 */
public class OrderWithState extends AggregateRootWithState<OrderId, OrderState, OrderWithState> {

    /**
     * Needed if we use {@link DefaultConstructorAggregateRootInstanceFactory}
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
