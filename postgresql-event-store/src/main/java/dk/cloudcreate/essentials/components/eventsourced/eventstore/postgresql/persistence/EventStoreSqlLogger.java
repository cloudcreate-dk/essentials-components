package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.persistence;

import org.jdbi.v3.core.statement.StatementContext;
import org.slf4j.*;

import java.sql.SQLException;
import java.time.Duration;

import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

public class EventStoreSqlLogger implements org.jdbi.v3.core.statement.SqlLogger {
    private final Logger log;

    public EventStoreSqlLogger() {
        log = LoggerFactory.getLogger("EventStore.Sql");
    }

    @Override
    public void logBeforeExecution(StatementContext context) {

    }

    @Override
    public void logAfterExecution(StatementContext context) {
        if (log.isTraceEnabled()) {
            log.trace("Execution time: {} ms - {}", Duration.between(context.getExecutionMoment(), context.getCompletionMoment()).toMillis(), context.getRenderedSql());
        }
    }

    @Override
    public void logException(StatementContext context, SQLException ex) {
        log.error(msg("Failed Execution time: {} ms - {}", Duration.between(context.getExecutionMoment(), context.getExceptionMoment()).toMillis(), context.getRenderedSql()), ex);
    }
}
