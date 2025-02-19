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

package me.ahoo.wow.webflux.route.event.state

import me.ahoo.wow.event.compensation.StateEventCompensator
import me.ahoo.wow.eventsourcing.EventStore
import me.ahoo.wow.messaging.compensation.CompensationConfig
import me.ahoo.wow.modeling.matedata.AggregateMetadata
import me.ahoo.wow.openapi.RoutePaths
import me.ahoo.wow.openapi.event.state.RegenerateStateEventRouteSpec
import me.ahoo.wow.webflux.exception.ExceptionHandler
import me.ahoo.wow.webflux.exception.asServerResponse
import me.ahoo.wow.webflux.route.RouteHandlerFunctionFactory
import org.springframework.web.reactive.function.server.HandlerFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

class RegenerateStateEventFunction(
    private val aggregateMetadata: AggregateMetadata<*, *>,
    private val eventStore: EventStore,
    private val stateEventCompensator: StateEventCompensator,
    private val exceptionHandler: ExceptionHandler
) : HandlerFunction<ServerResponse> {
    private val handler =
        RegenerateStateEventHandler(aggregateMetadata, eventStore, stateEventCompensator)

    override fun handle(request: ServerRequest): Mono<ServerResponse> {
        val cursorId = request.pathVariable(RoutePaths.BATCH_CURSOR_ID)
        val limit = request.pathVariable(RoutePaths.BATCH_LIMIT).toInt()
        return request.bodyToMono(CompensationConfig::class.java)
            .flatMap {
                handler.handle(it, cursorId, limit)
            }
            .asServerResponse(exceptionHandler)
    }
}

class RegenerateStateEventFunctionFactory(
    private val eventStore: EventStore,
    private val stateEventCompensator: StateEventCompensator,
    private val exceptionHandler: ExceptionHandler
) : RouteHandlerFunctionFactory<RegenerateStateEventRouteSpec> {
    override val supportedSpec: Class<RegenerateStateEventRouteSpec>
        get() = RegenerateStateEventRouteSpec::class.java

    override fun create(spec: RegenerateStateEventRouteSpec): HandlerFunction<ServerResponse> {
        return RegenerateStateEventFunction(
            aggregateMetadata = spec.aggregateMetadata,
            eventStore = eventStore,
            stateEventCompensator = stateEventCompensator,
            exceptionHandler = exceptionHandler,
        )
    }
}
