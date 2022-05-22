package dk.cloudcreate.essentials.components.common.transaction;

public class NoActiveUnitOfWorkException extends UnitOfWorkException {
    public NoActiveUnitOfWorkException() {
    }

    public NoActiveUnitOfWorkException(String message) {
        super(message);
    }

    public NoActiveUnitOfWorkException(String message, Throwable cause) {
        super(message, cause);
    }

    public NoActiveUnitOfWorkException(Throwable cause) {
        super(cause);
    }

    public NoActiveUnitOfWorkException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
