package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventTypeTest {
    @Test
    void test_creating_a_EventType_instance_from_a_String_value() {
        // Given
        var fqcn = EventJSON.class.getName();
        // When
        var eventJavaType = EventType.of(fqcn);
        // Then
        assertThat(eventJavaType.toString()).isEqualTo(EventType.FQCN_PREFIX + fqcn);
        assertThat(eventJavaType.getJavaTypeName()).isEqualTo(fqcn);
        assertThat(eventJavaType.toJavaClass()).isEqualTo(EventJSON.class);
    }

    @Test
    void test_creating_a_EventType_instance_from_a_Class_value() {
        // Given
        var type = EventJSON.class;
        var fqcn = type.getName();
        // When
        var eventJavaType = EventType.of(type);
        // Then
        assertThat(eventJavaType.toString()).isEqualTo(EventType.FQCN_PREFIX + fqcn);
        assertThat(eventJavaType.getJavaTypeName()).isEqualTo(fqcn);
        assertThat(eventJavaType.toJavaClass()).isEqualTo(type);
    }

    @Test
    void test_creating_a_EventType_instance_from_a_serialized_String_value_that_starts_with_fqcn_prefix() {
        // Given
        var fqcn = EventJSON.class.getName();
        var eventJavaType = EventType.of(fqcn);
        assertThat(eventJavaType.toString()).isEqualTo(EventType.FQCN_PREFIX + fqcn);
        // When
        var eventJavaTypeFromSerializedValue = EventType.of(eventJavaType.toString());
        // Then
        assertThat(eventJavaTypeFromSerializedValue.toString()).isEqualTo(EventType.FQCN_PREFIX + fqcn);
        assertThat(eventJavaTypeFromSerializedValue.getJavaTypeName()).isEqualTo(fqcn);
        assertThat(eventJavaTypeFromSerializedValue.toJavaClass()).isEqualTo(EventJSON.class);
        assertThat(eventJavaTypeFromSerializedValue.equals(eventJavaType)).isTrue();
        assertThat((CharSequence) eventJavaTypeFromSerializedValue).isEqualTo(eventJavaType);
    }

    @Test
    void test_two_different_EventTypes_are_not_equal() {
        // Given
        var eventJavaType = EventType.of(EventJSON.class);
        // When
        var otherEventJavaType = EventType.of(EventMetaDataJSON.class);
        // Then
        assertThat(eventJavaType.getJavaTypeName()).isNotEqualTo(otherEventJavaType.getJavaTypeName());
        assertThat(eventJavaType.toJavaClass()).isNotEqualTo(otherEventJavaType.toJavaClass());
        assertThat(eventJavaType.toString()).isNotEqualTo(otherEventJavaType.toString());
        assertThat(eventJavaType.equals(otherEventJavaType)).isFalse();
        assertThat((CharSequence) eventJavaType).isNotEqualTo(otherEventJavaType);
    }

    @Test
    void test_isSerializedEventType() {
        // Given
        var fqcn = EventJSON.class.getName();
        var eventJavaType = EventType.of(fqcn);
        // Then
        assertThat(EventType.isSerializedEventType(eventJavaType.toString())).isTrue();
        assertThat(EventType.isSerializedEventType(fqcn)).isFalse();
        assertThat(EventType.isSerializedEventType(eventJavaType.getJavaTypeName())).isFalse();
    }
}