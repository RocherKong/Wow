/*
 * Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.ahoo.wow.r2dbc

import io.r2dbc.spi.Connection
import io.r2dbc.spi.Readable
import me.ahoo.wow.api.modeling.AggregateId
import me.ahoo.wow.eventsourcing.snapshot.SimpleSnapshot
import me.ahoo.wow.eventsourcing.snapshot.Snapshot
import me.ahoo.wow.eventsourcing.snapshot.SnapshotRepository
import me.ahoo.wow.infra.TypeNameMapper.asType
import me.ahoo.wow.modeling.annotation.asStateAggregateMetadata
import me.ahoo.wow.modeling.state.StateAggregate.Companion.asStateAggregate
import me.ahoo.wow.serialization.asJsonString
import me.ahoo.wow.serialization.asObject
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

class R2dbcSnapshotRepository(
    private val database: Database,
    private val snapshotSchema: SnapshotSchema
) : SnapshotRepository {

    override fun <S : Any> load(
        aggregateId: AggregateId
    ): Mono<Snapshot<S>> {
        return Flux.usingWhen(
            /* resourceSupplier = */
            database.createConnection(aggregateId),
            /* resourceClosure = */
            {
                it.createStatement(snapshotSchema.load(aggregateId))
                    .bind(0, aggregateId.id)
                    .execute()
            },
            Connection::close,
        )
            .flatMap {
                it.map { readable ->
                    mapSnapshot<S>(
                        aggregateId = aggregateId,
                        expectedVersion = null,
                        readable = readable,
                    )
                }
            }
            .next()
    }

    private fun <S : Any> mapSnapshot(
        aggregateId: AggregateId,
        expectedVersion: Int?,
        readable: Readable
    ): Snapshot<S> {
        val actualAggregateId = checkNotNull(readable.get("aggregate_id", String::class.java))
        require(aggregateId.id == actualAggregateId)
        val tenantId = checkNotNull(readable.get("tenant_id", String::class.java))
        require(tenantId == aggregateId.tenantId) {
            "The aggregated tenantId[${aggregateId.tenantId}] does not match the tenantId:[$tenantId] stored in the eventStore"
        }
        val actualVersion = checkNotNull(readable.get("version", Int::class.java))
        expectedVersion?.let {
            check(actualVersion == expectedVersion)
        }
        val eventId = readable.get("event_id", String::class.java).orEmpty()
        val firstOperator = readable.get("first_operator", String::class.java).orEmpty()
        val operator = readable.get("operator", String::class.java).orEmpty()
        val firstEventTime = readable.get("first_event_time", Long::class.java) ?: 0L
        val eventTime = readable.get("event_time", Long::class.java) ?: 0L
        val snapshotTime = checkNotNull(readable.get("snapshot_time", Long::class.java))
        val metadata = checkNotNull(readable.get("state_type", String::class.java)).asType<S>()
            .asStateAggregateMetadata()
        val state = checkNotNull(readable.get("state", String::class.java))
        val stateRoot = state.asObject(metadata.aggregateType)
        val deleted = checkNotNull(readable.get("deleted", Boolean::class.java))
        return SimpleSnapshot(
            delegate = metadata.asStateAggregate(
                aggregateId = aggregateId,
                state = stateRoot,
                version = actualVersion,
                eventId = eventId,
                firstOperator = firstOperator,
                operator = operator,
                firstEventTime = firstEventTime,
                eventTime = eventTime,
                deleted = deleted,
            ),
            snapshotTime = snapshotTime,
        )
    }

    override fun <S : Any> save(snapshot: Snapshot<S>): Mono<Void> {
        return Flux.usingWhen(
            database.createConnection(snapshot.aggregateId),
            {
                it.createStatement(snapshotSchema.save(snapshot.aggregateId))
                    .bind(0, snapshot.aggregateId.id)
                    .bind(1, snapshot.aggregateId.tenantId)
                    .bind(2, snapshot.version)
                    .bind(3, snapshot.state.javaClass.name)
                    .bind(4, snapshot.state.asJsonString())
                    .bind(5, snapshot.eventId)
                    .bind(6, snapshot.firstOperator)
                    .bind(7, snapshot.operator)
                    .bind(8, snapshot.firstEventTime)
                    .bind(9, snapshot.eventTime)
                    .bind(10, snapshot.snapshotTime)
                    .bind(11, snapshot.deleted)
                    .execute()
            },
            Connection::close,
        )
            .flatMap { it.rowsUpdated }
            .then()
    }
}
