package dk.cloudcreate.essentials.components.eventsourced.aggregates;

import java.lang.annotation.*;

/**
 * Methods annotated with this Annotation will automatically be called when an event is being applied or rehydrated on to an {@link Aggregate} instance
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EventHandler {
}