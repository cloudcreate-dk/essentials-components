package dk.cloudcreate.essentials.components.eventsourced.aggregates.classic.objenesis;

import dk.cloudcreate.essentials.components.eventsourced.aggregates.classic.*;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.classic.objenesis.NoDefaultConstructorOrderEvents.*;
import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Objenesis OrderAggregateRootTest")
class OrderAggregateRootTest {

    @Test
    void verify_that_an_initial_event_with_null_aggregateid_causes_failure() {
        assertThatThrownBy(() -> new Order(null, CustomerId.random(), 123))
                .isExactlyInstanceOf(InitialEventIsMissingAggregateIdException.class);
    }

    @Test
    void verify_the_aggregates_id_is_the_same_as_the_initial_events_aggregateid() {
        // Given
        var orderId            = OrderId.random();
        var orderingCustomerId = CustomerId.random();
        var orderNumber        = 123;

        // When
        var order = new Order(orderId,
                              orderingCustomerId,
                              orderNumber);

        // Then
        assertThat(order.uncommittedChanges().size()).isEqualTo(1);
        assertThat(order.uncommittedChanges().get(0)).isInstanceOf(OrderAdded.class);

        var orderAddedEvent = (OrderAdded) order.uncommittedChanges().get(0);
        assertThat((CharSequence) orderAddedEvent.aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) orderAddedEvent.orderingCustomerId).isEqualTo(orderingCustomerId);
        assertThat(orderAddedEvent.orderNumber).isEqualTo(orderNumber);
        assertThat(orderAddedEvent.eventOrder()).isEqualTo(0);

        assertThat((CharSequence) order.aggregateId()).isEqualTo(orderId);
        assertThat(order.eventOrderOfLastAppliedEvent()).isEqualTo(0);
    }

    @Test
    void verify_markChangesAsCommitted_resets_uncomittedChanges() {
        // Given
        var orderId            = OrderId.random();
        var orderingCustomerId = CustomerId.random();
        var orderNumber        = 123;

        var aggregate = new Order(orderId, orderingCustomerId, orderNumber);
        assertThat(aggregate.uncommittedChanges().size()).isEqualTo(1);
        assertThat((CharSequence) aggregate.aggregateId()).isEqualTo(orderId);
        assertThat(aggregate.eventOrderOfLastAppliedEvent()).isEqualTo(0);

        // When
        aggregate.markChangesAsCommitted();

        // Then
        assertThat(aggregate.uncommittedChanges().size()).isEqualTo(0);
    }

    @Test
    void test_rehydrating_aggregate() {
        // given
        var orderId            = OrderId.random();
        var orderingCustomerId = CustomerId.random();
        var orderNumber        = 123;
        var productId          = ProductId.random();

        var aggregate = new Order(orderId, orderingCustomerId, orderNumber);
        assertThat(aggregate.productAndQuantity.get(productId)).isNull();

        // And
        aggregate.addProduct(productId, 10);

        assertThat((CharSequence) aggregate.aggregateId()).isEqualTo(orderId);
        assertThat(aggregate.productAndQuantity.get(productId)).isEqualTo(10);
        assertThat(aggregate.uncommittedChanges().size()).isEqualTo(2);
        assertThat(aggregate.eventOrderOfLastAppliedEvent()).isEqualTo(1);

        assertThat(aggregate.uncommittedChanges().get(1)).isInstanceOf(ProductAddedToOrder.class);
        var productAddedEvent = (ProductAddedToOrder) aggregate.uncommittedChanges().get(1);
        assertThat((CharSequence) productAddedEvent.aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) productAddedEvent.productId).isEqualTo(productId);
        assertThat(productAddedEvent.quantity).isEqualTo(10);
        assertThat(productAddedEvent.eventOrder()).isEqualTo(1);

        // when
        var rehydratedAggregate = AggregateRootInstanceFactory.objenesisAggregateRootFactory().create(OrderId.class, Order.class)
                                                              .rehydrate(aggregate.uncommittedChanges().stream());

        // then
        assertThat((CharSequence) rehydratedAggregate.aggregateId()).isEqualTo(orderId);
        assertThat(rehydratedAggregate.productAndQuantity.get(productId)).isEqualTo(10);
        assertThat(rehydratedAggregate.uncommittedChanges().size()).isEqualTo(0);
        assertThat(rehydratedAggregate.eventOrderOfLastAppliedEvent()).isEqualTo(1);
    }

    @Test
    void test_rehydrating_aggregate_and_then_modifying_the_aggregate_state() {
        // given
        var orderId            = OrderId.random();
        var orderingCustomerId = CustomerId.random();
        var orderNumber        = 123;
        var productId          = ProductId.random();

        var aggregate = new Order(orderId, orderingCustomerId, orderNumber);
        assertThat(aggregate.productAndQuantity.get(productId)).isNull();

        // And
        aggregate.addProduct(productId, 10);

        assertThat((CharSequence) aggregate.aggregateId()).isEqualTo(orderId);
        assertThat(aggregate.productAndQuantity.get(productId)).isEqualTo(10);
        assertThat(aggregate.uncommittedChanges().size()).isEqualTo(2);
        assertThat(aggregate.eventOrderOfLastAppliedEvent()).isEqualTo(1);

        // when
        var rehydratedAggregate = AggregateRootInstanceFactory.objenesisAggregateRootFactory().create(OrderId.class, Order.class)
                                                              .rehydrate(aggregate.uncommittedChanges().stream());
        var newProductId = ProductId.random();
        rehydratedAggregate.addProduct(newProductId, 3);

        // then
        assertThat(rehydratedAggregate.uncommittedChanges().size()).isEqualTo(1);
        assertThat(rehydratedAggregate.eventOrderOfLastAppliedEvent()).isEqualTo(2);
        assertThat(rehydratedAggregate.uncommittedChanges().get(0)).isInstanceOf(ProductAddedToOrder.class);

        var newProductAddedEvent = (ProductAddedToOrder) rehydratedAggregate.uncommittedChanges().get(0);
        assertThat((CharSequence) newProductAddedEvent.aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) newProductAddedEvent.productId).isEqualTo(newProductId);
        assertThat(newProductAddedEvent.quantity).isEqualTo(3);
        assertThat(newProductAddedEvent.eventOrder()).isEqualTo(2);

        assertThat((CharSequence) rehydratedAggregate.aggregateId()).isEqualTo(orderId);
        assertThat(rehydratedAggregate.productAndQuantity.get(productId)).isEqualTo(10);
        assertThat(rehydratedAggregate.productAndQuantity.get(newProductId)).isEqualTo(3);
    }
}