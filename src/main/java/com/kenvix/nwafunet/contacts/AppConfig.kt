package com.kenvix.nwafunet.contacts

import kotlinx.serialization.*

@Serializable
enum class StunServerPolicy { ROUND_ROBIN, RANDOM }

@Serializable
data class AppConfig(
    val ipUrls: List<String> = listOf(
        "stun://stun.yy.com",
        "stun://stun.miwifi.com",
        "stun://stun.chat.bilibili.com",
    ),
    val clientName: String = "client114514",
    val mqttBrokerUrl: String = "ws://example.kenvix.com/path" ,
    val mqttUsername: String = "username" ,
    val mqttPassword: String = "password" ,
    val notifierTopic: String = "ip/exampleNotifier",
    val receiverTopics: Map<String, String> = mapOf(
        "ip/exampleReceiver1" to "scripts/exampleReceiver1Handler",
        "ip/exampleReceiver2" to "scripts/exampleReceiver2Handler",
    ),
    val stunServerPolicy: StunServerPolicy = StunServerPolicy.ROUND_ROBIN,
    val peerToBrokenPingInterval: Int = 25_000,
    val peerToBrokenTimeout: Int = 10_000,
    val version: Int = 1,
) {
    companion object {
        const val CURRENT_VERSION = 1
        lateinit var INSTANCE: AppConfig
    }
}
