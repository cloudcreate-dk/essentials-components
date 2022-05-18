package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.PersistedEvent;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json.JSONDeserializationException;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventType;
import dk.cloudcreate.essentials.shared.reflection.invocation.*;

import java.lang.reflect.Method;

import static dk.cloudcreate.essentials.shared.FailFast.*;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

public abstract class PatternMatchingPersistedEventHandler implements PersistedEventHandler {
    private final PatternMatchingMethodInvoker<Object> invoker;

    public PatternMatchingPersistedEventHandler() {
        invoker = new PatternMatchingMethodInvoker<>(this,
                                                     new MethodPatternMatcher<>() {

                                                         @Override
                                                         public boolean isInvokableMethod(Method method) {
                                                             requireNonNull(method, "No candidate method supplied");
                                                             var isCandidate = method.isAnnotationPresent(SubscriptionEventHandler.class) &&
                                                                     method.getParameterCount() >= 1 && method.getParameterCount() <= 2;
                                                             if (method.getParameterCount() == 2) {
                                                                 // Check that the 2nd parameter is a PersistedEvent, otherwise it's not supported
                                                                 return PersistedEvent.class.equals(method.getParameterTypes()[1]);
                                                             }
                                                             return isCandidate;

                                                         }

                                                         @Override
                                                         public Class<?> resolveInvocationArgumentTypeFromMethodDefinition(Method method) {
                                                             requireNonNull(method, "No method supplied");
                                                             return method.getParameterTypes()[0];
                                                         }

                                                         @Override
                                                         public Class<?> resolveInvocationArgumentTypeFromObject(Object argument) {
                                                             requireNonNull(argument, "No argument supplied");
                                                             requireMustBeInstanceOf(argument, PersistedEvent.class);
                                                             var persistedEvent = (PersistedEvent) argument;

                                                             if (persistedEvent.event().getEventType().isPresent()) {
                                                                 return persistedEvent.event().getEventType()
                                                                                      .map(EventType::toJavaClass)
                                                                                      .get();
                                                             } else {
                                                                 // In case it was a named Event, then let's return String as the lowest common denominator
                                                                 return String.class;
                                                             }
                                                         }

                                                         public void invokeMethod(Method methodToInvoke, Object argument, Object invokeMethodOn, Class<?> resolvedInvokeMethodWithArgumentOfType) throws Exception {
                                                             requireNonNull(methodToInvoke, "No methodToInvoke supplied");
                                                             requireNonNull(argument, "No argument supplied");
                                                             requireMustBeInstanceOf(argument, PersistedEvent.class);
                                                             requireNonNull(invokeMethodOn, "No invokeMethodOn supplied");
                                                             requireNonNull(resolvedInvokeMethodWithArgumentOfType, "No resolvedInvokeMethodWithArgumentOfType supplied");

                                                             var    persistedEvent = (PersistedEvent) argument;
                                                             Object firstParameter;
                                                             if (persistedEvent.event().getEventType().isPresent()) {
                                                                 firstParameter = persistedEvent.event().getJsonDeserialized()
                                                                                                .orElseThrow(() -> new JSONDeserializationException(msg("No JSON Deserialized payload available for PersistedEvent with eventId: {}, globalOrder: {} and eventType: {}",
                                                                                                                                                        persistedEvent.eventId(),
                                                                                                                                                        persistedEvent.globalEventOrder(),
                                                                                                                                                        persistedEvent.event().getEventTypeOrName().getValue())));

                                                             } else {
                                                                 firstParameter = persistedEvent.event().getJson();
                                                             }

                                                             if (methodToInvoke.getParameterCount() == 1) {
                                                                 methodToInvoke.invoke(invokeMethodOn, firstParameter);
                                                             } else {
                                                                 methodToInvoke.invoke(invokeMethodOn, firstParameter, persistedEvent);
                                                             }
                                                         }
                                                     },
                                                     InvocationStrategy.InvokeMostSpecificTypeMatched);
    }

    public void handle(PersistedEvent event) {
        invoker.invoke(event, unmatchedEvent -> {
            throw new IllegalArgumentException(msg("Unmatched PersistedEvent with eventId: {}, globalOrder: {}, eventType: {}, aggregateId: {}, eventOrder: {}",
                                                   event.eventId(),
                                                   event.globalEventOrder(),
                                                   event.event().getEventTypeOrName().getValue(),
                                                   event.aggregateId(),
                                                   event.eventOrder()));
        });
    }
}