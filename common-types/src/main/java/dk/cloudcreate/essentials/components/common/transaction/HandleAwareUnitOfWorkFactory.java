package dk.cloudcreate.essentials.components.common.transaction;

/**
 * Specialization of {@link UnitOfWorkFactory} that created and maintains {@link HandleAwareUnitOfWork}'s
 */
public interface HandleAwareUnitOfWorkFactory<UOW extends HandleAwareUnitOfWork> extends UnitOfWorkFactory<UOW> {
}
