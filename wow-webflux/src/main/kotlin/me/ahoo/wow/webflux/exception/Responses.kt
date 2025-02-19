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

package me.ahoo.wow.webflux.exception

import me.ahoo.wow.api.exception.ErrorInfo
import me.ahoo.wow.exception.asErrorInfo
import me.ahoo.wow.openapi.command.CommandHeaders.WOW_ERROR_CODE
import me.ahoo.wow.serialization.asJsonString
import me.ahoo.wow.webflux.exception.ErrorHttpStatusMapping.asHttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Mono

fun Throwable.asResponseEntity(): ResponseEntity<ErrorInfo> {
    val errorInfo = asErrorInfo()
    val status = errorInfo.asHttpStatus()
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .header(WOW_ERROR_CODE, errorInfo.errorCode)
        .body(errorInfo)
}

fun ErrorInfo.asServerResponse(): Mono<ServerResponse> {
    val status = asHttpStatus()
    return ServerResponse.status(status)
        .contentType(MediaType.APPLICATION_JSON)
        .header(WOW_ERROR_CODE, errorCode)
        .bodyValue(this.asJsonString())
}

fun Mono<*>.asServerResponse(exceptionHandler: ExceptionHandler = DefaultExceptionHandler): Mono<ServerResponse> {
    return flatMap {
        if (it is ErrorInfo) {
            return@flatMap it.asServerResponse()
        }
        ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .header(WOW_ERROR_CODE, ErrorInfo.SUCCEEDED)
            .bodyValue(it.asJsonString())
    }.onErrorResume {
        exceptionHandler.handle(it)
    }
}
