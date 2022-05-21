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

# Event Sourced Aggregates

This library focuses on providing different flavours of Event Source Aggregates that are built to work with the `EventStore` concept.  
The `EventStore` is very flexible and doesn't specify any specific design requirements for an Aggregate or its Events, except that that have to be associated with an `AggregateType` (see the 
`AggregateType` sub section or the `EventStore` section for more information).

This library supports multiple flavours of Aggregate design such as the `AggregateRoot`, `AggregateRootWithState` and the `FlexAggregate`.   

#### Order aggregate with separate state object

Below is an example of an `Order` aggregate based on the `AggregateRootWithState` concept, where the Aggregate contains the command methods and all event handlers and state are contained
within an `AggregateState` object, which in this case is called the `OrderState`:

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

#### Event example:

The `AggregateRoot` (and by extension the `AggregateRootWithState`) defines a root/base `Event` which all events must inherit from.  
*Note: The `FlexAggregate` doesn't apply any requirements on Events, it can be POJO's (Plain Old Java Objects), `Record`'s, etc.*

```
public final class OrderEvents {
    public static class OrderAdded extends Event<OrderId> {
        private CustomerId orderingCustomerId;
        private long       orderNumber;

        public OrderAdded() {
        }

        public OrderAdded(OrderId orderId, CustomerId orderingCustomerId, long orderNumber) {
            // MUST be set manually for the FIRST/INITIAL - after this the AggregateRoot ensures
            // that the aggregateId will be set on the other events automatically
            aggregateId(orderId);
            this.orderingCustomerId = orderingCustomerId;
            this.orderNumber = orderNumber;
        }

        public CustomerId getOrderingCustomerId() {
            return orderingCustomerId;
        }

        public long getOrderNumber() {
            return orderNumber;
        }
    }

    public static class ProductAddedToOrder extends Event<OrderId> {
        private ProductId productId;
        private int       quantity;

        public ProductAddedToOrder() {
        }

        public ProductAddedToOrder(ProductId productId, int quantity) {
            this.productId = productId;
            this.quantity = quantity;
        }

        public ProductId getProductId() {
            return productId;
        }

        public int getQuantity() {
            return quantity;
        }
    }
    ...
}
```

#### OrderState:
The state object must inherit from the `AggregateState` base class:
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

### AggregateType
Each Aggregate implementation class (such as the `Order` Aggregate above) needs to be associated with an `AggregateType`.  
An `AggregateType` should not be confused with the Java implementation class for your Aggregate.

An `AggregateType` is used for grouping/categorizing multiple `AggregateEventStream` instances related to similar types of aggregates.  
This allows us to easily retrieve or be notified of new Events related to the same type of Aggregates (such as when using `EventStore#pollEvents(..)`)     
Using `SeparateTablePerAggregateTypePersistenceStrategy` means that each `AggregateType` will be persisted in a separate event store table.

What's important here is that the AggregateType is only a name and shouldn't be confused with the Fully Qualified Class Name of the Aggregate implementation class.  
This is the classical split between the logical concept and the physical implementation.  
It's important to not link the Aggregate Implementation Class (the Fully Qualified Class Name) with the AggregateType name as that would make refactoring of your code base much harder, as the Fully
Qualified Class Name then would be captured in the stored Events.   
Had the AggregateType and the Aggregate Implementation Class been one and the same, then moving the Aggregate class to another package or renaming it would break many things.

To avoid the temptation to use the same name for both the AggregateType and the Aggregate Implementation Class, we prefer using the plural name of the Aggregate as the AggregateType name.  
Example:

| Aggregate-Type | Aggregate Root Implementation Class (Fully Qualified Class Name) | Top-level Event Type (Fully Qualified Class Name) |  
|----------------|------------------------------------------------------------------|---------------------------------------------------|
| Orders         | com.mycompany.project.persistence.Order                          | com.mycompany.project.persistence.OrderEvent      |
| Accounts       | com.mycompany.project.persistence.Account                        | com.mycompany.project.persistence.AccountEvent    |
| Customer       | com.mycompany.project.persistence.Customer                       | com.mycompany.project.persistence.CustomerEvent   |

You can add as many `AggregateType` configurations as needed, but they need to be added BEFORE you try to persist or load events related to a given `AggregateType`.

### AggregateRoot Repository

In order to acquire an `AggregateRootRepository` instance for your Aggregate Root Implementation Class, you need to call the static method `from`
on the `AggregateRootRepository` interface.

Apart from providing an instance of the `EventStore` you also need to provide an `AggregateTypeConfiguration`, such as the `SeparateTablePerAggregateTypeConfiguration`
that instructs the `EventStore`'s  persistence strategy, such as the `SeparateTablePerAggregateTypePersistenceStrategy` how to map your Java Events into JSON in the Event Store.
(see the `PostgreSQL Event Store` section for details on configuring the `EventStore`)

```
var orders = AggregateType.of("Orders");
var ordersRepository = AggregateRootRepository.from(eventStore,
                                                    standardSingleTenantConfigurationUsingJackson(
                                                        orders,
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

# PostgreSQL Event Store

This library contains a fully features Event Store

## Concept

The primary concept of the EventStore are **Event Streams**   
Definition: An Event Stream is a collection of related Events *(e.g. Order events that are related to Order aggregate instances)*   
The most common denominator for Events in an Event Stream is the Type of Aggregate they're associated with.  
Classical examples of Aggregate Types and their associated events are:
- **Order** aggregate  
  *Examples of Order Events:*
  - OrderCreated
  - ProductAddedToOrder
  - ProductRemoveFromOrder
  - OrderAccepted
- **Account** aggregate  
  *Examples of Account Events:*
  - AccountRegistered
  - AccountCredited
  - AccountDebited
- **Customer** aggregate  
  *Examples of Customer Events:*
  - CustomerRegistered
  - CustomerMoved
  - CustomersAddressCorrected
  - CustomerStatusChanged

We could put all Events from all Aggregate Types into one Event Stream, but this is often not very useful:
- From a usage and use case perspective it makes more sense to subscribe and handle events related to the same type of Aggregates separate from the handling of other Events related to other types of Aggregates.  
  - E.g. it makes more sense to handle Order related Events separate from Account related Events
- Using the `SeparateTablePerAggregateTypePersistenceStrategy` we can store all Events related to a specific `AggregateType` in a separate table from other Aggregate types, which is more efficient and allows us to store many more Events related to this given `AggregateType`.  
  This allows use to use the PersistedEvent.globalEventOrder() to track the order in which Events, related to the same type of Aggregate, were persisted.    
  This also allows us to use the GlobalEventOrder as a natual Resume.Point for the EventStore subscriptions (see EventStoreSubscriptionManager)  

This aligns with the concept of the `AggregateEventStream` which contains Events related to a specific `AggregateType` with a distinct **AggregateId**  
When loading/fetching and persisting/appending Events we always work at the Aggregate instance level, i.e. with `AggregateEventStream`'s.  

The `AggregateType` is used for grouping/categorizing multiple `AggregateEventStream` instances related to similar types of aggregates.  
Unless you're using a fully functional style aggregate where you only perform a Left-Fold of all Events in an AggregateEventStream, then there will typically be 
a 1-1 relationship between an `AggregateType` and the class that implements the Aggregate.  

What's important here is that the `AggregateType` is only a name and shouldn't be confused with the Fully Qualified Class Name of the Aggregate implementation class.   
This is the classical split between the logical concept and the physical implementation.  
It's important to not link the Aggregate Implementation Class (the Fully Qualified Class Name) with the AggregateType name as that would make refactoring of your code 
base much harder, as the Fully Qualified Class Name then would be captured in the stored Events.  
Had the `AggregateType` and the Aggregate Implementation Class been one and the same, then moving the Aggregate class to another package or renaming it would break many 
things.   
To avoid the temptation to use the same name for both the AggregateType and the Aggregate Implementation Class, we prefer using the **plural name** of the Aggregate as the `AggregateType` name.  
Example:

| Aggregate-Type | Aggregate Implementation Class (Fully Qualified Class Name) | Top-level Event Type (Fully Qualified Class Name) |  
|----------------|-------------------------------------------------------------|---------------------------------------------------|
| Orders         | com.mycompany.project.persistence.Order                     | com.mycompany.project.persistence.OrderEvent      |
| Accounts       | com.mycompany.project.persistence.Account                   | com.mycompany.project.persistence.AccountEvent    |
| Customer       | com.mycompany.project.persistence.Customer                  | com.mycompany.project.persistence.CustomerEvent   |

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

Setup the EventStore using transaction/UnitOfWork management by the EventStore: `EventStoreManagedUnitOfWorkFactory`    
See `Spring-PostgreSQL Event Store` for a Spring oriented setup

```
var persistenceStrategy = new SeparateTablePerAggregateTypePersistenceStrategy(jdbi,
                                                                               new EventStoreManagedUnitOfWorkFactory(jdbi),
                                                                               new MyPersistableEventMapper());
eventStore = new PostgresqlEventStore<>(unitOfWorkFactory,
                                        persistenceStrategy);
```

## PersistableEventMapper

The `MyPersistableEventMapper` is a mapper that you need to write in order to provide a translation between generic Java based Events such as `OrderAdded`, `OrderAccepted` and the `PersistableEvent` type that
the `EventStore` knows how to persist.  
The custom `PersistableEventMapper` can also provide context specific information such as `Tenant`, `CorrelationId`, etc.  

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

What's important here is that the AggregateType is only a name and shouldn't be confused with the Fully Qualified Class Name of the Aggregate implementation class.  
This is the classical split between the logical concept and the physical implementation.  
It's important to not link the Aggregate Implementation Class (the Fully Qualified Class Name) with the AggregateType name as that would make refactoring of your code base much harder, as the Fully
Qualified Class Name then would be captured in the stored Events.   
Had the AggregateType and the Aggregate Implementation Class been one and the same, then moving the Aggregate class to another package or renaming it would break many things.

To avoid the temptation to use the same name for both the AggregateType and the Aggregate Implementation Class, we prefer using the plural name of the Aggregate as the AggregateType name.  
Example:

| Aggregate-Type | Aggregate Implementation Class (Fully Qualified Class Name) | Top-level Event Type (Fully Qualified Class Name) |  
|----------------|-------------------------------------------------------------|---------------------------------------------------|
| Orders         | com.mycompany.project.persistence.Order                     | com.mycompany.project.persistence.OrderEvent      |
| Accounts       | com.mycompany.project.persistence.Account                   | com.mycompany.project.persistence.AccountEvent    |
| Customer       | com.mycompany.project.persistence.Customer                  | com.mycompany.project.persistence.CustomerEvent   |

You can add as many `AggregateType` configurations as needed, but they need to be added BEFORE you try to persist or load events related to a given `AggregateType`.

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
To support storing the strong types, such as `EventId` used by the `PersistedEvent` type, the ObjectMapper needs to be configured with the
[Essential Types Jackson]|(https://github.com/cloudcreate-dk/essentials/tree/main/types-jackson) module's `EssentialTypesJacksonModule`.

Below is an example of an immutable Event design, which requires the `ObjectMapper` to be configured with
the [Essentials Immutable-Jackson](https://github.com/cloudcreate-dk/essentials/tree/main/immutable-jackson) module's `EssentialsImmutableJacksonModule`:

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
Example of appending the `OrderAdded` event, related to the `"Orders"` `AggregateType` with **aggregateId** specified by the `orderId` variable:
```
var orders = AggregateType.of("Order");

eventStore.unitOfWorkFactory().usingUnitOfWork(unitOfWork -> {
   var orderId = OrderId.random();
   eventStore.appendToStream(orders,
                             orderId,
                             new OrderAdded(orderId,
                                            CustomerId.random(),
                                            1234));
});
```

## Fetching Events from an AggregateType's EventStream
Example fetching an `AggregateEventStream` for the `"Orders"` `AggregateType` with **aggregateId** specified by the `orderId` variable:
```
var orders = AggregateType.of("Order");

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

You can also poll for events using the `EventStore` event polling mechanism, which allows you to subscribe to any point in an EventStream related to a given type of Aggregate:

```
var orders = AggregateType.of("Order");
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

### Subscribe asynchronously

Using asynchronous event subscription the `EventStoreSubscriptionManager` will keep track of where the individual Subscribers `ResumePoint` in the AggregateType EventStream's they subscribing to:

- `exclusivelySubscribeToAggregateEventsAsynchronously` - uses the `FencedLockManager` to ensure that only a single subscriber, with the same combination of `SubscriberId` and `AggregateType`, in the
  cluster can subscribe.
- `subscribeToAggregateEventsAsynchronously` - same as above, just without using the `FencedLockManager` to coordinate subscribers in a cluster

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

var orders = AggregateType.of("Order");
var productsSubscription = eventStoreSubscriptionManager.exclusivelySubscribeToAggregateEventsAsynchronously(
        SubscriberId.of("OrdersSub1"),
        orders,
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

When using 
- `EventStoreSubscriptionManager#exclusivelySubscribeToAggregateEventsAsynchronously(SubscriberId, AggregateType, GlobalEventOrder, Optional, PersistedEventHandler)`
- `EventStoreSubscriptionManager#subscribeToAggregateEventsAsynchronously(SubscriberId, AggregateType, GlobalEventOrder, Optional, PersistedEventHandler)`

then you can also use Event Pattern matching, using the `PatternMatchingPersistedEventHandler` to automatically call methods annotated with the `@SubscriptionEventHandler`
annotation and where the 1st argument matches the actual Event type (contained in the `PersistedEvent#event()`) provided to the `PersistedEventHandler#handle(PersistedEvent)` method:    
- If the `PersistedEvent#event()` contains a **typed/class based Event** then it matches on the 1st argument/parameter of the `@SubscriptionEventHandler` annotated method.  
- If the `PersistedEvent#event()` contains a **named Event**, then it matches on a `@SubscriptionEventHandle` annotated method that accepts a `String` as 1st argument.    

Each method may also include a 2nd argument that of type `PersistedEvent` in which case the event that's being matched is included as the 2nd argument in the call to the method.        
The methods can have any accessibility (private, public, etc.), they just have to be instance methods.  

```
public class MyEventHandler extends PatternMatchingPersistedEventHandler {

        @Override
        public void onResetFrom(GlobalEventOrder globalEventOrder) {

        }

        @SubscriptionEventHandler
        public void handle(OrderEvent.OrderAdded orderAdded) {
            ...
        }

        @SubscriptionEventHandler
        private void handle(OrderEvent.ProductAddedToOrder productAddedToOrder) {
          ...
        }
       
        @SubscriptionEventHandler
        private void handle(OrderEvent.ProductRemovedFromOrder productRemovedFromOrder, PersistedEvent productRemovedFromOrderPersistedEvent) {
          ...
        }

        @SubscriptionEventHandler
        private void handle(String json, PersistedEvent jsonPersistedEvent) {
          ...
        }

}
```

### Subscribe synchronously

Synchronous subscription allows you to receive and react to Events published within the active Transaction/`UnitOfWork` that's involved in `appending` the events to the `EventStream`
This can be useful for certain transactional views/projections where you require transactional consistency (e.g. assigning a sequential customer number, etc.):

- `subscribeToAggregateEventsInTransaction`

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

var productsSubscription = eventStoreSubscriptionManager.subscribeToAggregateEventsInTransaction(
        SubscriberId.of("ProductSubscriber"),
        AggregateType.of("Products"),
        Optional.empty(),
        new TransactionalPersistedEventHandler() {
            @Override
            public void handle(PersistedEvent event, UnitOfWork unitOfWork) {
               ...
            }
        });
```

When using
- `EventStoreSubscriptionManager#subscribeToAggregateEventsInTransaction(SubscriberId, AggregateType, Optional, TransactionalPersistedEventHandler)`

then you can also use Event Pattern matching, using the pattern matching `TransactionalPersistedEventHandler`.  
The `PatternMatchingTransactionalPersistedEventHandler` will automatically call methods annotated with the `@SubscriptionEventHandler` annotation and
where the 1st argument matches the actual Event type (contained in the `PersistedEvent#event()` provided to the `PersistedEventHandler#handle(PersistedEvent)` method
and where the 2nd argument is a `UnitOfWork`:

- If the `PersistedEvent#event()` contains a **typed/class based Event** then it matches on the 1st argument/parameter of the `@SubscriptionEventHandler` annotated method.
- If the `PersistedEvent#event()` contains a **named Event**, then it matches on a `@SubscriptionEventHandle` annotated method that accepts a `String` as 1st argument.    

Each method may also include a 3rd argument that of type `PersistedEvent` in which case the event that's being matched is included as the 3rd argument in the call to the method.  
The methods can have any accessibility (private, public, etc.), they just have to be instance methods.

Example:
```
public class MyEventHandler extends PatternMatchingTransactionalPersistedEventHandler {

        @SubscriptionEventHandler
        public void handle(OrderEvent.OrderAdded orderAdded, UnitOfWork unitOfWork) {
            ...
        }

        @SubscriptionEventHandler
        private void handle(OrderEvent.ProductAddedToOrder productAddedToOrder, UnitOfWork unitOfWork) {
          ...
        }

        @SubscriptionEventHandler
        private void handle(OrderEvent.ProductRemovedFromOrder productRemovedFromOrder, UnitOfWork unitOfWork, PersistedEvent productRemovedFromOrderPersistedEvent) {
          ...
        }

        @SubscriptionEventHandler
        private void handle(String json, UnitOfWork unitOfWork, PersistedEvent jsonPersistedEvent) {
          ...
        }
}
```

To use `Postgresql Event Store` just add the following Maven dependency:

```
<dependency>
    <groupId>dk.cloudcreate.essentials.components</groupId>
    <artifactId>postgresql-event-store</artifactId>
    <version>0.1.0</version>
</dependency>
```

## Features coming soon

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

## PostgreSQL Durable Queue

This library focuses purely on providing a durable Queue with support for DLQ (Dead Letter Queue) and message redelivery Coming soon