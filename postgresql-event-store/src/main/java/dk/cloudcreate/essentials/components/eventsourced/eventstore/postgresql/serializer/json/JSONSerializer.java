package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.EventMetaData;

/**
 * JSON serializer and deserializer
 */
public interface JSONSerializer {
    /**
     * Deserialize the payload in the <code>json</code> parameter into the Java type specified by the Fully Qualified Class Name contained
     * in the <code>javaType</code> parameter
     *
     * @param json     the json payload
     * @param javaType the Fully Qualified Class Name for the Java type that the json payload should be deserialized into
     * @param <T>      the corresponding Java type
     * @return the deserialized json payload
     * @throws JSONDeserializationException in case the json couldn't be deserialized to the specified java type
     */
    <T> T deserialize(String json, String javaType);

    /**
     * Deserialize the payload in the <code>json</code> parameter into the Java type specified by the <code>javaType</code> parameter
     *
     * @param json     the json payload
     * @param javaType the Java type that the json payload should be deserialized into
     * @param <T>      the corresponding Java type
     * @return the deserialized json payload
     * @throws JSONDeserializationException in case the json couldn't be deserialized to the specified java type
     */
    <T> T deserialize(String json, Class<T> javaType);

    /**
     * Serialize a java object to {@link EventJSON}
     *
     * @param objectToSerialize the java object that will be serialized to JSON
     * @return the corresponding {@link EventJSON} object
     * @throws JSONSerializationException in case the <code>objectToSerialize</code> couldn't be serialized to JSON
     */
    EventJSON serializeEvent(Object objectToSerialize);

    /**
     * Serialize the {@link EventMetaData} object to {@link EventJSON}
     *
     * @param metaData the {@link EventMetaData}  object that will be serialized to JSON
     * @return the corresponding {@link EventMetaDataJSON} object
     * @throws JSONSerializationException in case the <code>objectToSerialize</code> couldn't be serialized to JSON
     */
    EventMetaDataJSON serializeMetaData(EventMetaData metaData);
}
