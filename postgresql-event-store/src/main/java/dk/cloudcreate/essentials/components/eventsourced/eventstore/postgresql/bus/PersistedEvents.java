package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.bus;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream.PersistedEvent;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.transaction.UnitOfWork;

import java.util.List;

import static dk.cloudcreate.essentials.shared.FailFast.requireNonNull;

/**
 * Encapsulates all events Persisted within a {@link UnitOfWork}
 */
public class PersistedEvents {
    public final CommitStage          commitStage;
    public final UnitOfWork           unitOfWork;
    public final List<PersistedEvent> events;

    public PersistedEvents(CommitStage commitStage, UnitOfWork unitOfWork, List<PersistedEvent> events) {
        this.commitStage = requireNonNull(commitStage, "No commitStage provided");
        this.unitOfWork = requireNonNull(unitOfWork, "No unitOfWork provided");
        this.events = requireNonNull(events, "No events provided");
    }

    @Override
    public String toString() {
        return "PersistedEvents{" +
                "commitStage=" + commitStage + ", " +
                "events=" + events.size() +
                '}';
    }
}
