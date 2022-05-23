package dk.cloudcreate.essentials.components.eventsourced.aggregates.classic.objenesis;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dk.cloudcreate.essentials.components.common.transaction.UnitOfWork;
import dk.cloudcreate.essentials.components.common.types.*;
import dk.cloudcreate.essentials.components.eventsourced.aggregates.classic.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.bus.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer.AggregateIdSerializer;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.*;
import dk.cloudcreate.essentials.jackson.immutable.EssentialsImmutableJacksonModule;
import dk.cloudcreate.essentials.jackson.types.EssentialTypesJacksonModule;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.postgres.PostgresPlugin;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import reactor.core.Disposable;

import java.time.*;
import java.util.*;
import java.util.function.Consumer;

import static dk.cloudcreate.essentials.components.eventsourced.aggregates.classic.AggregateRootInstanceFactory.objenesisAggregateRootFactory;
import static dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence.table_per_aggregate_type.SeparateTablePerAggregateTypeConfiguration.standardSingleTenantConfigurationUsingJackson;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Objenesis OrderAggregateRootRepositoryTest")
@Testcontainers
class OrderAggregateRootRepositoryTest {
    public static final EventMetaData META_DATA = EventMetaData.of("Key1", "Value1", "Key2", "Value2");
    public static final AggregateType ORDERS    = AggregateType.of("Orders");

    private Jdbi                                                             jdbi;
    private AggregateType                                                    aggregateType;
    private EventStoreUnitOfWorkFactory                                      unitOfWorkFactory;
    private TestPersistableEventMapper                                       eventMapper;
    private PostgresqlEventStore<SeparateTablePerAggregateTypeConfiguration> eventStore;

    @Container
    private final PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:latest").withDatabaseName("event-store")
                                                                                                           .withUsername("test-user")
                                                                                                           .withPassword("secret-password");

    private AggregateRootRepository<OrderId, Order> ordersRepository;
    private RecordingLocalEventBusConsumer          recordingLocalEventBusConsumer;
    private Disposable                              persistedEventFlux;
    private List<PersistedEvent>                    asynchronousOrderEventsReceived;

    @BeforeEach
    void setup() {
        jdbi = Jdbi.create(postgreSQLContainer.getJdbcUrl(),
                           postgreSQLContainer.getUsername(),
                           postgreSQLContainer.getPassword());
        jdbi.installPlugin(new PostgresPlugin());
        jdbi.setSqlLogger(new EventStoreSqlLogger());

        aggregateType = ORDERS;
        unitOfWorkFactory = new EventStoreManagedUnitOfWorkFactory(jdbi);
        eventMapper = new TestPersistableEventMapper();
        eventStore = new PostgresqlEventStore<>(unitOfWorkFactory,
                                                new SeparateTablePerAggregateTypePersistenceStrategy(jdbi,
                                                                                                     unitOfWorkFactory,
                                                                                                     eventMapper));
        recordingLocalEventBusConsumer = new RecordingLocalEventBusConsumer();
        eventStore.localEventBus().addSyncSubscriber(recordingLocalEventBusConsumer);

        ordersRepository = AggregateRootRepository.from(eventStore,
                                                        standardSingleTenantConfigurationUsingJackson(ORDERS,
                                                                                                      createObjectMapper(),
                                                                                                      AggregateIdSerializer.serializerFor(OrderId.class),
                                                                                                      IdentifierColumnType.UUID,
                                                                                                      JSONColumnType.JSONB),
                                                        objenesisAggregateRootFactory(),
                                                        Order.class);

        asynchronousOrderEventsReceived = new ArrayList<>();
        persistedEventFlux = eventStore.pollEvents(ORDERS,
                                                   GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER,
                                                   Optional.empty(),
                                                   Optional.of(Duration.ofMillis(100)),
                                                   Optional.empty(),
                                                   Optional.empty())
                                       .subscribe(event -> asynchronousOrderEventsReceived.add(event));
    }

    @AfterEach
    void cleanup() {
        unitOfWorkFactory.getCurrentUnitOfWork().ifPresent(UnitOfWork::rollback);
        assertThat(unitOfWorkFactory.getCurrentUnitOfWork()).isEmpty();

        if (persistedEventFlux != null) {
            persistedEventFlux.dispose();
        }
    }

    @Test
    void persist_and_load_Order_aggregate() {
        // Given
        var orderId         = OrderId.of("beed77fb-d911-1111-9c48-03ed5bfe8f89");
        var customerId      = CustomerId.of("Test-Customer-Id-10");
        var orderNumber     = 1234;
        var productId       = ProductId.of("ProductId-1");
        var productQuantity = 2;

        // And
        var order = new Order(orderId, customerId, orderNumber);
        order.addProduct(productId, productQuantity);

        // Check state change
        assertThat(order.uncommittedChanges().size()).isEqualTo(2);
        var uncommittedEvents = new ArrayList<>(order.uncommittedChanges());
        assertThat((CharSequence) order.aggregateId()).isEqualTo(orderId);
        assertThat(order.productAndQuantity.get(productId)).isEqualTo(productQuantity);
        assertThat(order.accepted).isFalse();

        // When
        unitOfWorkFactory.usingUnitOfWork(unitOfWork -> {
            ordersRepository.persist(order);
        });

        // Then
        assertThat(order.uncommittedChanges().size()).isEqualTo(0);
        assertThat(recordingLocalEventBusConsumer.beforeCommitPersistedEvents.size()).isEqualTo(2);
        assertThat(recordingLocalEventBusConsumer.afterCommitPersistedEvents.size()).isEqualTo(2);
        Awaitility.waitAtMost(Duration.ofMillis(300))
                  .untilAsserted(() -> assertThat(asynchronousOrderEventsReceived.size()).isEqualTo(2));

        // And The events received are the same
        assertThat(recordingLocalEventBusConsumer.beforeCommitPersistedEvents)
                .isEqualTo(recordingLocalEventBusConsumer.afterCommitPersistedEvents);
        assertThat(recordingLocalEventBusConsumer.beforeCommitPersistedEvents)
                .isEqualTo(asynchronousOrderEventsReceived);

        // And the events contains everything expected
        assertThat((CharSequence) asynchronousOrderEventsReceived.get(0).eventId()).isNotNull();
        assertThat((CharSequence) asynchronousOrderEventsReceived.get(0).aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) asynchronousOrderEventsReceived.get(0).aggregateType()).isEqualTo(aggregateType);
        assertThat(asynchronousOrderEventsReceived.get(0).eventOrder()).isEqualTo(EventOrder.of(0));
        assertThat(asynchronousOrderEventsReceived.get(0).eventRevision()).isEqualTo(EventRevision.of(1));
        assertThat(asynchronousOrderEventsReceived.get(0).globalEventOrder()).isEqualTo(GlobalEventOrder.of(1));
        assertThat(asynchronousOrderEventsReceived.get(0).timestamp()).isBefore(OffsetDateTime.now());
        assertThat(asynchronousOrderEventsReceived.get(0).event().getEventName()).isEmpty();
        assertThat(asynchronousOrderEventsReceived.get(0).event().getEventType()).isEqualTo(Optional.of(EventType.of(NoDefaultConstructorOrderEvents.OrderAdded.class)));
        assertThat(asynchronousOrderEventsReceived.get(0).event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(NoDefaultConstructorOrderEvents.OrderAdded.class).toString());
        assertThat(asynchronousOrderEventsReceived.get(0).event().getJsonDeserialized().get()).usingRecursiveComparison().isEqualTo(uncommittedEvents.get(0));
        assertThat(asynchronousOrderEventsReceived.get(0).event().getJson()).isEqualTo("{\"eventOrder\": 0, \"aggregateId\": \"beed77fb-d911-1111-9c48-03ed5bfe8f89\", \"orderNumber\": 1234, \"orderingCustomerId\": \"Test-Customer-Id-10\"}");
        assertThat(asynchronousOrderEventsReceived.get(0).metaData().getJson()).contains("\"Key1\": \"Value1\"");
        assertThat(asynchronousOrderEventsReceived.get(0).metaData().getJson()).contains("\"Key2\": \"Value2\"");
        assertThat(asynchronousOrderEventsReceived.get(0).metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(asynchronousOrderEventsReceived.get(0).metaData().getJsonDeserialized()).isEqualTo(Optional.of(META_DATA));
        assertThat(asynchronousOrderEventsReceived.get(0).causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(asynchronousOrderEventsReceived.get(0).correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));

        assertThat((CharSequence) asynchronousOrderEventsReceived.get(1).eventId()).isNotNull();
        assertThat((CharSequence) asynchronousOrderEventsReceived.get(1).aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) asynchronousOrderEventsReceived.get(1).aggregateType()).isEqualTo(aggregateType);
        assertThat(asynchronousOrderEventsReceived.get(1).eventOrder()).isEqualTo(EventOrder.of(1));
        assertThat(asynchronousOrderEventsReceived.get(1).eventRevision()).isEqualTo(EventRevision.of(1));
        assertThat(asynchronousOrderEventsReceived.get(1).globalEventOrder()).isEqualTo(GlobalEventOrder.of(2));
        assertThat(asynchronousOrderEventsReceived.get(1).timestamp()).isBefore(OffsetDateTime.now());
        assertThat(asynchronousOrderEventsReceived.get(1).event().getEventName()).isEmpty();
        assertThat(asynchronousOrderEventsReceived.get(1).event().getEventType()).isEqualTo(Optional.of(EventType.of(NoDefaultConstructorOrderEvents.ProductAddedToOrder.class)));
        assertThat(asynchronousOrderEventsReceived.get(1).event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(NoDefaultConstructorOrderEvents.ProductAddedToOrder.class).toString());
        assertThat(asynchronousOrderEventsReceived.get(1).event().getJsonDeserialized().get()).usingRecursiveComparison().isEqualTo(uncommittedEvents.get(1));
        assertThat(asynchronousOrderEventsReceived.get(1).event().getJson()).isEqualTo("{\"quantity\": 2, \"productId\": \"ProductId-1\", \"eventOrder\": 1, \"aggregateId\": \"beed77fb-d911-1111-9c48-03ed5bfe8f89\"}");
        assertThat(asynchronousOrderEventsReceived.get(1).metaData().getJson()).contains("\"Key1\": \"Value1\"");
        assertThat(asynchronousOrderEventsReceived.get(1).metaData().getJson()).contains("\"Key2\": \"Value2\"");
        assertThat(asynchronousOrderEventsReceived.get(1).metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(asynchronousOrderEventsReceived.get(1).metaData().getJsonDeserialized()).isEqualTo(Optional.of(META_DATA));
        assertThat(asynchronousOrderEventsReceived.get(1).causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(asynchronousOrderEventsReceived.get(1).correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));

        var loadedOrder = unitOfWorkFactory.withUnitOfWork(unitOfWork -> ordersRepository.load(orderId));
        assertThat(loadedOrder).isNotNull();
        assertThat((CharSequence) loadedOrder.aggregateId()).isEqualTo(orderId);
        assertThat(loadedOrder.productAndQuantity.get(productId)).isEqualTo(productQuantity);
        assertThat(loadedOrder.accepted).isFalse();
    }

    @Test
    void persist_load_and_persist_Order() {
        // Given
        var orderId         = OrderId.of("beed77fb-d911-1111-9c48-03ed5bfe8f89");
        var customerId      = CustomerId.of("Test-Customer-Id-10");
        var orderNumber     = 1234;
        var productId       = ProductId.of("ProductId-1");
        var productQuantity = 2;

        // And
        var order = new Order(orderId, customerId, orderNumber);
        order.addProduct(productId, productQuantity);
        unitOfWorkFactory.usingUnitOfWork(unitOfWork -> {
            ordersRepository.persist(order);
        });

        Awaitility.waitAtMost(Duration.ofMillis(300))
                  .untilAsserted(() -> assertThat(asynchronousOrderEventsReceived.size()).isEqualTo(2));
        asynchronousOrderEventsReceived.clear();
        recordingLocalEventBusConsumer.clear();

        // When modifying a loaded aggregate within a unitofwork then the changes are automatically persisted
        var uncommittedEvents = new ArrayList<>();
        var changedOrder = unitOfWorkFactory.withUnitOfWork(unitOfWork -> {
            var loadedOrder = ordersRepository.load(orderId);
            assertThat(loadedOrder.uncommittedChanges().size()).isEqualTo(0);
            assertThat((CharSequence) loadedOrder.aggregateId()).isEqualTo(orderId);
            assertThat(loadedOrder.productAndQuantity.get(productId)).isEqualTo(productQuantity);
            assertThat(loadedOrder.accepted).isFalse();

            loadedOrder.accept();

            assertThat(loadedOrder.uncommittedChanges().size()).isEqualTo(1);
            uncommittedEvents.addAll(loadedOrder.uncommittedChanges());
            assertThat((CharSequence) loadedOrder.aggregateId()).isEqualTo(orderId);
            assertThat(loadedOrder.productAndQuantity.get(productId)).isEqualTo(productQuantity);
            assertThat(loadedOrder.accepted).isTrue();

            return loadedOrder;
        });

        // Then
        assertThat(changedOrder.uncommittedChanges().size()).isEqualTo(0);
        assertThat(recordingLocalEventBusConsumer.beforeCommitPersistedEvents.size()).isEqualTo(1);
        assertThat(recordingLocalEventBusConsumer.afterCommitPersistedEvents.size()).isEqualTo(1);
        Awaitility.waitAtMost(Duration.ofMillis(300))
                  .untilAsserted(() -> assertThat(asynchronousOrderEventsReceived.size()).isEqualTo(1));

        // And The events received are the same
        assertThat(recordingLocalEventBusConsumer.beforeCommitPersistedEvents)
                .isEqualTo(recordingLocalEventBusConsumer.afterCommitPersistedEvents);
        assertThat(recordingLocalEventBusConsumer.beforeCommitPersistedEvents)
                .isEqualTo(asynchronousOrderEventsReceived);

        assertThat((CharSequence) asynchronousOrderEventsReceived.get(0).eventId()).isNotNull();
        assertThat((CharSequence) asynchronousOrderEventsReceived.get(0).aggregateId()).isEqualTo(orderId);
        assertThat((CharSequence) asynchronousOrderEventsReceived.get(0).aggregateType()).isEqualTo(aggregateType);
        assertThat(asynchronousOrderEventsReceived.get(0).eventOrder()).isEqualTo(EventOrder.of(2));
        assertThat(asynchronousOrderEventsReceived.get(0).eventRevision()).isEqualTo(EventRevision.of(1));
        assertThat(asynchronousOrderEventsReceived.get(0).globalEventOrder()).isEqualTo(GlobalEventOrder.of(3));
        assertThat(asynchronousOrderEventsReceived.get(0).timestamp()).isBefore(OffsetDateTime.now());
        assertThat(asynchronousOrderEventsReceived.get(0).event().getEventName()).isEmpty();
        assertThat(asynchronousOrderEventsReceived.get(0).event().getEventType()).isEqualTo(Optional.of(EventType.of(NoDefaultConstructorOrderEvents.OrderAccepted.class)));
        assertThat(asynchronousOrderEventsReceived.get(0).event().getEventTypeOrNamePersistenceValue()).isEqualTo(EventType.of(NoDefaultConstructorOrderEvents.OrderAccepted.class).toString());
        assertThat(asynchronousOrderEventsReceived.get(0).event().getJsonDeserialized().get()).usingRecursiveComparison().isEqualTo(uncommittedEvents.get(0));
        assertThat(asynchronousOrderEventsReceived.get(0).event().getJson()).isEqualTo("{\"eventOrder\": 2, \"aggregateId\": \"beed77fb-d911-1111-9c48-03ed5bfe8f89\"}");
        assertThat(asynchronousOrderEventsReceived.get(0).metaData().getJson()).contains("\"Key1\": \"Value1\"");
        assertThat(asynchronousOrderEventsReceived.get(0).metaData().getJson()).contains("\"Key2\": \"Value2\"");
        assertThat(asynchronousOrderEventsReceived.get(0).metaData().getJavaType()).isEqualTo(Optional.of(EventMetaData.class.getName()));
        assertThat(asynchronousOrderEventsReceived.get(0).metaData().getJsonDeserialized()).isEqualTo(Optional.of(META_DATA));
        assertThat(asynchronousOrderEventsReceived.get(0).causedByEventId()).isEqualTo(Optional.of(eventMapper.causedByEventId));
        assertThat(asynchronousOrderEventsReceived.get(0).correlationId()).isEqualTo(Optional.of(eventMapper.correlationId));
    }

    private ObjectMapper createObjectMapper() {
        var objectMapper = JsonMapper.builder()
                                     .disable(MapperFeature.AUTO_DETECT_GETTERS)
                                     .disable(MapperFeature.AUTO_DETECT_IS_GETTERS)
                                     .disable(MapperFeature.AUTO_DETECT_SETTERS)
                                     .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
                                     .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                                     .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                                     .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                                     .enable(MapperFeature.AUTO_DETECT_CREATORS)
                                     .enable(MapperFeature.AUTO_DETECT_FIELDS)
                                     .enable(MapperFeature.PROPAGATE_TRANSIENT_MARKER)
                                     .addModule(new Jdk8Module())
                                     .addModule(new JavaTimeModule())
                                     .addModule(new EssentialTypesJacksonModule())
                                     .addModule(new EssentialsImmutableJacksonModule())
                                     .build();

        objectMapper.setVisibility(objectMapper.getSerializationConfig().getDefaultVisibilityChecker()
                                               .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                                               .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                                               .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                                               .withCreatorVisibility(JsonAutoDetect.Visibility.ANY));
        return objectMapper;
    }

    private static class TestPersistableEventMapper implements PersistableEventMapper {
        private final CorrelationId correlationId   = CorrelationId.random();
        private final EventId       causedByEventId = EventId.random();

        @Override
        public PersistableEvent map(Object aggregateId, AggregateTypeConfiguration aggregateTypeConfiguration, Object event, EventOrder eventOrder) {
            return PersistableEvent.from(EventId.random(),
                                         aggregateTypeConfiguration.aggregateType,
                                         aggregateId,
                                         EventTypeOrName.with(event.getClass()),
                                         event,
                                         eventOrder,
                                         EventRevision.of(1),
                                         META_DATA,
                                         OffsetDateTime.now(),
                                         causedByEventId,
                                         correlationId,
                                         null);
        }
    }

    /**
     * Simple test in memory projector that just returns the underlying list of {@link PersistedEvent}'s
     */
    private class InMemoryListProjector implements InMemoryProjector {
        @Override
        public boolean supports(Class<?> projectionType) {
            return List.class.isAssignableFrom(projectionType);
        }

        @Override
        public <ID, PROJECTION> Optional<PROJECTION> projectEvents(AggregateType aggregateType,
                                                                   ID aggregateId,
                                                                   Class<PROJECTION> projectionType,
                                                                   EventStore<?> eventStore) {
            var eventStream = eventStore.fetchStream(aggregateType,
                                                     aggregateId);
            return (Optional<PROJECTION>) eventStream.map(actualEventStream -> actualEventStream.eventList());
        }
    }

    private static class RecordingLocalEventBusConsumer implements Consumer<PersistedEvents> {
        private final List<PersistedEvent> beforeCommitPersistedEvents  = new ArrayList<>();
        private final List<PersistedEvent> afterCommitPersistedEvents   = new ArrayList<>();
        private final List<PersistedEvent> afterRollbackPersistedEvents = new ArrayList<>();

        @Override
        public void accept(PersistedEvents persistedEvents) {
            if (persistedEvents.commitStage == CommitStage.BeforeCommit) {
                beforeCommitPersistedEvents.addAll(persistedEvents.events);
            } else if (persistedEvents.commitStage == CommitStage.AfterCommit) {
                afterCommitPersistedEvents.addAll(persistedEvents.events);
            } else {
                afterRollbackPersistedEvents.addAll(persistedEvents.events);
            }
        }

        private void clear() {
            beforeCommitPersistedEvents.clear();
            afterCommitPersistedEvents.clear();
            afterRollbackPersistedEvents.clear();
        }
    }
}