package dk.cloudcreate.essentials.components.eventsourced.aggregates.classic;

public final class OrderEvents {
    // ------------------------------------------------------------------------ Events ------------------------------------------------------------------------------------
    // Note: These Events assume the ObjectMapper is configured without Objenesis Jackson JSON instantiation (only requires EssentialTypesJacksonModule from types-jackson)
    // ------------------------------------------------------------------------ Events ------------------------------------------------------------------------------------
    public static class OrderAdded extends Event<OrderId> {
        private CustomerId orderingCustomerId;
        private long       orderNumber;

        public OrderAdded() {
        }

        public OrderAdded(OrderId orderId, CustomerId orderingCustomerId, long orderNumber) {
            // MUST be set manually for the FIRST/INITIAL - after this the AggregateRoot ensures
            // that the aggregateId will be set on the other events automatically
            aggregateId(orderId);
            this.orderingCustomerId = orderingCustomerId;
            this.orderNumber = orderNumber;
        }

        public CustomerId getOrderingCustomerId() {
            return orderingCustomerId;
        }

        public long getOrderNumber() {
            return orderNumber;
        }
    }

    public static class ProductAddedToOrder extends Event<OrderId> {
        private ProductId productId;
        private int       quantity;

        public ProductAddedToOrder() {
        }

        public ProductAddedToOrder(ProductId productId, int quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }

        public ProductId getProductId() {
            return productId;
        }

        public int getQuantity() {
            return quantity;
        }
    }

    public static class ProductOrderQuantityAdjusted extends Event<OrderId> {
        private ProductId productId;
        private int       newQuantity;

        public ProductOrderQuantityAdjusted() {
        }

        public ProductOrderQuantityAdjusted(ProductId productId, int newQuantity) {
            this.productId = productId;
            this.newQuantity = newQuantity;
        }

        public ProductId getProductId() {
            return productId;
        }

        public int getNewQuantity() {
            return newQuantity;
        }
    }

    public static class ProductRemovedFromOrder extends Event<OrderId> {
        private ProductId productId;

        public ProductRemovedFromOrder() {
        }

        public ProductRemovedFromOrder(ProductId productId) {
            this.productId = productId;
        }

        public ProductId getProductId() {
            return productId;
        }
    }

    public static class OrderAccepted extends Event<OrderId> {
    }
}
