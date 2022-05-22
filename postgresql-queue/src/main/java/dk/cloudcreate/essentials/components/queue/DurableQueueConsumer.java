package dk.cloudcreate.essentials.components.queue;

import dk.cloudcreate.essentials.components.common.Lifecycle;

public interface DurableQueueConsumer extends Lifecycle {
    QueueName queueName();

    void cancel();
}
