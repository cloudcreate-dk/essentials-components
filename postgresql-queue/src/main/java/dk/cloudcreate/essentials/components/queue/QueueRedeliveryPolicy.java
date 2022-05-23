package dk.cloudcreate.essentials.components.queue;

import java.time.Duration;

import static dk.cloudcreate.essentials.shared.FailFast.*;

public class QueueRedeliveryPolicy {
    public final Duration initialRedeliveryDelay;
    public final Duration followupRedeliveryDelay;
    public final double   followupRedeliveryDelayMultiplier;
    public final Duration maximumFollowupRedeliveryThreshold;
    public final int      maximumNumberOfRedeliveries;

    public QueueRedeliveryPolicy(Duration initialRedeliveryDelay,
                                 Duration followupRedeliveryDelay,
                                 double followupRedeliveryDelayMultiplier,
                                 Duration maximumFollowupRedeliveryDelayThreshold,
                                 int maximumNumberOfRedeliveries) {
        this.initialRedeliveryDelay = requireNonNull(initialRedeliveryDelay, "You must specify an initialRedeliveryDelay");
        this.followupRedeliveryDelay = requireNonNull(followupRedeliveryDelay, "You must specify an followupRedeliveryDelay");
        this.followupRedeliveryDelayMultiplier = followupRedeliveryDelayMultiplier;
        this.maximumFollowupRedeliveryThreshold = requireNonNull(maximumFollowupRedeliveryDelayThreshold, "You must specify an maximumFollowupRedeliveryDelayThreshold");
        this.maximumNumberOfRedeliveries = maximumNumberOfRedeliveries;
    }

    public Duration calculateNextRedeliveryDelay(int currentNumberOfRedeliveryAttempts) {
        requireTrue(currentNumberOfRedeliveryAttempts >= 0, "currentNumberOfRedeliveryAttempts must be 0 or larger");
        if (currentNumberOfRedeliveryAttempts == 0) {
            return initialRedeliveryDelay;
        }
        var calculatedRedeliveryDelay = initialRedeliveryDelay.plus(Duration.ofMillis((long) (followupRedeliveryDelay.toMillis() * followupRedeliveryDelayMultiplier)));
        if (calculatedRedeliveryDelay.compareTo(maximumFollowupRedeliveryThreshold) >= 0) {
            return maximumFollowupRedeliveryThreshold;
        } else {
            return calculatedRedeliveryDelay;
        }
    }

    public static QueueRedeliveryPolicy fixedBackoff(Duration redeliveryDelay,
                                                     int maximumNumberOfRedeliveries) {
        return new QueueRedeliveryPolicy(redeliveryDelay,
                                         redeliveryDelay,
                                         1.0d,
                                         redeliveryDelay,
                                         maximumNumberOfRedeliveries);
    }

    public static QueueRedeliveryPolicy linearBackoff(Duration redeliveryDelay,
                                                      Duration maximumFollowupRedeliveryDelayThreshold,
                                                      int maximumNumberOfRedeliveries) {
        return new QueueRedeliveryPolicy(redeliveryDelay,
                                         redeliveryDelay,
                                         1.0d,
                                         maximumFollowupRedeliveryDelayThreshold,
                                         maximumNumberOfRedeliveries);
    }

    public static QueueRedeliveryPolicy exponentialBackoff(Duration initialRedeliveryDelay,
                                                           Duration followupRedeliveryDelay,
                                                           double followupRedeliveryDelayMultiplier,
                                                           Duration maximumFollowupRedeliveryDelayThreshold,
                                                           int maximumNumberOfRedeliveries) {
        return new QueueRedeliveryPolicy(initialRedeliveryDelay,
                                         followupRedeliveryDelay,
                                         followupRedeliveryDelayMultiplier,
                                         maximumFollowupRedeliveryDelayThreshold,
                                         maximumNumberOfRedeliveries);
    }

}
