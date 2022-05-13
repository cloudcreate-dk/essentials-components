/*
 * Copyright 2021-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.eventstream;

import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.EventStore;
import dk.cloudcreate.essentials.components.eventsourced.eventstore.postgresql.InMemoryProjector;
import dk.cloudcreate.essentials.types.*;

/**
 * Provides an aggregate type category that an {@link AggregateEventStream} belongs to<br>
 * The {@link AggregateType} is used for grouping/categorizing multiple {@link AggregateEventStream} instances related to
 * similar types of aggregates. This allows us to easily retrieve or be notified of new Events related to the same type of Aggregates.<br>
 * <b>Note: The aggregate type is only a name and shouldn't be confused with the Fully Qualified Class Name of an Aggregate implementation class. At the {@link EventStore}
 * this is supported as an <b>In Memory Projection</b> - see {@link InMemoryProjector}</b>
 * E.g. the {@link AggregateEventStream}'s associated with an <b>Order</b> Aggregate can all be grouped by sharing the same {@link AggregateType} named <b>Orders</b><br>
 * The {@link AggregateType} is typically the plural name of the type of domain-object/aggregate that
 * the stream is storing events for.<br>
 * Example: if we store debit and credit events related to an Account, then the {@link AggregateType}
 * would typically be "Accounts" or "accounts"
 */
public class AggregateType extends CharSequenceType<AggregateType> implements Identifier {
    public AggregateType(CharSequence value) {
        super(value);
    }

    public static AggregateType of(CharSequence value) {
        return new AggregateType(value);
    }
}
