# Essentials Java Components

Essentials Components is a set of Java version 11 (and later) components that are based on the [Essentials](https://github.com/cloudcreate-dk/essentials) library while providing more complex features
or Components such as an Event Store, Distributed Fenced Locking, Event Sourced Aggregates

**NOTE:**
**The libraries are WORK-IN-PROGRESS**

# Common-Types
This library contains the smallest set of supporting building blocks needed for other Essentials Components libraries, such as:

- **Identifiers**
    - `CorrelationId`
    - `EventId`
    - `MessageId`
    - `SubscriberId`
    - `Tenant` and `TenantId`
- **Common Interfaces**
    - `Lifecycle`

To use `common-types` just add the following Maven dependency:

```
<dependency>
    <groupId>dk.cloudcreate.essentials.components</groupId>
    <artifactId>common-types</artifactId>
    <version>0.1.0</version>
</dependency>
```

# PostgreSQL Distributed Fenced Lock

This library provides a Postgresql based Locking Manager variant of the Fenced Locking concept described [here](https://martin.kleppmann.com/2016/02/08/how-to-do-distributed-locking.html)

```
public PostgresqlFencedLockManager(Jdbi jdbi,
                                   Duration lockTimeOut,
                                   Duration lockConfirmationInterval) {
   ...
}
```

Usage example:

```
var lockManager = new PostgresqlFencedLockManager(Jdbi.create(jdbcUrl,
                                                               username,
                                                               password),
                                                   Duration.ofSeconds(3),
                                                   Duration.ofSeconds(1));
lockManager.start();

// Try to acquire the lock. If the lock is acquired by another lock manager instance then it returns Optional.empty()
Optional<FencedLock> lockOption = lockManager.tryAcquireLock(lockName);

// Try to acquire the lock. If the lock is acquired by another lock manager instance then it will keep trying for 2 seconds and 
// if the lock is still acquired then it will return Optional.empty()
Optional<FencedLock> lockOption = lockManager.tryAcquireLock(lockName, Duration.ofSeconds(2));

// Acquire lock. Is the lock is free then the method return immediately, otherwise it will wait until it can acquire the lock
FencedLock lock = lockManager.acquireLock(lockName);

// Perform an asynchronos lock acquiring
lockManager.acquireLockAsync(lockName, new LockCallback() {
    @Override
    public void lockReleased(FencedLock lock) {
        
    }

    @Override
    public void lockAcquired(FencedLock lock) {

    }
});

// The current fenced token can accessed through
long fenceToken = fencedLock.getCurrentToken(); 

// You can check if a lock is acquired by the lock manager that returned it
fencedLock.isLockedByThisLockManagerInstance();
```

To use `PostgreSQL Distributed Fenced Lock` just add the following Maven dependency:

```
<dependency>
    <groupId>dk.cloudcreate.essentials.components</groupId>
    <artifactId>postgresql-distributed-fenced-lock</artifactId>
    <version>0.1.0</version>
</dependency>
```

# PostgreSQL Event Store

This library contains a fully features Event Store

## Setup JDBI
The `PostgresqlEventStore` internally uses the Jdbi JDBC API.  
Below is an example of how to configure Jdbi - See `Spring-PostgreSQL Event Store` for a Spring oriented setup

```
var jdbi = Jdbi.create(jdbcUrl,
                           username,
                           password);
jdbi.installPlugin(new PostgresPlugin());
jdbi.setSqlLogger(new EventStoreSqlLogger());
```

Example of setting up Jdbi using `HikariDataSource`:
```
HikariConfig hikariConfig = new HikariConfig();
hikariConfig.setJdbcUrl(jdbcUrl);
hikariConfig.setUsername(username);
hikariConfig.setPassword(password);

var ds = new HikariDataSource(hikariConfig);
var jdbi = Jdbi.create(ds);
jdbi.installPlugin(new PostgresPlugin());
jdbi.setSqlLogger(new EventStoreSqlLogger());
```

## UnitOfWork / Transaction Management
Setup the EventStore using transaction/UnitOfWork management by the EventStore (e.g. instead of Spring)  
See `Spring-PostgreSQL Event Store` for a Spring oriented setup

```
var persistenceStrategy = new SeparateTablePerAggregateTypePersistenceStrategy(jdbi,
                                                                               new EventStoreManagedUnitOfWorkFactory(jdbi),
                                                                               new MyPersistableEventMapper());
eventStore = new PostgresqlEventStore<>(unitOfWorkFactory,
                                        persistenceStrategy);
```

## PersistableEventMapper
The `MyPersistableEventMapper` is a mapper that you need to write in order to provide a translation between generic Events such as OrderAdded, OrderAccepted and the `PersistableEvent` type that
the `EventStore` knows how to persist. The custom `PersistableEventMapper` can also provide context specific information such as `Tenant`, `CorrelationId`, etc.  
Here an example of a `TestPersistableEventMapper`:

```
class TestPersistableEventMapper implements PersistableEventMapper {
        private final CorrelationId correlationId   = CorrelationId.random();
        private final EventId       causedByEventId = EventId.random();

        @Override
        public PersistableEvent map(Object aggregateId, 
                                    AggregateTypeConfiguration aggregateTypeConfiguration, 
                                    Object event, 
                                    EventOrder eventOrder) {
            return PersistableEvent.from(EventId.random(),
                                         aggregateTypeConfiguration.aggregateType,
                                         aggregateId,
                                         EventTypeOrName.with(event.getClass()),
                                         event,
                                         eventOrder,
                                         EventRevision.of(1),
                                         new EventMetaData(),
                                         OffsetDateTime.now(),
                                         causedByEventId,
                                         correlationId,
                                         TenantId.of("MyTenant"));
        }
    }
```

## Define the `AggregateType`'s that can be persisted.

An `AggregateType` should not be confused with the Java implementation class for your Aggregate (see the `EventSourced-Aggregates` module).  
An `AggregateType` is used for grouping/categorizing multiple `AggregateEventStream` instances related to similar types of aggregates.  
This allows us to easily retrieve or be notified of new Events related to the same type of Aggregates (such as when using `EventStore#pollEvents(..)`)     
Using `SeparateTablePerAggregateTypePersistenceStrategy` means that each `AggregateType` will be persisted in a separate event store table.

You can add as many `AggregateType` configurations as needed, but they need to be added BEFORE you try to persist or load events related to 
a given `AggregateType`.

```
var orders = AggregateType.of("Order");
eventStore.addAggregateTypeConfiguration(
    standardSingleTenantConfigurationUsingJackson(orders,
                                                  createObjectMapper(),
                                                  AggregateIdSerializer.serializerFor(OrderId.class),
                                                  IdentifierColumnType.UUID,
                                                  JSONColumnType.JSONB));
```

### ObjectMapper setup
The setup of the `ObjectMapper` needs to support the type of Events being persisted.
Below is an example of an immutable Event design, which requires the `ObjectMapper` to be configured
with the [Essentials Immutable-Jackson](https://github.com/cloudcreate-dk/essentials/tree/main/immutable-jackson) module's `EssentialsImmutableJacksonModule`: 
```
public class OrderEvent {
    public final OrderId orderId;

    public OrderEvent(OrderId orderId) {
        this.orderId = orderId;
    }

    public static class OrderAdded extends OrderEvent {
        public final CustomerId orderingCustomerId;
        public final long       orderNumber;

        public OrderAdded(OrderId orderId, CustomerId orderingCustomerId, long orderNumber) {
            super(orderId);
            this.orderingCustomerId = orderingCustomerId;
            this.orderNumber = orderNumber;
        }
    }

    public static class ProductAddedToOrder extends OrderEvent {
        public final ProductId productId;
        public final int       quantity;

        public ProductAddedToOrder(OrderId orderId, ProductId productId, int quantity) {
            super(orderId);
            this.productId = productId;
            this.quantity = quantity;
        }
    }
}
```


```
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
                                 .addModule(new EssentialTypesJacksonModule())      // Needed to support serializing and deserializing Essential Types such as EventId, OrderId, etc.
                                 .addModule(new EssentialsImmutableJacksonModule()) // Needed if the Event is immutable (i.e. doesn't have a default constructor)
                                 .build();

    objectMapper.setVisibility(objectMapper.getSerializationConfig().getDefaultVisibilityChecker()
                                           .withGetterVisibility(JsonAutoDetect.Visibility.NONE)
                                           .withSetterVisibility(JsonAutoDetect.Visibility.NONE)
                                           .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                                           .withCreatorVisibility(JsonAutoDetect.Visibility.ANY));
    return objectMapper;
}
```

## Appending Events to an AggregateType's EventStream
```
eventStore.unitOfWorkFactory().usingUnitOfWork(unitOfWork -> {
   var orderId = OrderId.random();
   eventStore.appendToStream(orders,
                             orderId,
                             new OrderAdded(orderId,
                                            CustomerId.random(),
                                            1234));
});

var events = eventStore.unitOfWorkFactory().withUnitOfWork(unitOfWork -> {
  return eventStore.fetchStream(orders, orderId);
});
```

## LocalEventBus event subscription
You can subscribe (synchronous or asynchronous) to events directly on the `EventStore` by e.g. listening til the `LocalEventBus`
```
eventStore.localEventBus().addSyncSubscriber(persistedEvents -> {
            
});
eventStore.localEventBus().addAsyncSubscriber(persistedEvents -> {
    
});
```

## EventStore asynchronous Event polling
You can also poll for events using the `EventStore` event polling mechanism, which allows you to subscribe to any point in an EventStream related to a 
given type of Aggregate:
```
disposableFlux = eventStore.pollEvents(orders, // Aggregatetype
                                           GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER,
                                           Optional.empty(),
                                           Optional.of(Duration.ofMillis(100)),
                                           Optional.empty(),
                                           Optional.empty())
                               .subscribe(event -> {
                               });
```

## EventStore SubscriptionManager
Finally, you can use the `EventStoreSubscriptionManager`, which supports:

###Subscribe asynchronously 
Using asynchronous event subscription the `EventStoreSubscriptionManager` will keep track of 
where the individual Subscribers `ResumePoint` in the AggregateType EventStream's they subscribing to:
- `exclusivelySubscribeToAggregateEventsAsynchronously` - uses the `FencedLockManager` to ensure that only a single subscriber, with the same combination of `SubscriberId` and `AggregateType`, in the cluster can subscribe.
- `subscribeToAggregateEventsAsynchronously` - same as above, just without using the `FencedLockManager` to coordinate subscribers in a cluster

### Subscribe synchronously
Synchronous subscription allows you to receive and react to Events published within the active Transaction/`UnitOfWork` that's involved in `appending` the events to the `EventStream` 
This can be useful for certain transactional views/projections where you require transactional consistency (e.g. assigning a sequential customer number, etc.):
- `subscribeToAggregateEventsInTransaction`

Example using `exclusivelySubscribeToAggregateEventsAsynchronously`: 
```
var eventStoreSubscriptionManager = EventStoreSubscriptionManager.createFor(eventStore,
                                                                             50,
                                                                             Duration.ofMillis(100),
                                                                             new PostgresqlFencedLockManager(jdbi,
                                                                                                             Duration.ofSeconds(3),
                                                                                                             Duration.ofSeconds(1)),
                                                                             Duration.ofSeconds(1),
                                                                             new PostgresqlDurableSubscriptionRepository(jdbi));
eventStoreSubscriptionManager.start();

var productsSubscription = eventStoreSubscriptionManager.exclusivelySubscribeToAggregateEventsAsynchronously(
        SubscriberId.of("ProductsSub1"),
        AggregateType.of("Products"),
        GlobalEventOrder.FIRST_GLOBAL_EVENT_ORDER, // The initial subscription points. Only applies the first time you subscribe
                                                   // All subsequent subscriptions for the same subscriber, the EventStoreSubscriptionManager
                                                   // keeps track of the Resume Point using the PostgresqlDurableSubscriptionRepository
        Optional.empty(),
        new PersistedEventHandler() {
            @Override
            public void onResetFrom(GlobalEventOrder globalEventOrder) {
              // You can reset the Resume Point using the resetFrom(..) method after which this method will be called
              // and the Resume Point in the EventStoreSubscriptionManager will be reset to the same value
              // and the event stream will start streaming events from the new Resume Point       
            }

            @Override
            public void handle(PersistedEvent event) {
                
            }
        });
```

To use `Postgresql Event Store` just add the following Maven dependency:

```
<dependency>
    <groupId>dk.cloudcreate.essentials.components</groupId>
    <artifactId>postgresql-event-store</artifactId>
    <version>0.1.0</version>
</dependency>
```

##Features coming soon
- Subscription Manager event gap detection
- Improved Subscription Manager error handling (e.g. using the `PostgreSQL Durable Queue`) 
- EventStore asynchronous event-subscription using Postgresql Notify functionality to only poll when there have been events appended to the `EventStream` 

# Spring PostgreSQL Event Store

This library provides the `SpringManagedUnitOfWorkFactory` (as opposed to the standard `EventStoreManagedUnitOfWorkFactory`)
which allows the `EventStore` to participate in Spring managed Transactions.

```
@SpringBootApplication
class Application {
    @Bean
    public com.fasterxml.jackson.databind.Module essentialJacksonModule() {
        return new EssentialTypesJacksonModule();
    }

    @Bean
    public Jdbi jdbi(DataSource dataSource) {
        Jdbi jdbi = Jdbi.create(new TransactionAwareDataSourceProxy(dataSource));
        return jdbi;
    }
    
    @Bean
    public UnitOfWorkFactory unitOfWorkFactory(Jdbi jdbi, PlatformTransactionManager transactionManager) {
        return new SpringManagedUnitOfWorkFactory(jdbi, transactionManager);
    }
}
```

The rest of the setup matches the Postgresql EventStore setup.

```
jdbi.installPlugin(new PostgresPlugin());
jdbi.setSqlLogger(new EventStoreSqlLogger());

var persistenceStrategy = new SeparateTablePerAggregateTypePersistenceStrategy(jdbi,
                                                                               new EventStoreManagedUnitOfWorkFactory(jdbi),
                                                                               new MyPersistableEventMapper());
eventStore = new PostgresqlEventStore<>(unitOfWorkFactory,
                                        persistenceStrategy);
                                        
var orders = AggregateType.of("Order");

eventStore.addAggregateTypeConfiguration(
    standardSingleTenantConfigurationUsingJackson(orders,
                                                  createObjectMapper(),
                                                  AggregateIdSerializer.serializerFor(OrderId.class),
                                                  IdentifierColumnType.UUID,
                                                  JSONColumnType.JSONB));
                                                  
                                                  
```

You can still use the UnitOfWorkFactory to start and commit Spring transactions, or you can use the `TransactionTemplate` class or `@Transactional` annotation to start and commit transactions.

No matter how a transaction then you can always acquire the active `UnitOfWork` using

```
unitOfWorkFactory.getCurrentUnitOfWork()
```

To use `Spring Postgresql Event Store` just add the following Maven dependency:

```
<dependency>
    <groupId>dk.cloudcreate.essentials.components</groupId>
    <artifactId>spring-postgresql-event-store</artifactId>
    <version>0.1.0</version>
</dependency>
```

# Event Sourced Aggregates

This library focuses on providing different falvours of Event Source Aggregates

Aggregate design where the Aggregate contains the command method and all event handlers and state is contained within an `AggregateState` object:

```
public class Order extends AggregateRootWithState<OrderId, OrderState, Order> {
    public Order() {
    }

    public Order(OrderId orderId,
                 CustomerId orderingCustomerId,
                 int orderNumber) {
        requireNonNull(orderId, "You must provide an orderId");
        requireNonNull(orderingCustomerId, "You must provide an orderingCustomerId");

        apply(new OrderAdded(orderId,
                             orderingCustomerId,
                             orderNumber));
    }

    public void addProduct(ProductId productId, int quantity) {
        requireNonNull(productId, "You must provide a productId");
        if (state.accepted) {
            throw new IllegalStateException("Order is already accepted");
        }
        apply(new ProductAddedToOrder(productId, quantity));
    }

    public void adjustProductQuantity(ProductId productId, int newQuantity) {
        requireNonNull(productId, "You must provide a productId");
        if (state.accepted) {
            throw new IllegalStateException("Order is already accepted");
        }
        if (state.productAndQuantity.containsKey(productId)) {
            apply(new ProductOrderQuantityAdjusted(productId, newQuantity));
        }
    }

    public void removeProduct(ProductId productId) {
        requireNonNull(productId, "You must provide a productId");
        if (state.accepted) {
            throw new IllegalStateException("Order is already accepted");
        }
        if (state.productAndQuantity.containsKey(productId)) {
            apply(new ProductRemovedFromOrder(productId));
        }
    }

    public void accept() {
        if (state.accepted) {
            return;
        }
        apply(new OrderAccepted());
    }
}
```

OrderState:

```
public class OrderState extends AggregateState<OrderId> {
    Map<ProductId, Integer> productAndQuantity;
    boolean                 accepted;

    @EventHandler
    private void on(OrderAdded e) {
        productAndQuantity = new HashMap<>();
    }

    @EventHandler
    private void on(ProductAddedToOrder e) {
        var existingQuantity = productAndQuantity.get(e.getProductId());
        productAndQuantity.put(e.getProductId(), e.getQuantity() + (existingQuantity != null ? existingQuantity : 0));
    }

    @EventHandler
    private void on(ProductOrderQuantityAdjusted e) {
        productAndQuantity.put(e.getProductId(), e.getNewQuantity());
    }

    @EventHandler
    private void on(ProductRemovedFromOrder e) {
        productAndQuantity.remove(e.getProductId());
    }

    @EventHandler
    private void on(OrderAccepted e) {
        accepted = true;
    }
}
```

Aggregate repository

```
var orders = AggregateType.of("Orders");
var ordersRepository = AggregateRootRepository.from(eventStore,
                                                    standardSingleTenantConfigurationUsingJackson(orders,
                                                        createObjectMapper(),
                                                        AggregateIdSerializer.serializerFor(OrderId.class),
                                                        IdentifierColumnType.UUID,
                                                        JSONColumnType.JSONB),
                                                    defaultConstructorFactory(),
                                                    Order.class);

var orderId = OrderId.random();
unitOfWorkFactory.usingUnitOfWork(unitOfWork -> {                                                    
   var order = new Order(orderId, CustomerId.random(), 1234);
   order.addProduct(ProductId.random(), 2);
   ordersRepository.persist(order);
});

// Using Spring Transaction Template
var order = transactionTemplate.execute(status -> ordersRepository.load(orderId));
```

To use `EventSourced Aggregates` just add the following Maven dependency:

```
<dependency>
    <groupId>dk.cloudcreate.essentials.components/groupId>
    <artifactId>eventsourced-aggregates</artifactId>
    <version>0.1.0</version>
</dependency>
```

## PostgreSQL Durable Queue

This library focuses purely on providing a durable Queue with support for DLQ (Dead Letter Queue) and message redelivery

....