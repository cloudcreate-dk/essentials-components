package dk.cloudcreate.essentials.components.eventsourced.aggregates.classic.objenesis.state;

import dk.cloudcreate.essentials.components.eventsourced.aggregates.*;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.classic.objenesis.NoDefaultConstructorOrderEvents.*;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.classic.Event;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.classic.state.AggregateState;

import java.util.*;

public class OrderState extends AggregateState<OrderId, Event<OrderId>> {
    // Fields are public for framework tests performed - this isn't a pattern to replicate in a business application
    public Map<ProductId, Integer> productAndQuantity;
    public boolean                 accepted;

//    @Override
//    protected void applyEventToTheAggregate(Event<OrderId> event) {
//        if (event instanceof OrderAdded e) {
//            // You don't need to store all properties from an Event inside the AggregateState.
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
