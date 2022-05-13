package dk.cloudcreate.essentials.components.eventsourced.aggregates.classic.objenesis;

import dk.cloudcreate.essentials.components.eventsourced.aggregates.EventHandler;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.classic.*;

import java.util.*;

import static dk.cloudcreate.essentials.components.eventsourced.aggregates.classic.objenesis.NoDefaultConstructorOrderEvents.*;
import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Example Order aggregate for testing {@link AggregateRoot} and {@link AggregateRootRepository}<br>
 * Requires that {@link AggregateRootRepository} instance creation
 * is configured to use {@link AggregateRootInstanceFactory#objenesisAggregateRootFactory()},
 * since the aggregate doesn't have a default constructor
 */
public class Order extends AggregateRoot<OrderId, Order> {
    Map<ProductId, Integer> productAndQuantity;
    boolean                 accepted;

    public Order(OrderId orderId,
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
        if (accepted) {
            throw new IllegalStateException("Order is already accepted");
        }
        apply(new ProductAddedToOrder(productId, quantity));
    }

    public void adjustProductQuantity(ProductId productId, int newQuantity) {
        requireNonNull(productId, "You must provide a productId");
        if (accepted) {
            throw new IllegalStateException("Order is already accepted");
        }
        if (productAndQuantity.containsKey(productId)) {
            apply(new ProductOrderQuantityAdjusted(productId, newQuantity));
        }
    }

    public void removeProduct(ProductId productId) {
        requireNonNull(productId, "You must provide a productId");
        if (accepted) {
            throw new IllegalStateException("Order is already accepted");
        }
        if (productAndQuantity.containsKey(productId)) {
            apply(new ProductRemovedFromOrder(productId));
        }
    }

    public void accept() {
        if (accepted) {
            return;
        }
        apply(new OrderAccepted());
    }

//    @Override
//    protected void applyEventToTheAggregate(Event<OrderId> event) {
//        if (event instanceof OrderAdded e) {
//            // You don't need to store all properties from an Event inside the Aggregate.
//            // Only do this IF it actually is needed for business logic and in this cases none of them are needed
//            // for further command processing
//
//            // To support instantiation using e.g. Objenesis we initialize productAndQuantity here
//            productAndQuantity = new HashMap<>();
//        } else if (event instanceof ProductAddedToOrder e) {
//            var existingQuantity = productAndQuantity.get(e.productId);
//            productAndQuantity.put(e.productId, e.quantity + (existingQuantity != null ? existingQuantity : 0));
//        } else if (event instanceof ProductOrderQuantityAdjusted e) {
//             productAndQuantity.put(e.productId, e.newQuantity);
//        } else if (event instanceof ProductRemovedFromOrder e) {
//            productAndQuantity = productAndQuantity.remove(e.productId);
//        } else if (event instanceof OrderAccepted) {
//            accepted = true;
//        }
//    }

    @EventHandler
    private void on(OrderAdded e) {
        productAndQuantity = new HashMap<>();
    }

    @EventHandler
    private void on(ProductAddedToOrder e) {
        var existingQuantity = productAndQuantity.get(e.productId);
        productAndQuantity.put(e.productId, e.quantity + (existingQuantity != null ? existingQuantity : 0));
    }

    @EventHandler
    private void on(ProductOrderQuantityAdjusted e) {
        productAndQuantity.put(e.productId, e.newQuantity);
    }

    @EventHandler
    private void on(ProductRemovedFromOrder e) {
        productAndQuantity.remove(e.productId);
    }

    @EventHandler
    private void on(OrderAccepted e) {
        accepted = true;
    }
}
