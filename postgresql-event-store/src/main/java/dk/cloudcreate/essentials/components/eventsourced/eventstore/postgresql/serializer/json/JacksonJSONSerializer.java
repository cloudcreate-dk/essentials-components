package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.EventMetaData;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventType;
import dk.cloudcreate.essentials.shared.reflection.Classes;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

public class JacksonJSONSerializer implements JSONSerializer {
    private final ObjectMapper objectMapper;

    public JacksonJSONSerializer(ObjectMapper objectMapper) {
        this.objectMapper = requireNonNull(objectMapper, "No object mapper instance provided");
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T deserialize(String json, String javaType) {
        return deserialize(json,
                           (Class<T>) Classes.forName(requireNonNull(javaType, "No javaType provided")));
    }

    @Override
    public <T> T deserialize(String json, Class<T> javaType) {
        requireNonNull(json, "No json provided");
        requireNonNull(javaType, "No javaType provided");
        try {
            return objectMapper.readValue(json, javaType);
        } catch (JsonProcessingException e) {
            throw new JSONDeserializationException(msg("Failed to deserialize JSON to {}", javaType.getName()),
                                                   e);
        }
    }

    @Override
    public EventJSON serializeEvent(Object objectToSerialize) {
        requireNonNull(objectToSerialize, "No objectToSerialize provided");
        try {
            return new EventJSON(this,
                                 objectToSerialize,
                                 EventType.of(objectToSerialize.getClass()),
                                 objectMapper.writeValueAsString(objectToSerialize));
        } catch (JsonProcessingException e) {
            throw new JSONSerializationException(msg("Failed to serialize {} to JSON", objectToSerialize.getClass().getName()),
                                                 e);
        }
    }

    @Override
    public EventMetaDataJSON serializeMetaData(EventMetaData metaData) {
        requireNonNull(metaData, "No metaData provided");
        try {
            return new EventMetaDataJSON(this,
                                         metaData,
                                         metaData.getClass().getName(),
                                         objectMapper.writeValueAsString(metaData));
        } catch (JsonProcessingException e) {
            throw new JSONSerializationException(msg("Failed to serialize {} to JSON", metaData.getClass().getName()),
                                                 e);
        }
    }
}
