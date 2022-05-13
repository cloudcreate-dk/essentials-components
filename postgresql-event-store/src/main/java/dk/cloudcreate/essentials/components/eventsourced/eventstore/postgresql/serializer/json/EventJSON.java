package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.PersistedEvent;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.*;
import dk.cloudcreate.essentials.types.CharSequenceType;

import java.util.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * JSON Serialized payload, used to Serialize {@link PersistedEvent} {@link PersistedEvent#metaData()} and the {@link PersistedEvent#event()} payload
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class EventJSON {
    private transient JSONSerializer   jsonSerializer;
    /**
     * Cache or the {@link #json} deserialized back to its {@link #eventTypeOrName} form
     */
    private transient Optional<Object> jsonDeserialized;
    private final     EventTypeOrName  eventTypeOrName;
    private final     String           json;

    public EventJSON(JSONSerializer jsonSerializer, Object jsonDeserialized, EventType eventType, String json) {
        this(jsonSerializer, eventType, json);
        this.jsonDeserialized = Optional.of(requireNonNull(jsonDeserialized, "No payload provided"));
    }

    public EventJSON(JSONSerializer jsonSerializer, EventType eventType, String json) {
        this.jsonSerializer = requireNonNull(jsonSerializer, "No JSON serializer provided");
        this.eventTypeOrName = EventTypeOrName.with(eventType);
        this.json = json;
    }

    public EventJSON(JSONSerializer jsonSerializer, Object jsonDeserialized, EventName eventName, String json) {
        this(jsonSerializer, eventName, json);
        this.jsonDeserialized = Optional.of(requireNonNull(jsonDeserialized, "No payload provided"));
    }

    public EventJSON(JSONSerializer jsonSerializer, EventName eventName, String json) {
        this.jsonSerializer = requireNonNull(jsonSerializer, "No JSON serializer provided");
        this.eventTypeOrName = EventTypeOrName.with(eventName);
        this.json = json;
    }

    /**
     * The {@link #getJson()} deserialized to the corresponding {@link #getEventType()} that
     * was serialized into the {@link #getJson()}<br>
     * It's optional to specify a corresponding Java type, in which case {@link #getJsonDeserialized()}
     * will return {@link Optional#empty()}
     *
     * @return The {@link #getJson()} deserialized to the corresponding {@link #getEventType()}
     */
    @SuppressWarnings({"OptionalAssignedToNull", "unchecked"})
    public <T> Optional<T> getJsonDeserialized() {
        if (jsonDeserialized == null && jsonSerializer != null) {
            eventTypeOrName.ifHasEventType(eventJavaType -> jsonDeserialized = Optional.of(jsonSerializer.deserialize(json, eventJavaType.toJavaClass())));
        }
        return (Optional<T>) jsonDeserialized;
    }

    /**
     * Variant of {@link #getJsonDeserialized()} that will throw an {@link JSONDeserializationException} in case the
     * event payload cannot be serialized
     *
     * @param <T> the java type the event payload will be cast to
     * @return the deserialized event payload
     */
    @SuppressWarnings("unchecked")
    public <T> T deserialize() {
        return (T) getJsonDeserialized().orElseThrow(() -> new JSONDeserializationException(msg("Couldn't deserialize '{}' due to: {}",
                                                                                                eventTypeOrName,
                                                                                                jsonSerializer == null ? "No JSONSerializer specified" : "No EventJavaType specified")));
    }

    /**
     * The corresponding Java type (i.e. fully qualified class name) that
     * was serialized into the {@link #getJson()}<br>
     * It's optional to specify a corresponding Java type, in which case {@link #getJsonDeserialized()}
     * will return {@link Optional#empty()}
     *
     * @return The corresponding Java type (i.e. fully qualified class name) that
     * was serialized into the {@link #getJson()}
     */
    public String getEventTypeOrNamePersistenceValue() {
        try {
            return eventTypeOrName.getEventType().map(CharSequenceType::toString)
                                  .orElseGet(() -> eventTypeOrName.getEventName().get().toString());
        } catch (Exception e) {
            throw new IllegalStateException(msg("Failed to resolve eventTypeOrNamePersistenceValue for {}", eventTypeOrName), e);
        }
    }

    public Optional<EventType> getEventType() {
        return eventTypeOrName.getEventType();
    }

    public Optional<EventName> getEventName() {
        return eventTypeOrName.getEventName();
    }

    public EventTypeOrName getEventTypeOrName() {
        return eventTypeOrName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EventJSON)) return false;
        EventJSON eventJSON = (EventJSON) o;
        return eventTypeOrName.equals(eventJSON.eventTypeOrName) && Objects.equals(json, eventJSON.json);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventTypeOrName, json);
    }

    /**
     * The raw serialized JSON
     *
     * @return The raw serialized JSON
     */
    public String getJson() {
        return json;
    }

    @Override
    public String toString() {
        return "EventJSON{" +
                "eventTypeOrName=" + eventTypeOrName +
                '}';
    }
}
