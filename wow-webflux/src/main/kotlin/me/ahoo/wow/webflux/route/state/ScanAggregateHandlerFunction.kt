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

package me.ahoo.wow.webflux.route.state

import me.ahoo.wow.eventsourcing.EventStore
import me.ahoo.wow.modeling.matedata.AggregateMetadata
import me.ahoo.wow.modeling.state.StateAggregateRepository
import me.ahoo.wow.openapi.RoutePaths
import me.ahoo.wow.openapi.state.ScanAggregateRouteSpec
import me.ahoo.wow.webflux.exception.ExceptionHandler
import me.ahoo.wow.webflux.exception.asServerResponse
import me.ahoo.wow.webflux.route.RouteHandlerFunctionFactory
import org.springframework.web.reactive.function.server.HandlerFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

class ScanAggregateHandlerFunction(
    private val aggregateMetadata: AggregateMetadata<*, *>,
    private val stateAggregateRepository: StateAggregateRepository,
    private val eventStore: EventStore,
    private val exceptionHandler: ExceptionHandler
) : HandlerFunction<ServerResponse> {

    override fun handle(request: ServerRequest): Mono<ServerResponse> {
        val cursorId = request.pathVariable(RoutePaths.BATCH_CURSOR_ID)
        val limit = request.pathVariable(RoutePaths.BATCH_LIMIT).toInt()
        return eventStore.scanAggregateId(
            namedAggregate = aggregateMetadata.namedAggregate,
            cursorId = cursorId,
            limit = limit,
        ).flatMapSequential {
            stateAggregateRepository.load<Any>(it)
        }.filter {
            it.initialized && !it.deleted
        }.map { it.state }
            .collectList()
            .asServerResponse(exceptionHandler)
    }
}

class ScanAggregateHandlerFunctionFactory(
    private val stateAggregateRepository: StateAggregateRepository,
    private val eventStore: EventStore,
    private val exceptionHandler: ExceptionHandler
) : RouteHandlerFunctionFactory<ScanAggregateRouteSpec> {
    override val supportedSpec: Class<ScanAggregateRouteSpec>
        get() = ScanAggregateRouteSpec::class.java

    override fun create(spec: ScanAggregateRouteSpec): HandlerFunction<ServerResponse> {
        return ScanAggregateHandlerFunction(
            aggregateMetadata = spec.aggregateMetadata,
            stateAggregateRepository = stateAggregateRepository,
            eventStore = eventStore,
            exceptionHandler = exceptionHandler,
        )
    }
}
