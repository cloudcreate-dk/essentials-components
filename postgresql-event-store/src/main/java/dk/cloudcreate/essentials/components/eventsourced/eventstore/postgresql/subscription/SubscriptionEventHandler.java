package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.PersistedEvent;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json.EventJSON;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventType;

import java.lang.annotation.*;
import java.util.Optional;

/**
 * Methods annotated with this Annotation will automatically be called when a {@link PatternMatchingPersistedEventHandler} and {@link PatternMatchingTransactionalPersistedEventHandler}
 * receives a {@link PersistedEvent} with a {@link PersistedEvent#event()}, where the {@link EventJSON#getEventType()} {@link Optional#isPresent()}
 * and the {@link EventType#toJavaClass()} matches the type of the first argument/parameter on a method annotated with {@literal @SubscriptionEventHandler}
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SubscriptionEventHandler {
}