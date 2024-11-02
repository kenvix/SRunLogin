package com.kenvix.ipnotifier

import com.kenvix.ipnotifier.contacts.AppConfig
import com.kenvix.utils.log.Logging
import com.kenvix.utils.log.debug
import com.kenvix.utils.log.trace
import com.kenvix.utils.log.warning
import com.kenvix.utils.tools.sha256Of
import com.kenvix.utils.tools.toBase58String
import kotlinx.coroutines.*
import org.eclipse.paho.mqttv5.client.*
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence
import org.eclipse.paho.mqttv5.common.MqttException
import org.eclipse.paho.mqttv5.common.MqttMessage
import org.eclipse.paho.mqttv5.common.packet.MqttProperties
import java.net.Inet6Address
import java.net.InetAddress
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume

class MqttClient : CoroutineScope, AutoCloseable {
    private val logger = Logging.getLogger("MqttClient")

    private val job = Job() + CoroutineName("BrokerClient: $this")
    override val coroutineContext: CoroutineContext = job + Dispatchers.IO
    private val mqttClient: MqttAsyncClient = MqttAsyncClient(AppConfig.INSTANCE.mqttBrokerUrl, AppConfig.INSTANCE.clientName)

    @Volatile
    private var connectCoroutineContinuation: Continuation<Unit>? = null
    suspend fun connect() = withContext(Dispatchers.IO) {
        logger.debug("Preparing to connect to MQTT server: ${AppConfig.INSTANCE.mqttBrokerUrl}")

        mqttClient.setCallback(MqttEventHandler())

        val options = MqttConnectionOptionsBuilder()
            .automaticReconnectDelay(1000, 2000)
            .connectionTimeout(AppConfig.INSTANCE.peerToBrokenTimeout)
            .keepAliveInterval(AppConfig.INSTANCE.peerToBrokenPingInterval)
            .cleanStart(true)
            .username(AppConfig.INSTANCE.mqttUsername)
            .password(sha256Of(AppConfig.INSTANCE.mqttPassword).toBase58String().toByteArray())
            .automaticReconnect(true)
            .build()

        try {
            suspendCancellableCoroutine<Unit> {
                connectCoroutineContinuation = it
                mqttClient.connect(options)
                logger.trace("Connecting to MQTT server: ${AppConfig.INSTANCE.mqttBrokerUrl}")
            }
        } finally {
            connectCoroutineContinuation = null
        }
    }

    private suspend fun onMqttMessageArrived(topic: String, message: MqttMessage) {

    }

    private inner class MqttEventHandler() : MqttCallback {
        override fun disconnected(disconnectResponse: MqttDisconnectResponse?) {
            logger.info("MQTT Disconnected")
        }

        override fun mqttErrorOccurred(exception: MqttException) {
            logger.warning(exception, "MQTT Error Occurred")
        }

        override fun messageArrived(topic: String?, message: MqttMessage?) {
            logger.trace("Message arrived: $topic, Len ${message?.payload?.size}")
            if (topic.isNullOrBlank() || message == null) {
                logger.warning("Invalid message arrived: $topic, ${message?.payload}")
                return
            }

            launch(Dispatchers.IO) {
                onMqttMessageArrived(topic, message)
            }
        }

        override fun deliveryComplete(token: IMqttToken?) {
            logger.trace("Delivery complete: ${token?.topics?.contentToString()} - #${token?.messageId}: ${token?.message}")
        }

        override fun connectComplete(isReconnect: Boolean, serverURI: String?) {
            logger.info("Connect completed: [is_reconnect? $isReconnect]: $serverURI")

            while (isActive) {
                try {
                    val subscriptionResultArray = arrayOf(
                        mqttClient.subscribe(getMqttChannelBasePath(AppEnv.PeerId) + "control/#", 2),
                        mqttClient.subscribe(getMqttChannelBasePath(AppEnv.PeerId) + TOPIC_RESPONSE, 2),
                        mqttClient.subscribe(getMqttChannelBasePath(AppEnv.PeerId) + TOPIC_RELAY, 0),
                        mqttClient.subscribe(getMqttChannelBasePath(AppEnv.PeerId) + TOPIC_PING, 0),
                        mqttClient.subscribe(getMqttChannelBasePath(AppEnv.PeerId) + TOPIC_TEST, 2),
                    )

                    subscriptionResultArray.forEach {
                        it.waitForCompletion()
                        if (NATPeerToPeer.debugNetworkTraffic)
                            logger.trace("MQTT Subscription: ${it.topics.contentToString()} with QOS ${it.grantedQos.contentToString()} result: ${it.isComplete}")
                    }

                    break
                } catch (e: Exception) {
                    logger.error("MQTT Subscription failed, retry ...", e)
                }
            }

            if (isReconnect) {
                launch(Dispatchers.IO) { registerPeer() }
            }

            logger.info("MQTT Connected and subscribed to topics. Root topic: ${getMqttChannelBasePath(AppEnv.PeerId)}")
            ignoreException {
                connectCoroutineContinuation?.resume(Unit)
            }
        }

        override fun authPacketArrived(reasonCode: Int, properties: MqttProperties?) {
            logger.debug("Auth packet arrived: $reasonCode, $properties")
        }
    }

    override fun close() {
        mqttClient.close()
    }
}
