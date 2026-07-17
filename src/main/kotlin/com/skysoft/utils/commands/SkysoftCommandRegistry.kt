package com.skysoft.utils.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.tree.ArgumentCommandNode
import com.mojang.brigadier.tree.LiteralCommandNode
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource

class SkysoftCommandRegistry(
    private val dispatcher: CommandDispatcher<FabricClientCommandSource>,
    private val rootName: String = "skysoft",
    private val rootAliases: List<String> = listOf("ss", "soft"),
) {
    private var rootCommand: Command<FabricClientCommandSource>? = null
    private val childBuilders = mutableListOf<() -> ArgumentBuilder<FabricClientCommandSource, *>>()
    private val aliases = mutableListOf<LiteralArgumentBuilder<FabricClientCommandSource>>()
    private var fallbackBuilder: ((Collection<String>) -> ArgumentBuilder<FabricClientCommandSource, *>)? = null

    fun root(command: Command<FabricClientCommandSource>) {
        rootCommand = command
    }

    fun child(builder: () -> ArgumentBuilder<FabricClientCommandSource, *>) {
        childBuilders += builder
    }

    fun child(
        name: String,
        alias: String? = null,
        builder: (String) -> LiteralArgumentBuilder<FabricClientCommandSource>,
    ) {
        childBuilders += { builder(name) }
        alias?.let { aliases += builder(it) }
    }

    fun fallback(name: String, command: Command<FabricClientCommandSource>) {
        fallbackBuilder = { reservedInputs ->
            RequiredArgumentBuilder.argument<FabricClientCommandSource, String>(
                name,
                FallbackStringArgumentType(reservedInputs),
            ).executes(command)
        }
    }

    fun register() {
        (listOf(rootName) + rootAliases).forEach { name ->
            val children = childBuilders.map { it().build() }
            val reservedInputs = children.filterIsInstance<LiteralCommandNode<*>>().map { it.literal }
            reserveExistingFallbackInputs(name, reservedInputs)
            val root = literal(name)
            rootCommand?.let(root::executes)
            children.forEach(root::then)
            fallbackBuilder?.invoke(reservedInputs)?.let(root::then)
            dispatcher.register(root)
        }
        aliases.forEach(dispatcher::register)
    }

    private fun reserveExistingFallbackInputs(rootName: String, inputs: Collection<String>) {
        dispatcher.root.getChild(rootName)?.children.orEmpty().forEach { child ->
            val type = (child as? ArgumentCommandNode<*, *>)?.type
            (type as? FallbackStringArgumentType)?.reserve(inputs)
        }
    }

    companion object {
        fun literal(name: String): LiteralArgumentBuilder<FabricClientCommandSource> =
            LiteralArgumentBuilder.literal(name)

        fun stringArgument(name: String): RequiredArgumentBuilder<FabricClientCommandSource, String> =
            RequiredArgumentBuilder.argument(name, StringArgumentType.greedyString())

    }
}

private class FallbackStringArgumentType(reservedInputs: Collection<String>) : ArgumentType<String> {
    private val reservedInputs = reservedInputs.toMutableSet()
    private val delegate = StringArgumentType.greedyString()

    override fun parse(reader: StringReader): String {
        if (reader.remaining in reservedInputs) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(reader)
        }
        return delegate.parse(reader)
    }

    override fun getExamples(): Collection<String> = delegate.examples

    fun reserve(inputs: Collection<String>) {
        reservedInputs += inputs
    }
}
