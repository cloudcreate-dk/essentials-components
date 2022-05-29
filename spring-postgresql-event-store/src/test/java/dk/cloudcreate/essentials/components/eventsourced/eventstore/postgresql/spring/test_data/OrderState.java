package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.spring.test_data;

import dk.cloudcreate.essentials.components.eventsourced.aggregates.EventHandler;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.stateful.classic.state.AggregateState;

import java.util.*;

import static dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.spring.test_data.OrderEvent.*;

public class OrderState extends AggregateState<OrderId, OrderEvent> {
    // Fields are public for framework tests performed - this isn't a pattern to replicate in a business application
    public Map<ProductId, Integer> productAndQuantity;
    public boolean                 accepted;

    @EventHandler
    private void on(OrderAdded e) {
        productAndQuantity = new HashMap<>();
    }

    @EventHandler
    private void on(ProductAddedToOrder e) {
        var existingQuantity = productAndQuantity.get(e.getProductId());
        productAndQuantity.put(e.getProductId(), e.getQuantity() + (existingQuantity != null ? existingQuantity : 0));
    }

    @EventHandler
    private void on(ProductOrderQuantityAdjusted e) {
        productAndQuantity.put(e.getProductId(), e.getNewQuantity());
    }

    @EventHandler
    private void on(ProductRemovedFromOrder e) {
        productAndQuantity.remove(e.getProductId());
    }

    @EventHandler
    private void on(OrderAccepted e) {
        accepted = true;
    }
}
