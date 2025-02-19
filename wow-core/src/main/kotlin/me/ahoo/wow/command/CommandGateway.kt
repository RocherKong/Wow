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

package me.ahoo.wow.command

import me.ahoo.wow.command.wait.CommandStage
import me.ahoo.wow.command.wait.WaitStrategy
import me.ahoo.wow.command.wait.WaitingFor
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono

const val COMMAND_GATEWAY_PROCESSOR_NAME = "CommandGateway"

/**
 * Command Gateway .
 *
 *
 * @author ahoo wang
 * @see CommandBus
 *
 */
interface CommandGateway : CommandBus {

    fun <C : Any> send(
        command: CommandMessage<C>,
        waitStrategy: WaitStrategy
    ): Mono<out ClientCommandExchange<C>>

    @Throws(CommandResultException::class)
    fun <C : Any> sendAndWait(
        command: CommandMessage<C>,
        waitStrategy: WaitStrategy
    ): Mono<CommandResult> {
        return send(command, waitStrategy)
            .onErrorMap {
                CommandResultException(it.asResult(command, processorName = COMMAND_GATEWAY_PROCESSOR_NAME))
            }
            .flatMap {
                waitStrategy.waiting()
                    .map { waitSignal ->
                        waitSignal.asResult(it.message)
                            .apply {
                                if (!succeeded) {
                                    throw CommandResultException(this)
                                }
                            }
                    }
            }
    }

    fun <C : Any> sendAndWaitForSent(
        command: CommandMessage<C>
    ): Mono<CommandResult> {
        return send(command)
            .thenReturn(
                CommandResult(
                    stage = CommandStage.SENT,
                    aggregateId = command.aggregateId.id,
                    contextName = command.contextName,
                    processorName = COMMAND_GATEWAY_PROCESSOR_NAME,
                    tenantId = command.aggregateId.tenantId,
                    requestId = command.requestId,
                    commandId = command.commandId,
                ),
            ).onErrorResume {
                it.asResult(command, processorName = COMMAND_GATEWAY_PROCESSOR_NAME)
                    .toMono()
            }
    }

    fun <C : Any> sendAndWaitForProcessed(
        command: CommandMessage<C>
    ): Mono<CommandResult> =
        sendAndWait(command, WaitingFor.processed(command.contextName))
}
