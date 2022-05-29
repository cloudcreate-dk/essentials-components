package dk.cloudcreate.essentials.components.eventsourced.aggregates.modern.with_state;

import dk.cloudcreate.essentials.components.eventsourced.aggregates.*;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.modern.OrderEvent;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.modern.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

public class Order extends AggregateRoot<OrderId, OrderEvent, Order> implements WithState<OrderId, OrderEvent, Order, OrderState> {
    /**
     * Used for rehydration
     */
    public Order(OrderId orderId) {
        super(orderId);
    }

    public Order(OrderId orderId,
                 CustomerId orderingCustomerId,
                 int orderNumber) {
        super(orderId);
        requireNonNull(orderingCustomerId, "You must provide an orderingCustomerId");

        apply(new OrderEvent.OrderAdded(orderId,
                                        orderingCustomerId,
                                        orderNumber));
    }

    public void addProduct(ProductId productId, int quantity) {
        requireNonNull(productId, "You must provide a productId");
        if (state().accepted) {
            throw new IllegalStateException("Order is already accepted");
        }
        apply(new OrderEvent.ProductAddedToOrder(aggregateId(),
                                                 productId,
                                                 quantity));
    }

    public void adjustProductQuantity(ProductId productId, int newQuantity) {
        requireNonNull(productId, "You must provide a productId");
        if (state().accepted) {
            throw new IllegalStateException("Order is already accepted");
        }
        if (state().productAndQuantity.containsKey(productId)) {
            apply(new OrderEvent.ProductOrderQuantityAdjusted(aggregateId(),
                                                              productId,
                                                              newQuantity));
        }
    }

    public void removeProduct(ProductId productId) {
        requireNonNull(productId, "You must provide a productId");
        if (state().accepted) {
            throw new IllegalStateException("Order is already accepted");
        }
        if (state().productAndQuantity.containsKey(productId)) {
            apply(new OrderEvent.ProductRemovedFromOrder(aggregateId(),
                                                         productId));
        }
    }

    public void accept() {
        if (state().accepted) {
            return;
        }
        apply(eventOrder -> new OrderEvent.OrderAccepted(aggregateId(),
                                                         eventOrder));
    }

    /**
     * For test purpose and to allow the {@link AggregateRoot#state()} method to return
     * the correct type, such that we don't need to use e.g. <code>state(OrderState.class).accepted</code><br>
     * If this wasn't for test purposes, then we could have kept the protected visibility
     */
    @SuppressWarnings("unchecked")
    public OrderState state() {
        return super.state();
    }
}
