package dk.cloudcreate.essentials.components.eventsourced.aggregates.classic;

import dk.cloudcreate.essentials.types.CharSequenceType;

import java.util.UUID;

public class OrderId extends CharSequenceType<OrderId> {

    protected OrderId(CharSequence value) {
        super(value);
    }

    public static OrderId random() {
        return new OrderId(UUID.randomUUID().toString());
    }

    public static OrderId of(CharSequence id) {
        return new OrderId(id);
    }
}