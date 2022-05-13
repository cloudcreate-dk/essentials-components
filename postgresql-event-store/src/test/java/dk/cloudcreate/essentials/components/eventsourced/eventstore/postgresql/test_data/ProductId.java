package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.test_data;

import dk.cloudcreate.essentials.types.CharSequenceType;

import java.util.UUID;

public class ProductId extends CharSequenceType<ProductId> {

    protected ProductId(CharSequence value) {
        super(value);
    }

    public static ProductId random() {
        return new ProductId(UUID.randomUUID().toString());
    }

    public static ProductId of(CharSequence id) {
        return new ProductId(id);
    }
}