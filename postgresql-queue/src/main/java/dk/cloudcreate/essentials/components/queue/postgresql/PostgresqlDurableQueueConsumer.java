package dk.cloudcreate.essentials.components.queue.postgresql;

import dk.cloudcreate.essentials.components.common.transaction.*;
import dk.cloudcreate.essentials.components.queue.*;
import dk.cloudcreate.essentials.shared.concurrent.ThreadFactoryBuilder;
import org.slf4j.*;

import java.util.concurrent.*;

import static dk.cloudcreate.essentials.shared.FailFast.*;
import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

public class PostgresqlDurableQueueConsumer implements DurableQueueConsumer {
    private static final Logger log = LoggerFactory.getLogger(PostgresqlDurableQueueConsumer.class);

    private final QueueRedeliveryPolicy                                         redeliveryPolicy;
    private final QueueName                                                     queueName;
    private final QueuedMessageHandler                                          queuedMessageHandler;
    private final ScheduledExecutorService                                      scheduler;
    private final PostgresqlDurableQueues                                       postgresqlDurableQueues;
    private final HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory;

    private volatile boolean started;

    public PostgresqlDurableQueueConsumer(QueueName queueName,
                                          QueuedMessageHandler queuedMessageHandler,
                                          QueueRedeliveryPolicy redeliveryPolicy,
                                          int parallelMessageConsumers,
                                          HandleAwareUnitOfWorkFactory<? extends HandleAwareUnitOfWork> unitOfWorkFactory,
                                          PostgresqlDurableQueues postgresqlDurableQueues) {
        this.queueName = requireNonNull(queueName, "queueName is missing");
        this.queuedMessageHandler = requireNonNull(queuedMessageHandler, "You must specify a queuedMessageHandler");
        this.redeliveryPolicy = requireNonNull(redeliveryPolicy, "You must specify a redelivery policy");
        this.unitOfWorkFactory = requireNonNull(unitOfWorkFactory, "You must specify a unitOfWorkFactory");
        this.postgresqlDurableQueues = requireNonNull(postgresqlDurableQueues, "postgresqlDurableQueues is missing");

        requireTrue(parallelMessageConsumers >= 1, "You must specify a number of parallelMessageConsumers >= 1");
        this.scheduler = Executors.newScheduledThreadPool(parallelMessageConsumers,
                                                          new ThreadFactoryBuilder()
                                                                  .nameFormat("Queue-" + queueName + "-Polling-%d")
                                                                  .daemon(true)
                                                                  .build());
    }

    @Override
    public void start() {
        if (!started) {
            log.info("[{}] Starting DurableQueueConsumer with polling interval {} (based on initialRedeliveryDelay)",
                     queueName,
                     redeliveryPolicy.initialRedeliveryDelay);
            scheduler.scheduleAtFixedRate(this::pollQueue,
                                          redeliveryPolicy.initialRedeliveryDelay.toMillis(),
                                          redeliveryPolicy.initialRedeliveryDelay.toMillis(),
                                          TimeUnit.MILLISECONDS);
            started = true;
        }
    }

    @Override
    public void stop() {
        if (started) {
            log.info("[{}] Stopping DurableQueueConsumer", queueName);
            scheduler.shutdownNow();
            started = false;
            postgresqlDurableQueues.removeQueueConsumer(this);
            log.info("[{}] DurableQueueConsumer stopped", queueName);
        }
    }

    @Override
    public boolean isStarted() {
        return started;
    }

    @Override
    public QueueName queueName() {
        return queueName;
    }

    @Override
    public void cancel() {
        stop();
    }

    private void pollQueue() {
        log.trace("[{}] Polling Queue for the next message ready for delivery", queueName);
        unitOfWorkFactory.usingUnitOfWork(handleAwareUnitOfWork -> {
            try {
                postgresqlDurableQueues.getNextMessageReadyForDelivery(queueName)
                                       .map(queuedMessage -> {
                                           log.debug("[{}:{}] Delivering message. Total attempts: {}, Redelivery Attempts: {}",
                                                     queueName,
                                                     queuedMessage.id,
                                                     queuedMessage.totalDeliveryAttempts,
                                                     queuedMessage.redeliveryAttempts);
                                           try {
                                               queuedMessageHandler.handle(queuedMessage);
                                               log.debug("[{}:{}] Message handled successfully. Deleting the message in the Queue Store message. Total attempts: {}, Redelivery Attempts: {}",
                                                         queueName,
                                                         queuedMessage.id,
                                                         queuedMessage.totalDeliveryAttempts,
                                                         queuedMessage.redeliveryAttempts);
                                               return postgresqlDurableQueues.deleteMessage(queuedMessage.id);
                                           } catch (Exception e) {
                                               log.debug(msg("[{}:{}] QueueMessageHandler for failed to handle: {}",
                                                             queueName,
                                                             queuedMessage.id,
                                                             queuedMessage), e);
                                               if (queuedMessage.totalDeliveryAttempts >= redeliveryPolicy.maximumNumberOfRedeliveries + 1) {
                                                   // Dead letter
                                                   log.debug("[{}:{}] Marking Message as Dead Letter: {}",
                                                             queueName,
                                                             queuedMessage.id,
                                                             queuedMessage);
                                                   return postgresqlDurableQueues.markAsDeadLetterMessage(queuedMessage.id, e);
                                               } else {
                                                   // Redeliver later
                                                   var redeliveryDelay = redeliveryPolicy.calculateNextRedeliveryDelay(queuedMessage.redeliveryAttempts);
                                                   log.debug(msg("[{}:{}] Using redeliveryDelay '{}' for QueueEntryId '{}' due to: {}",
                                                                 queueName,
                                                                 queuedMessage.id,
                                                                 redeliveryDelay,
                                                                 queuedMessage.id,
                                                                 e.getMessage()));
                                                   return postgresqlDurableQueues.retryMessage(queuedMessage.id,
                                                                                               e,
                                                                                               redeliveryDelay);
                                               }
                                           }
                                       });
            } catch (Exception e) {
                log.error(msg("[{}] Error Polling Queue", queueName), e);
            }
        });
    }

}
