package com.kenvix.ipnotifier.contacts

import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

@Serializable
enum class StunServerPolicy { ROUND_ROBIN, RANDOM }

@Serializable
data class AppConfig(
    val ipUrls: List<String> = listOf(
        "stun://stun.yy.com",
        "stun://stun.miwifi.com",
        "stun://stun.chat.bilibili.com",
    ),
    val mqttBrokerUrl: String = "https://example.kenvix.com/path" ,
    val notifierTopic: String = "ip/exampleNotifier",
    val receiverTopics: Map<String, String> = mapOf(
        "ip/exampleReceiver1" to "scripts/exampleReceiver1Handler",
        "ip/exampleReceiver2" to "scripts/exampleReceiver2Handler",
    ),
    val stunServerPolicy: StunServerPolicy = StunServerPolicy.ROUND_ROBIN,
    val version: Int = 1,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
