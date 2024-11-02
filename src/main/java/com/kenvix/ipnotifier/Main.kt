@file:JvmName("Main")
package com.kenvix.ipnotifier

import com.kenvix.ipnotifier.contacts.AppConfig
import com.kenvix.utils.log.Logging
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.system.exitProcess

private val logger = Logging.getLogger("Main")

@OptIn(ExperimentalSerializationApi::class)
suspend fun main(args: Array<String>) {
    logger.info("IP Notifier Started // by Kenvix <i@kenvix.com>")

    AppConfig.INSTANCE = loadConfig(args.firstOrNull() ?: "./config.json")
}

private fun loadConfig(path: String): AppConfig {
    val configPath = Path.of(path).toAbsolutePath()
    val prettyJson = Json {
        prettyPrint = true
        allowComments = true
        allowTrailingComma = true
        encodeDefaults = true
    }

    logger.info("Loading configuration from $configPath")
    val config: AppConfig

    if (!configPath.toFile().exists()) {
        logger.info("Configuration file not found at $configPath creating a new one")
        config = AppConfig()
        configPath.writeText(prettyJson.encodeToString(AppConfig.serializer(), config))
    } else {
        config = prettyJson.decodeFromString(AppConfig.serializer(), configPath.readText())
        if (config.version < AppConfig.CURRENT_VERSION) {
            logger.warning("Configuration file version mismatch, updating to ${AppConfig.CURRENT_VERSION}")
            configPath.writeText(prettyJson.encodeToString(AppConfig.serializer(), config))
        }
    }

    val defaultValues = AppConfig()
    if (config.mqttBrokerUrl == defaultValues.mqttBrokerUrl) {
        logger.severe("Configuration error: mqttBrokerUrl is not set. Please set it in $configPath")
        exitProcess(2)
    }

    return config
}
