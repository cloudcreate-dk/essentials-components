package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.serializer;

import dk.cloudcreate.essentials.components.common.types.*;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import org.jdbi.v3.core.argument.ArgumentFactory;

import java.util.Optional;

import static dk.cloudcreate.essentials.shared.MessageFormatter.msg;

/**
 * Encapsulates how a {@link Tenant} value related to a given Event can be serialized to a
 * single string value that fits into the {@link EventStore}'s
 * concept of storing a tenant (id) as single column that contains a String value.<br>
 * The actual Postgresql column type is always a <code>TEXT</code> column<br>
 *
 * @see NoSupportForMultiTenancySerializer
 * @see TenantIdSerializer
 */
public interface TenantSerializer<T extends Tenant> {
    /**
     * The Java type of the {@link Tenant}
     */
    Class<?> tenantType();

    /**
     * Serializes a typed {@link Tenant} instance to a String
     *
     * @param tenant the tenant
     * @return the string version of the tenant value or null if the tenant value is null
     */
    String serialize(T tenant);

    /**
     * Deserializes a string version of tenant to a typed
     * {@link Tenant} value
     *
     * @param tenant the string version of the tenant value
     * @return the type version of the tenant value or {@link Optional#empty()} if the string value is null
     */
    Optional<T> deserialize(String tenant);

    /**
     * Serializer for the {@link TenantId} type
     */
    class TenantIdSerializer implements TenantSerializer<TenantId> {

        @Override
        public Class<?> tenantType() {
            return TenantId.class;
        }

        @Override
        public String serialize(TenantId tenant) {
            if (tenant == null) {
                return null;
            }
            return tenant.toString();
        }

        @Override
        public Optional<TenantId> deserialize(String tenant) {
            if (tenant == null) {
                return Optional.empty();
            }
            return Optional.of(TenantId.of(tenant));
        }
    }

    /**
     * Default serializer for single tenant systems, i.e. a setup where there isn't support for multi tenancy, where there isn't any concrete {@link Tenant}
     * defined and not a single Event is associated with a {@link Tenant} instance.<br>
     * If this serializer is presented with a {@link Tenant} (serializer or deserialized) it will fail
     * with an exception
     */
    class NoSupportForMultiTenancySerializer implements TenantSerializer<Tenant> {
        @Override
        public Class<?> tenantType() {
            return TenantId.class;
        }

        @Override
        public String serialize(Tenant tenant) {
            if (tenant != null) {
                throw new IllegalArgumentException(msg("A tenant value of type {} was non-null. You must supply a different {} as this serializer expects no {} values",
                                                       tenant.getClass().getName(),
                                                       TenantSerializer.class.getName(),
                                                       Tenant.class.getSimpleName()));
            }
            return null;
        }

        @Override
        public Optional<Tenant> deserialize(String tenant) {
            if (tenant != null) {
                throw new IllegalArgumentException(msg("A tenant value was non-null. You must supply a different {} as this serializer expects no {} values",
                                                       TenantSerializer.class.getName(),
                                                       Tenant.class.getSimpleName()));
            }
            return Optional.empty();
        }
    }
}
