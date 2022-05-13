package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.subscription;

import dk.cloudcreate.essentials.components.common.types.SubscriberId;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.AggregateType;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.types.GlobalEventOrder;

import java.time.OffsetDateTime;
import java.util.Objects;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

public class SubscriptionResumePoint {
    private final SubscriberId     subscriberId;
    private final AggregateType    aggregateType;
    private       GlobalEventOrder resumeFromAndIncluding;
    private       OffsetDateTime   lastUpdated;
    private       boolean          changed;

    public SubscriptionResumePoint(SubscriberId subscriberId, AggregateType aggregateType, GlobalEventOrder resumeFromAndIncluding, OffsetDateTime lastUpdated) {
        this.subscriberId = requireNonNull(subscriberId, "No subscriberId provided");
        this.aggregateType = requireNonNull(aggregateType, "No aggregateType provided");
        this.resumeFromAndIncluding = requireNonNull(resumeFromAndIncluding, "No resumeFromAndIncluding provided");
        this.lastUpdated = requireNonNull(lastUpdated, "No lastUpdated provided");
    }

    public SubscriberId getSubscriberId() {
        return subscriberId;
    }

    public AggregateType getAggregateType() {
        return aggregateType;
    }

    public GlobalEventOrder getResumeFromAndIncluding() {
        return resumeFromAndIncluding;
    }

    public OffsetDateTime getLastUpdated() {
        return lastUpdated;
    }

    public SubscriptionResumePoint setResumeFromAndIncluding(GlobalEventOrder resumeFromAndIncluding) {
        requireNonNull(resumeFromAndIncluding, "No resumeFromAndIncluding provided");
        if (!Objects.equals(this.resumeFromAndIncluding, resumeFromAndIncluding)) {
            changed = true;
        }
        this.resumeFromAndIncluding = resumeFromAndIncluding;
        return this;
    }

    public SubscriptionResumePoint setLastUpdated(OffsetDateTime lastUpdated) {
        this.lastUpdated = requireNonNull(lastUpdated, "No lastUpdated provided");
        changed = false;
        return this;
    }

    public boolean isChanged() {
        return changed;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SubscriptionResumePoint)) return false;
        SubscriptionResumePoint that = (SubscriptionResumePoint) o;
        return subscriberId.equals(that.subscriberId) && aggregateType.equals(that.aggregateType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subscriberId, aggregateType);
    }

    @Override
    public String toString() {
        return "SubscriptionResumePoint{" +
                "subscriberId=" + subscriberId +
                ", aggregateType=" + aggregateType +
                ", resumeFromAndIncluding=" + resumeFromAndIncluding +
                ", lastUpdated=" + lastUpdated +
                '}';
    }
}
