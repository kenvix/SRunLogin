@file:JvmName("Main")
package com.kenvix.ipnotifier

import com.kenvix.ipnotifier.contacts.AppConfig
import com.kenvix.utils.log.Logging
import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

private val logger = Logging.getLogger("Main")
lateinit var config: AppConfig
    private set

suspend fun main(args: Array<String>) {
    logger.info("IP Notifier Started // by Kenvix <i@kenvix.com>")

    val configPath = Path.of(args.firstOrNull() ?: "./config.json").toAbsolutePath()
    val prettyJson = Json {
        prettyPrint = true
        encodeDefaults = true
    }
    logger.info("Loading configuration from $configPath")

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
}
