package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.spring.test_data;

import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.StatefulAggregateInstanceFactory.ReflectionBasedAggregateInstanceFactory;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.StatefulAggregateRepository;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.classic.AggregateRoot;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.classic.state.AggregateRootWithState;

import static dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.spring.test_data.OrderEvent.*;
import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Example Order aggregate for testing {@link AggregateRoot} and {@link StatefulAggregateRepository}
 */
public class Order extends AggregateRootWithState<OrderId, OrderEvent, OrderState, Order> {

    /**
     * Needed if we use {@link ReflectionBasedAggregateInstanceFactory}
     */
    public Order() {
    }

    public Order(OrderId orderId,
                 CustomerId orderingCustomerId,
                 int orderNumber) {
        requireNonNull(orderId, "You must provide an orderId");
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
