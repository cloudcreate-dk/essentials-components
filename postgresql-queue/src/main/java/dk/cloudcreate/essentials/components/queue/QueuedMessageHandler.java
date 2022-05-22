package dk.cloudcreate.essentials.components.queue;

public interface QueuedMessageHandler {
    void handle(QueuedMessage queueMessage);
}
