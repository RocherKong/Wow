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

package me.ahoo.wow.eventsourcing.mock

import me.ahoo.wow.api.modeling.AggregateId
import me.ahoo.wow.eventsourcing.snapshot.InMemorySnapshotRepository
import me.ahoo.wow.eventsourcing.snapshot.Snapshot
import me.ahoo.wow.eventsourcing.snapshot.SnapshotRepository
import me.ahoo.wow.infra.Decorator
import reactor.core.publisher.Mono
import java.time.Duration

class DelaySnapshotRepository(
    private val delaySupplier: () -> Duration = { Duration.ofMillis(5) },
    override val delegate: SnapshotRepository = InMemorySnapshotRepository()
) :
    SnapshotRepository, Decorator<SnapshotRepository> {
    override fun <S : Any> load(aggregateId: AggregateId): Mono<Snapshot<S>> {
        return delegate.load<S>(aggregateId).delaySubscription(delaySupplier())
    }

    override fun <S : Any> save(snapshot: Snapshot<S>): Mono<Void> {
        return delegate.save(snapshot).delaySubscription(delaySupplier())
    }
}
