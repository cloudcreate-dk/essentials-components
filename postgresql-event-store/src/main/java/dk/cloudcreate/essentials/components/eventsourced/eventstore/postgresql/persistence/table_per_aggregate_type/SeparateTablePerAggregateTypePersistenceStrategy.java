package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type;

import dk.cloudcreate.essentials.components.common.types.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStoreException;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.jdbi.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.json.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.*;
import dk.cloudcreate.essentials.shared.Exceptions;
import dk.cloudcreate.essentials.shared.collections.Streams;
import dk.cloudcreate.essentials.types.LongRange;
import org.jdbi.v3.core.*;
import org.jdbi.v3.core.result.*;
import org.jdbi.v3.core.statement.*;
import org.slf4j.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.*;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;
import static dk.cloudcreate.essentials.shared.MessageFormatter.NamedArgumentBinding.arg;
import static dk.cloudcreate.essentials.shared.MessageFormatter.*;

/**
 * This strategy uses a separate table for a given {@link AggregateType}. An aggregate's {@link AggregateEventStream} instance is always
 * associated with a given {@link AggregateType}, which means that multiple instances of a given Aggregate type can be persisted into
 * the same EventStream table. The individual aggregate {@link AggregateEventStream}'s are separated by the {@link AggregateEventStream#aggregateId()}.<br>
 * This strategy uses the concept of a {@link PersistedEvent#globalEventOrder()} to keep track of the order of when individual events were added
 * to the EventStream table, across all aggregate {@link AggregateEventStream} instances that all share the same EventStream table.<br>
 * <br>
 * This strategy is designed with the with an {@link EventStreamTableColumnNames} provided to {@link AggregateTypeConfiguration}<br>
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class SeparateTablePerAggregateTypePersistenceStrategy implements AggregateEventStreamPersistenceStrategy<SeparateTablePerAggregateTypeConfiguration> {
    private static final Logger log = LoggerFactory.getLogger(SeparateTablePerAggregateTypePersistenceStrategy.class);

    /**
     * Key: {@link AggregateType}<br>
     * Value: The insert SQL for the event stream table the event stream is persisted to
     */
    private final ConcurrentMap<AggregateType, String>                                     insertSql                         = new ConcurrentHashMap<>();
    /**
     * Key: {@link AggregateType}<br>
     * Value: The Query SQL for the event stream table the aggregate's events are persisted to
     */
    private final ConcurrentMap<AggregateType, String>                                     lastPersistedEventForAggregateSql = new ConcurrentHashMap<>();
    private final ConcurrentMap<AggregateType, SeparateTablePerAggregateTypeConfiguration> aggregateTypeConfigurations       = new ConcurrentHashMap<>();
    private final EventStoreUnitOfWorkFactory                                              unitOfWorkFactory;
    private final PersistableEventMapper                                                   eventMapper;
    private final Optional<PostgresqlEventStreamListener>                                  postgresEventStreamListener;
    private final Jdbi                                                                     jdbi;

    /**
     * Create a new {@link SeparateTablePerAggregateTypePersistenceStrategy} using the specified {@link PersistableEventMapper}
     *
     * @param eventMapper the mapper that will convert from the raw events to the {@link PersistableEvent} that the {@link SeparateTablePerAggregateTypePersistenceStrategy}
     *                    can persist and convert into a {@link PersistedEvent}
     */
    public SeparateTablePerAggregateTypePersistenceStrategy(Jdbi jdbi,
                                                            EventStoreUnitOfWorkFactory unitOfWorkFactory,
                                                            PersistableEventMapper eventMapper,
                                                            List<SeparateTablePerAggregateTypeConfiguration> aggregateTypeConfigurations) {
        this(jdbi,
             unitOfWorkFactory,
             eventMapper,
             aggregateTypeConfigurations,
             null);
    }

    /**
     * Create a new {@link SeparateTablePerAggregateTypePersistenceStrategy} using the specified {@link PersistableEventMapper}
     *
     * @param eventMapper the mapper that will convert from the raw events to the {@link PersistableEvent} that the {@link SeparateTablePerAggregateTypePersistenceStrategy}
     *                    can persist and convert into a {@link PersistedEvent}
     */
    public SeparateTablePerAggregateTypePersistenceStrategy(Jdbi jdbi,
                                                            EventStoreUnitOfWorkFactory unitOfWorkFactory,
                                                            PersistableEventMapper eventMapper,
                                                            SeparateTablePerAggregateTypeConfiguration... aggregateTypeConfigurations) {
        this(jdbi,
             unitOfWorkFactory,
             eventMapper,
             List.of(aggregateTypeConfigurations),
             null);
    }

    /**
     * Disabled for now until the {@link PostgresqlEventStreamListener} is complete
     *
     * @param eventMapper
     * @param postgresqlEventStreamListener
     */
    private SeparateTablePerAggregateTypePersistenceStrategy(Jdbi jdbi,
                                                             EventStoreUnitOfWorkFactory unitOfWorkFactory,
                                                             PersistableEventMapper eventMapper,
                                                             List<SeparateTablePerAggregateTypeConfiguration> aggregateTypeConfigurations,
                                                             PostgresqlEventStreamListener postgresqlEventStreamListener) {
        this.jdbi = requireNonNull(jdbi, "No jdbi instance provided");
        this.unitOfWorkFactory = requireNonNull(unitOfWorkFactory);
        this.eventMapper = requireNonNull(eventMapper, "No event mapper provided");
        this.postgresEventStreamListener = Optional.ofNullable(postgresqlEventStreamListener);

        jdbi.registerArgument(new CorrelationIdArgumentFactory());
        jdbi.registerArgument(new EventIdArgumentFactory());
        jdbi.registerArgument(new EventOrderArgumentFactory());
        jdbi.registerArgument(new GlobalEventOrderArgumentFactory());
        jdbi.registerArgument(new EventRevisionArgumentFactory());

        requireNonNull(aggregateTypeConfigurations, "No eventStreamConfigurations provided");
        aggregateTypeConfigurations.forEach(this::addAggregateTypeConfiguration);
    }

    @Override
    public SeparateTablePerAggregateTypePersistenceStrategy addAggregateTypeConfiguration(SeparateTablePerAggregateTypeConfiguration eventStreamConfiguration) {
        requireNonNull(eventStreamConfiguration, "No eventStreamConfiguration provided");
        if (!aggregateTypeConfigurations.containsKey(eventStreamConfiguration.aggregateType)) {
            aggregateTypeConfigurations.put(eventStreamConfiguration.aggregateType, eventStreamConfiguration);
            initializeEventStorageFor(eventStreamConfiguration);
        }
        return this;
    }

    private SeparateTablePerAggregateTypeConfiguration getAggregateTypeConfiguration(AggregateType aggregateType) {
        var config = aggregateTypeConfigurations.get(aggregateType);
        if (config == null) {
            throw new EventStoreException(msg("EventStream with name '{}' hasn't been configured. Please add it to the persistence strategy's configuration at initialization time or using addAggregateTypeConfiguration(config)", aggregateType));
        }
        return config;
    }

    /**
     * Configure any underlying tables as specified according
     * to the parameters in the {@link AggregateTypeConfiguration}
     *
     * @param eventStreamConfiguration the configuration for the given event stream
     */
    private void initializeEventStorageFor(SeparateTablePerAggregateTypeConfiguration eventStreamConfiguration) {
        requireNonNull(eventStreamConfiguration, "No eventStreamConfiguration provided");
        log.info("Initializing EventStream storage for aggregate-type '{}'", eventStreamConfiguration.aggregateType);
        unitOfWorkFactory.usingUnitOfWork(unitOfWork -> {
            Optional<String> eventTable = unitOfWork.handle().select("SELECT to_regclass(?)", eventStreamConfiguration.eventStreamTableName)
                                                    .mapTo(String.class)
                                                    .findOne();
            if (eventTable.isEmpty()) {
                createEventStreamTable(unitOfWork.handle(), eventStreamConfiguration);
            }
            ensureIndexes(unitOfWork.handle(), eventStreamConfiguration);
            addEventStreamPostgresqlNotification(unitOfWork.handle(), eventStreamConfiguration);
        });
        // Start listening for changes
        // TODO: Start listening
//            if (!postgresEventStreamListener.get().isStarted()) {
//                postgresEventStreamListener.get().start();
//            }
//            postgresEventStreamListener.get().listenForChangesTo(eventStreamTableName);
    }


    /**
     * Reset the EventStore for the given configuration
     *
     * @param configuration the configuration for the given event stream
     */
    public void resetEventStorageFor(SeparateTablePerAggregateTypeConfiguration configuration) {
        requireNonNull(configuration, "No configuration provided");
        log.info("Resetting EventStream storage for aggregate-type '{}'", configuration.aggregateType);
        unitOfWorkFactory.usingUnitOfWork(unitOfWork -> {
            var changes = unitOfWork.handle().execute("DROP TABLE IF EXISTS " + configuration.eventStreamTableName);
            if (changes == 1) {
                log.debug("Dropped table '{}'", configuration.eventStreamTableName);
            }
        });
        initializeEventStorageFor(configuration);
    }

    private void ensureIndexes(Handle handle, SeparateTablePerAggregateTypeConfiguration eventStreamConfiguration) {
        var eventStreamTableName = eventStreamConfiguration.eventStreamTableName;
        var columnNames          = eventStreamConfiguration.eventStreamTableColumnNames;
        var numberOfChanges = handle.createUpdate(bind("CREATE INDEX IF NOT EXISTS {:tableName}_{:tenantColumn} ON {:tableName} ({:tenantColumn})",
                                                       arg("tableName", eventStreamTableName),
                                                       arg("tenantColumn", columnNames.tenantColumn)
                                                      )
                                                 )
                                    .execute();

        log.info("[{}] '{}' index on '{}' {}",
                 eventStreamConfiguration.aggregateType,
                 eventStreamConfiguration.eventStreamTableName,
                 columnNames.tenantColumn,
                 numberOfChanges == 1 ? "created" : "already existed");
    }

    private void createEventStreamTable(Handle handle, SeparateTablePerAggregateTypeConfiguration eventStreamConfiguration) {
        var eventStreamTableName = eventStreamConfiguration.eventStreamTableName;
        var columnNames          = eventStreamConfiguration.eventStreamTableColumnNames;
        Update update = handle.createUpdate(bind("CREATE TABLE {:tableName} (\n" +
                                                         "            {:globalOrderColumn} bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,\n" +
                                                         "            {:aggregateIdColumn} {:aggregateIdColumnType} NOT NULL,\n" +
                                                         "            {:eventOrderColumn} bigint NOT NULL,\n" +
                                                         "            {:eventIdColumn} {:eventIdColumnType} NOT NULL,\n" +
                                                         "            {:causedByEventIdColumn} {:eventIdColumnType},\n" +
                                                         "            {:correlationIdColumn} {:correlationIdColumnType},\n" +
                                                         "            {:eventTypeColumn} text NOT NULL,\n" +
                                                         "            {:eventRevisionColumn} text NOT NULL,\n" +
                                                         "            {:timestampColumn} TIMESTAMP WITH TIME ZONE NOT NULL,\n" +
                                                         "            {:eventPayloadColumn} {:eventPayloadType} NOT NULL,\n" +
                                                         "            {:eventMetaDataColumn} {:eventMetaDataType} NOT NULL,\n" +
                                                         "            {:tenantColumn} text,\n" +
                                                         "          UNIQUE ({:aggregateIdColumn}, {:eventOrderColumn}),\n" +
                                                         "          UNIQUE ({:eventIdColumn})\n" +
                                                         "        )",
                                                 arg("tableName", eventStreamTableName),
                                                 arg("globalOrderColumn", columnNames.globalOrderColumn),
                                                 arg("aggregateIdColumn", columnNames.aggregateIdColumn),
                                                 arg("aggregateIdColumnType", eventStreamConfiguration.aggregateIdColumnType),
                                                 arg("eventOrderColumn", columnNames.eventOrderColumn),
                                                 arg("eventIdColumn", columnNames.eventIdColumn),
                                                 arg("eventIdColumnType", eventStreamConfiguration.eventIdColumnType),
                                                 arg("causedByEventIdColumn", columnNames.causedByEventIdColumn),
                                                 arg("correlationIdColumn", columnNames.correlationIdColumn),
                                                 arg("correlationIdColumnType", eventStreamConfiguration.correlationIdColumnType),
                                                 arg("eventTypeColumn", columnNames.eventTypeColumn),
                                                 arg("eventRevisionColumn", columnNames.eventRevisionColumn),
                                                 arg("timestampColumn", columnNames.timestampColumn),
                                                 arg("eventPayloadColumn", columnNames.eventPayloadColumn),
                                                 arg("eventPayloadType", eventStreamConfiguration.eventJsonColumnType),
                                                 arg("eventMetaDataColumn", columnNames.eventMetaDataColumn),
                                                 arg("eventMetaDataType", eventStreamConfiguration.eventMetadataJsonColumnType),
                                                 arg("tenantColumn", columnNames.tenantColumn)
                                                )
                                           );

        beforeCreateEventStreamTableCreation(update, handle);
        log.info("[{}] Creating event-stream table '{}'", eventStreamConfiguration.aggregateType, eventStreamConfiguration.eventStreamTableName);
        int numberOfChanges = update.execute();
        afterCreateEventStreamTableCreation(numberOfChanges, update, handle);
    }

    private void addEventStreamPostgresqlNotification(Handle handle, SeparateTablePerAggregateTypeConfiguration eventStreamConfiguration) {
        var eventStreamTableName = eventStreamConfiguration.eventStreamTableName;
        var columnNames          = eventStreamConfiguration.eventStreamTableColumnNames;

        if (postgresEventStreamListener.isPresent()) {
            var update = handle.createUpdate((bind("CREATE OR REPLACE FUNCTION notify_{:tableName}_change()\n" +
                                                           "        RETURNS trigger\n" +
                                                           "        LANGUAGE PLPGSQL\n" +
                                                           "       AS $$\n" +
                                                           "       BEGIN\n" +
                                                           "         PERFORM (\n" +
                                                           "            WITH payload(table_name,\n" +
                                                           "                         {:aggregateIdColumnName},\n" +
                                                           "                         {:eventTypeColumnName},\n" +
                                                           "                         {:eventOrderColumnName},\n" +
                                                           "                         {:globalOrderColumnName},\n" +
                                                           "                         {:timestampColumnName},\n" +
                                                           "                         {:tenantColumnName}) as\n" +
                                                           "            (\n" +
                                                           "              SELECT '{:tableName}',\n" +
                                                           "                     NEW.{:aggregateIdColumnName},\n" +
                                                           "                     NEW.{:eventTypeColumnName},\n" +
                                                           "                     NEW.{:eventOrderColumnName},\n" +
                                                           "                     NEW.{:globalOrderColumnName},\n" +
                                                           "                     NEW.{:timestampColumnName},\n" +
                                                           "                     NEW.{:tenantColumnName}\n" +
                                                           "            )\n" +
                                                           "            SELECT pg_notify('{:tableName}', row_to_json(payload)::text)\n" +
                                                           "              FROM payload\n" +
                                                           "         );\n" +
                                                           "         RETURN NULL;\n" +
                                                           "       END;\n" +
                                                           "       $$;",
                                                   arg("tableName", eventStreamTableName),
                                                   arg("aggregateIdColumnName", columnNames.aggregateIdColumn),
                                                   arg("eventTypeColumnName", columnNames.eventTypeColumn),
                                                   arg("eventOrderColumnName", columnNames.eventOrderColumn),
                                                   arg("globalOrderColumnName", columnNames.globalOrderColumn),
                                                   arg("timestampColumnName", columnNames.timestampColumn),
                                                   arg("tenantColumnName", columnNames.tenantColumn))
                                             ));
            beforeEventStreamTableNotificationFunctionCreation(update, handle);
            var numberOfChanges = update.execute();
            log.info("[{}] {} event-stream Notification Function 'notify_{}_change' for table '{}'",
                     eventStreamConfiguration.aggregateType,
                     numberOfChanges == 1 ? "Created" : "Replaced",
                     eventStreamTableName,
                     eventStreamTableName);
            afterEventStreamTableNotificationFunctionCreation(numberOfChanges, update, handle);


            update = handle.createUpdate(bind("CREATE OR REPLACE TRIGGER notify_on_{:tableName}_changes\n" +
                                                      "      AFTER INSERT\n" +
                                                      "            ON {:tableName}\n" +
                                                      "      FOR EACH ROW\n" +
                                                      "         EXECUTE PROCEDURE notify_{:tableName}_change()",
                                              arg("tableName", eventStreamTableName)
                                             ));
            beforeEventStreamTableNotificationTriggerCreation(update, handle);
            numberOfChanges = update.execute();
            log.info("[{}] {} event-stream Notification Trigger 'notify_on_{}_changes' for table '{}'",
                     eventStreamConfiguration.aggregateType,
                     numberOfChanges == 1 ? "Created" : "Replaced",
                     eventStreamTableName,
                     eventStreamTableName);
            afterEventStreamTableNotificationTriggerCreation(numberOfChanges, update, handle);
        }
    }

    protected void afterEventStreamTableNotificationTriggerCreation(int numberOfChanges, Update update, Handle handle) {

    }

    protected void beforeEventStreamTableNotificationTriggerCreation(Update update, Handle handle) {

    }

    protected void afterEventStreamTableNotificationFunctionCreation(int numberOfChanges, Update update, Handle handle) {

    }

    protected void beforeEventStreamTableNotificationFunctionCreation(Update update, Handle handle) {

    }

    protected void afterCreateEventStreamTableCreation(int numberOfChanges, Update update, Handle handle) {

    }

    protected void beforeCreateEventStreamTableCreation(Update update, Handle handle) {

    }

    @Override
    public <STREAM_ID> AggregateEventStream<STREAM_ID> persist(EventStoreUnitOfWork unitOfWork,
                                                               AggregateType aggregateType,
                                                               STREAM_ID aggregateId,
                                                               Optional<Long> appendEventsAfterEventOrder,
                                                               List<?> persistableEvents) {
        requireNonNull(unitOfWork, "No unitOfWork provided");
        requireNonNull(aggregateType, "No aggregateType provided");
        requireNonNull(aggregateId, "No aggregateId provided");
        requireNonNull(appendEventsAfterEventOrder, "No appendEventsAfterEventOrder provided");
        requireNonNull(persistableEvents, "No persistableEvents provided");

        var configuration = getAggregateTypeConfiguration(aggregateType);
        if (persistableEvents.isEmpty()) {
            return AggregateEventStream.of(configuration,
                                           aggregateId,
                                           LongRange.only(EventOrder.NO_EVENTS_PERSISTED.longValue()),
                                           Stream.empty());
        }

        var batch = unitOfWork.handle()
                              .prepareBatch(getInsertSql(configuration));

        var eventOrder = new AtomicLong(appendEventsAfterEventOrder.orElseGet(() -> loadLastPersistedEventRelatedTo(unitOfWork,
                                                                                                                    aggregateType,
                                                                                                                    aggregateId)
                .map(PersistedEvent::eventOrder)
                .orElse(EventOrder.NO_EVENTS_PERSISTED)
                .longValue()));
        var initialEventOrder = eventOrder.get();
        var jdbiPersistableEvents = persistableEvents.stream()
                                                     .map(rawPersistableEvent -> eventMapper.map(aggregateId, configuration, rawPersistableEvent, EventOrder.of(eventOrder.incrementAndGet())))
                                                     .map(persistableEvent -> addEventToPersistenceBatch(configuration, batch, persistableEvent))
                                                     .collect(Collectors.toList());

        final ResultBearing result = batch.executeAndReturnGeneratedKeys(configuration.eventStreamTableColumnNames.globalOrderColumn);
        try {
            var eventGlobalOrders = result.reduceRows(new ArrayList<Long>(),
                                                      (listOfGlobalOrders, row) -> {
                                                          listOfGlobalOrders.add(row.getColumn(configuration.eventStreamTableColumnNames.globalOrderColumn, Long.class));
                                                          return listOfGlobalOrders;
                                                      }).stream();

            var persistedEvents = Streams.zipOrderedAndEqualSizedStreams(eventGlobalOrders, jdbiPersistableEvents.stream(), (eventGlobalOrder, jdbiPersistableEvent) -> PersistedEvent.from(jdbiPersistableEvent.persistableEvent,
                                                                                                                                                                                            configuration.aggregateType,
                                                                                                                                                                                            GlobalEventOrder.of(eventGlobalOrder),
                                                                                                                                                                                            jdbiPersistableEvent.serializedEvent,
                                                                                                                                                                                            jdbiPersistableEvent.serializedEventMetaData,
                                                                                                                                                                                            jdbiPersistableEvent.eventTimestamp));
            var fromInclusive = eventOrder.longValue() - persistableEvents.size() + 1;
            return AggregateEventStream.of(configuration,
                                           aggregateId,
                                           LongRange.between(fromInclusive,
                                                             eventOrder.longValue()),
                                           persistedEvents);
        } catch (RuntimeException e) {
            var cause = Exceptions.getRootCause(e);
            if (cause.getMessage().contains("ERROR: duplicate key value violates unique constraint") && cause.getMessage().contains("aggregate_id_event_order_key")) {
                throw new OptimisticAppendToStreamException(msg("[{}] Optimistic Concurrency Exception Failed to Append {} Events to Stream related to aggregate with id '{}'. " +
                                                                        "First event was appended with eventOrder {}. Details: {}",
                                                                configuration.aggregateType,
                                                                persistableEvents.size(),
                                                                aggregateId,
                                                                initialEventOrder + 1,
                                                                cause.getMessage()), e);
            } else {
                throw new AppendToStreamException(msg("[{}] Failed to Append {} Events to Stream related to aggregate with id '{}'",
                                                      configuration.aggregateType,
                                                      persistableEvents.size(),
                                                      aggregateId), e);
            }
        }
    }

    @Override
    public <STREAM_ID> Optional<PersistedEvent> loadLastPersistedEventRelatedTo(EventStoreUnitOfWork unitOfWork, AggregateType aggregateType, STREAM_ID aggregateId) {
        requireNonNull(unitOfWork, "No unitOfWork provided");
        requireNonNull(aggregateType, "No aggregateType provided");
        requireNonNull(aggregateId, "No aggregateId provided");

        var configuration = getAggregateTypeConfiguration(aggregateType);
        var query = unitOfWork.handle()
                              .createQuery(getLastPersistedEventRelatedToAggregateSQL(configuration));
        var lastPersistedEvent = query.bind("aggregateId", configuration.aggregateIdColumnType == IdentifierColumnType.UUID ? UUID.fromString(configuration.aggregateIdSerializer.serialize(aggregateId)) : configuration.aggregateIdSerializer.serialize(aggregateId))
                                      .setFetchSize(1)
                                      .registerRowMapper(PersistedEvent.class, new PersistedEventRowMapper(this, configuration))
                                      .mapTo(PersistedEvent.class)
                                      .findOne();
        log.debug("[{}] Found Last-Persisted-Event for Aggregate with id '{}': {}", configuration.aggregateType, aggregateId, lastPersistedEvent);
        return lastPersistedEvent;
    }

    private String getLastPersistedEventRelatedToAggregateSQL(SeparateTablePerAggregateTypeConfiguration configuration) {
        return lastPersistedEventForAggregateSql.computeIfAbsent(configuration.aggregateType, aggregateType -> {
            var sqlTemplate = "SELECT * FROM {:tableName} WHERE \n" +
                    "   {:aggregateIdColumn} = :aggregateId \n" +
                    "   ORDER BY {:eventOrderColumn} DESC LIMIT 1";
            return bind(sqlTemplate,
                        // Column names
                        arg("tableName", configuration.eventStreamTableName),
                        arg("aggregateIdColumn", configuration.eventStreamTableColumnNames.aggregateIdColumn),
                        arg("eventOrderColumn", configuration.eventStreamTableColumnNames.eventOrderColumn));
        });
    }

    @SuppressWarnings("unchecked")
    private JdbiPersistableEventWrapper addEventToPersistenceBatch(SeparateTablePerAggregateTypeConfiguration configuration, PreparedBatch batch, PersistableEvent persistableEvent) {
        var serializedEvent         = configuration.jsonSerializer.serializeEvent(persistableEvent.event());
        var serializedEventMetaData = configuration.jsonSerializer.serializeMetaData(persistableEvent.metaData());

        var timestamp = persistableEvent.timestamp()
                                        .orElseGet(OffsetDateTime::now)
                                        .withOffsetSameInstant(ZoneOffset.UTC);

        Object correlationId   = null;
        Object causedByEventId = null;
        if (persistableEvent.causedByEventId()
                            .isPresent()) {
            if (configuration.eventIdColumnType == IdentifierColumnType.UUID) {
                causedByEventId = UUID.fromString(persistableEvent.causedByEventId()
                                                                  .get()
                                                                  .toString());
            } else {
                causedByEventId = persistableEvent.causedByEventId()
                                                  .get();
            }
        }

        if (persistableEvent.correlationId()
                            .isPresent()) {
            if (configuration.correlationIdColumnType == IdentifierColumnType.UUID) {
                correlationId = UUID.fromString(persistableEvent.correlationId()
                                                                .get()
                                                                .toString());
            } else {
                correlationId = persistableEvent.correlationId()
                                                .get();
            }
        }

        batch.bind("aggregateId",
                   configuration.aggregateIdColumnType == IdentifierColumnType.UUID ?
                   UUID.fromString(configuration.aggregateIdSerializer.serialize(persistableEvent.aggregateId())) :
                   configuration.aggregateIdSerializer.serialize(persistableEvent.aggregateId()))
             .bind("eventOrder", persistableEvent.eventOrder())
             .bind("eventId", configuration.eventIdColumnType == IdentifierColumnType.UUID ?
                              UUID.fromString(persistableEvent.eventId().toString()) :
                              persistableEvent.eventId().toString())
             .bind("causedByEventId", causedByEventId)
             .bind("correlationId", correlationId)
             .bind("eventType", serializedEvent.getEventTypeOrNamePersistenceValue())
             .bind("eventRevision", persistableEvent.eventRevision())
             .bind("timestamp", timestamp)
             .bind("eventPayload", bindEventJSONForPersistence(serializedEvent, configuration))
             .bind("eventMetaData", bindEventMetaDataJSONForPersistence(serializedEventMetaData, configuration))
             .bind("tenant", persistableEvent.tenant().map(tenant -> configuration.tenantSerializer.serialize(tenant)).orElse(null))
             .add();
        return new JdbiPersistableEventWrapper(persistableEvent, timestamp, serializedEvent, serializedEventMetaData);
    }

    private Object bindEventJSONForPersistence(EventJSON eventJson, AggregateTypeConfiguration configuration) {
        return eventJson.getJson();
    }

    private Object bindEventMetaDataJSONForPersistence(EventMetaDataJSON eventMetaDataJson, AggregateTypeConfiguration configuration) {
        return eventMetaDataJson.getJson();
    }

    /**
     * Placeholder for various data generated when a {@link PersistableEvent}
     * is being added to a {@link org.jdbi.v3.core.Jdbi} {@link org.jdbi.v3.core.statement.Batch}
     */
    private static class JdbiPersistableEventWrapper {
        private final PersistableEvent  persistableEvent;
        private final OffsetDateTime    eventTimestamp;
        private final EventJSON         serializedEvent;
        private final EventMetaDataJSON serializedEventMetaData;

        private JdbiPersistableEventWrapper(PersistableEvent persistableEvent,
                                            OffsetDateTime eventTimestamp,
                                            EventJSON serializedEvent,
                                            EventMetaDataJSON serializedEventMetaData) {
            this.persistableEvent = persistableEvent;
            this.eventTimestamp = eventTimestamp;
            this.serializedEvent = serializedEvent;
            this.serializedEventMetaData = serializedEventMetaData;
        }
    }

    @Override
    public <STREAM_ID> Optional<AggregateEventStream<STREAM_ID>> loadAggregateEvents(EventStoreUnitOfWork unitOfWork, AggregateType aggregateType, STREAM_ID aggregateId, LongRange eventOrderRange, Optional<Tenant> onlyIncludeEventsIfTheyBelongToTenant) {
        requireNonNull(unitOfWork, "No unitOfWork provided");
        requireNonNull(aggregateType, "No aggregateType provided");
        requireNonNull(aggregateId, "No aggregateId provided");
        requireNonNull(eventOrderRange, "No eventOrderRange provided");
        requireNonNull(onlyIncludeEventsIfTheyBelongToTenant, "No onlyIncludeEventsIfTheyBelongToTenant provided");

        var configuration = getAggregateTypeConfiguration(aggregateType);
        var query = unitOfWork.handle()
                              .createQuery(loadAggregateEventsQuerySql(configuration, eventOrderRange, onlyIncludeEventsIfTheyBelongToTenant));
        query.bind("aggregateId", configuration.aggregateIdColumnType == IdentifierColumnType.UUID ? UUID.fromString(configuration.aggregateIdSerializer.serialize(aggregateId)) : configuration.aggregateIdSerializer.serialize(aggregateId))
             .bind("eventOrderRangeFrom", eventOrderRange.fromInclusive);
        if (eventOrderRange.isClosedRange()) {
            query.bind("eventOrderRangeFrom", eventOrderRange.fromInclusive);
            query.bind("eventOrderRangeTo", eventOrderRange.toInclusive);
        }
        onlyIncludeEventsIfTheyBelongToTenant.ifPresent(tenant -> query.bind("tenant", configuration.tenantSerializer.serialize(tenant)));
        query.setFetchSize(configuration.queryFetchSize);
        query.registerRowMapper(PersistedEvent.class, new PersistedEventRowMapper(this, configuration));
        final ResultIterable<PersistedEvent> persistedEvents = query.mapTo(PersistedEvent.class);

        var eventsIterator = persistedEvents.iterator();
        if (!eventsIterator.hasNext()) {
            return Optional.empty();
        }
        return Optional.of(AggregateEventStream.of(configuration,
                                                   aggregateId,
                                                   eventOrderRange,
                                                   StreamSupport.stream(Spliterators.spliteratorUnknownSize(eventsIterator, 0),
                                                                        false)));
    }


    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    protected String loadAggregateEventsQuerySql(SeparateTablePerAggregateTypeConfiguration configuration,
                                                 LongRange eventOrderRange,
                                                 Optional<Tenant> onlyIncludeEventsIfTheyBelongToTenant) {
        String sql = "SELECT * FROM {:tableName} WHERE \n" +
                "   {:aggregateIdColumn} = :aggregateId AND\n";

        if (eventOrderRange.isClosedRange()) {
            sql += "   {:eventOrderColumn} BETWEEN :eventOrderRangeFrom AND :eventOrderRangeTo";
        } else {
            sql += "   {:eventOrderColumn} >= :eventOrderRangeFrom";
        }
        if (onlyIncludeEventsIfTheyBelongToTenant.isPresent()) {
            sql += " AND\n   ({:tenantColumn} IS NULL OR {:tenantColumn} = :tenant)";
        }
        sql += " ORDER BY {:eventOrderColumn} ASC";

        return bind(sql,
                    // Column names
                    arg("tableName", configuration.eventStreamTableName),
                    arg("aggregateIdColumn", configuration.eventStreamTableColumnNames.aggregateIdColumn),
                    arg("eventOrderColumn", configuration.eventStreamTableColumnNames.eventOrderColumn),
                    arg("tenantColumn", configuration.eventStreamTableColumnNames.tenantColumn));
    }

    @Override
    public Stream<PersistedEvent> loadEventsByGlobalOrder(EventStoreUnitOfWork unitOfWork, AggregateType aggregateType, LongRange globalOrderRange, Optional<Tenant> onlyIncludeEventsIfTheyBelongToTenant) {
        requireNonNull(unitOfWork, "No unitOfWork provided");
        requireNonNull(aggregateType, "No aggregateType provided");
        requireNonNull(globalOrderRange, "No aggregateId provided");
        requireNonNull(onlyIncludeEventsIfTheyBelongToTenant, "No onlyIncludeEventsIfTheyBelongToTenant provided");

        var configuration = getAggregateTypeConfiguration(aggregateType);
        var query = unitOfWork.handle()
                              .createQuery(loadEventsByGlobalOrderQuerySql(configuration, globalOrderRange, onlyIncludeEventsIfTheyBelongToTenant));

        query.bind("globalOrderRangeFrom", globalOrderRange.fromInclusive);
        if (globalOrderRange.isClosedRange()) {
            query.bind("globalOrderRangeFrom", globalOrderRange.fromInclusive);
            query.bind("globalOrderRangeTo", globalOrderRange.toInclusive);
        }
        onlyIncludeEventsIfTheyBelongToTenant.ifPresent(tenant -> query.bind("tenant", configuration.tenantSerializer.serialize(tenant)));
        query.setFetchSize(configuration.queryFetchSize);
        query.registerRowMapper(PersistedEvent.class, new PersistedEventRowMapper(this, configuration));
        return query.mapTo(PersistedEvent.class)
                    .stream();
    }

    @Override
    public Optional<PersistedEvent> loadEvent(EventStoreUnitOfWork unitOfWork, AggregateType aggregateType, EventId eventId) {
        requireNonNull(unitOfWork, "No unitOfWork provided");
        requireNonNull(aggregateType, "No aggregateType provided");
        requireNonNull(eventId, "No eventId provided");

        var configuration = getAggregateTypeConfiguration(aggregateType);
        return unitOfWork.handle()
                         .createQuery(loadEventQuerySql(configuration))
                         .bind("eventId", configuration.eventIdColumnType == IdentifierColumnType.UUID ? UUID.fromString(eventId.toString()) : eventId)
                         .setFetchSize(1)
                         .registerRowMapper(PersistedEvent.class, new PersistedEventRowMapper(this, configuration))
                         .mapTo(PersistedEvent.class)
                         .findOne();
    }

    protected String loadEventQuerySql(SeparateTablePerAggregateTypeConfiguration configuration) {
        String sql = "SELECT * FROM {:tableName} WHERE \n" +
                "   {:eventIdColumn} = :eventId";

        return bind(sql,
                    // Column names
                    arg("tableName", configuration.eventStreamTableName),
                    arg("eventIdColumn", configuration.eventStreamTableColumnNames.eventIdColumn)
                   );
    }

    private String loadEventsByGlobalOrderQuerySql(SeparateTablePerAggregateTypeConfiguration configuration, LongRange globalOrderRange, Optional<Tenant> onlyIncludeEventsIfTheyBelongToTenant) {
        String sql = "SELECT * FROM {:tableName} WHERE \n";

        if (globalOrderRange.isClosedRange()) {
            sql += "   {:globalOrderColumn} BETWEEN :globalOrderRangeFrom AND :globalOrderRangeTo";
        } else {
            sql += "   {:globalOrderColumn} >= :globalOrderRangeFrom";
        }
        if (onlyIncludeEventsIfTheyBelongToTenant.isPresent()) {
            sql += " AND\n   ({:tenantColumn} IS NULL OR {:tenantColumn} = :tenant)";
        }
        sql += " ORDER BY {:globalOrderColumn} ASC";
        return bind(sql,
                    // Column names
                    arg("tableName", configuration.eventStreamTableName),
                    arg("globalOrderColumn", configuration.eventStreamTableColumnNames.globalOrderColumn),
                    arg("tenantColumn", configuration.eventStreamTableColumnNames.tenantColumn));
    }

    protected String getInsertSql(SeparateTablePerAggregateTypeConfiguration config) {
        return insertSql.computeIfAbsent(config.aggregateType, aggregateType ->
                bind("INSERT INTO {:tableName} (\n" +
                             "        {:aggregateIdColumn},\n" +
                             "        {:eventOrderColumn},\n" +
                             "        {:eventIdColumn},\n" +
                             "        {:causedByEventIdColumn},\n" +
                             "        {:correlationIdColumn},\n" +
                             "        {:eventTypeColumn},\n" +
                             "        {:eventRevisionColumn},\n" +
                             "        {:timestampColumn},\n" +
                             "        {:eventPayloadColumn},\n" +
                             "        {:eventMetaDataColumn},\n" +
                             "        {:tenantColumn}\n" +
                             "     ) VALUES (\n" +
                             "        :aggregateId,\n" +
                             "        :eventOrder,\n" +
                             "        :eventId,\n" +
                             "        :causedByEventId,\n" +
                             "        :correlationId,\n" +
                             "        :eventType,\n" +
                             "        :eventRevision,\n" +
                             "        :timestamp,\n" +
                             "        :eventPayload::{:eventPayloadJSONType},\n" +
                             "        :eventMetaData::{:eventMetaDataPayloadJSONType},\n" +
                             "        :tenant\n" +
                             "     ) RETURNING {:globalOrder}",
                     // Column names
                     arg("tableName", config.eventStreamTableName.toLowerCase()),
                     arg("aggregateIdColumn", config.eventStreamTableColumnNames.aggregateIdColumn),
                     arg("eventOrderColumn", config.eventStreamTableColumnNames.eventOrderColumn),
                     arg("eventIdColumn", config.eventStreamTableColumnNames.eventIdColumn),
                     arg("causedByEventIdColumn", config.eventStreamTableColumnNames.causedByEventIdColumn),
                     arg("correlationIdColumn", config.eventStreamTableColumnNames.correlationIdColumn),
                     arg("eventTypeColumn", config.eventStreamTableColumnNames.eventTypeColumn),
                     arg("eventRevisionColumn", config.eventStreamTableColumnNames.eventRevisionColumn),
                     arg("timestampColumn", config.eventStreamTableColumnNames.timestampColumn),
                     arg("eventPayloadColumn", config.eventStreamTableColumnNames.eventPayloadColumn),
                     arg("eventPayloadJSONType", config.eventJsonColumnType),
                     arg("eventMetaDataColumn", config.eventStreamTableColumnNames.eventMetaDataColumn),
                     arg("eventMetaDataPayloadJSONType", config.eventMetadataJsonColumnType),
                     arg("tenantColumn", config.eventStreamTableColumnNames.tenantColumn),
                     arg("globalOrder", config.eventStreamTableColumnNames.globalOrderColumn)));
    }
}
