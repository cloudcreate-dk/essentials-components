package dk.cloudcreate.essentials.components.queue.postgresql.test_data;

public class ProductEvent {
    public final ProductId productId;

    public ProductEvent(ProductId productId) {
        this.productId = productId;
    }

    public static class ProductAdded extends ProductEvent {
        public ProductAdded(ProductId productId) {
            super(productId);
        }
    }

    public static class ProductDiscontinued extends ProductEvent {
        public ProductDiscontinued(ProductId productId) {
            super(productId);
        }
    }
}
