package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.test_data;

public class OrderEvent {
    public final OrderId orderId;

    public OrderEvent(OrderId orderId) {
        this.orderId = orderId;
    }

    public static class OrderAdded extends OrderEvent {
        public final CustomerId orderingCustomerId;
        public final long       orderNumber;

        public OrderAdded(OrderId orderId, CustomerId orderingCustomerId, long orderNumber) {
            super(orderId);
            this.orderingCustomerId = orderingCustomerId;
            this.orderNumber = orderNumber;
        }
    }

    public static class ProductAddedToOrder extends OrderEvent {
        public final ProductId productId;
        public final int       quantity;

        public ProductAddedToOrder(OrderId orderId, ProductId productId, int quantity) {
            super(orderId);
            this.productId = productId;
            this.quantity = quantity;
        }
    }

    public static class ProductOrderQuantityAdjusted extends OrderEvent {
        public final ProductId productId;
        public final int       newQuantity;

        public ProductOrderQuantityAdjusted(OrderId orderId, ProductId productId, int newQuantity) {
            super(orderId);
            this.productId = productId;
            this.newQuantity = newQuantity;
        }
    }

    public static class ProductRemovedFromOrder extends OrderEvent {
        public final ProductId productId;

        public ProductRemovedFromOrder(OrderId orderId, ProductId productId) {
            super(orderId);
            this.productId = productId;
        }
    }

    public static class OrderAccepted extends OrderEvent {
        public OrderAccepted(OrderId orderId) {
            super(orderId);
        }
    }
}
