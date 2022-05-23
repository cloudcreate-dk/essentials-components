package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence;

import dk.cloudcreate.essentials.components.common.transaction.UnitOfWork;
import dk.cloudcreate.essentials.components.common.types.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.EventOrder;

import java.util.*;

/**
 * This mapper is used by the {@link AggregateEventStreamPersistenceStrategy} to convert from any type of Event to a {@link PersistableEvent}
 * which is the type of Event the {@link AggregateEventStreamPersistenceStrategy} understands how to persist.<br>
 * This mapper is also responsible for enriching the {@link PersistableEvent} with additional metadata, such as {@link CorrelationId}, {@link PersistableEvent#causedByEventId()}, {@link Tenant} and
 * {@link EventMetaData}
 */
public interface PersistableEventMapper {
    /**
     * Convert a Java object Event into a PersistableEvent.
     *
     * @param aggregateId                the aggregate id (as provided to {@link EventStore#appendToStream(AggregateType, Object, Optional, List)}/{@link AggregateEventStreamPersistenceStrategy#persist(UnitOfWork, AggregateTypeConfiguration, Object, Optional, List)}
     * @param aggregateTypeConfiguration the configuration for the {@link AggregateEventStream} the events related to this aggregate instance will be appended to
     * @param event                      the raw Java event
     * @param eventOrder                 the order of the event
     * @return the {@link PersistableEvent} which is the precursor to the {@link PersistedEvent}.<br>
     * A {@link PersistableEvent} contains additional metadata, such as {@link CorrelationId}, {@link PersistableEvent#causedByEventId()}, {@link Tenant},
     * and {@link EventMetaData}
     */
    PersistableEvent map(Object aggregateId,
                         AggregateTypeConfiguration aggregateTypeConfiguration,
                         Object event,
                         EventOrder eventOrder);
}
