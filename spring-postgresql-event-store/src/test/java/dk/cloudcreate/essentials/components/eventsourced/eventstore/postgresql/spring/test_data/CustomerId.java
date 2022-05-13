package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.spring.test_data;

import dk.cloudcreate.essentials.types.CharSequenceType;

import java.util.UUID;

public class CustomerId extends CharSequenceType<CustomerId> {

    protected CustomerId(CharSequence value) {
        super(value);
    }

    public static CustomerId random() {
        return new CustomerId(UUID.randomUUID().toString());
    }

    public static CustomerId of(CharSequence id) {
        return new CustomerId(id);
    }
}