@file:JvmName("Main")
package com.kenvix.nwafunet

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.boolean
import com.github.ajalt.clikt.parameters.types.int
import com.kenvix.nwafunet.srun.SrunJsEngine
import com.kenvix.utils.log.Logging
import com.kenvix.utils.log.debug
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.IOException
import java.net.*
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.logging.Level

/**
 * -Djava.util.logging.ConsoleHandler.level=INFO
 */
object Entry : CliktCommand() {
    val portalAddress: String by option().help("Login Portal URL. For example http://172.26.8.11").default("http://172.26.8.11")
    private val ip: String? by option().help("Outbound IP").help("Your outbound IP address. Leave blank for auto detect.")

    val accountId: String by option().prompt("Account ID").help("Account ID")
    val accountPassword: String by option().prompt("Password").help("Password")
    val networkInterface: String? by option().help("Network Interface Name. All traffic will be sent through this interface if specified.").convert { it.trim() }

    val logout: Boolean by option().boolean().default(false)
    val checkAlive: Int by option().int().help("Check whether network is still alive every N seconds. 0 for disabled.").default(0)
    val keepAlive: Int by option().int().help("Send heart packet to keep alive every N seconds. 0 for disabled.").default(0)
    val retry: Int by option().int().help("Retry every N seconds if failed. 0 for disabled.").default(10)
    val retryWaitTime: Int by option().int().help("Retry wait time in N seconds").default(2)
    val logLevel: Level by option().convert { Level.parse(it) }.help("Log Level. FINEST < FINER < FINE < CONFIG < INFO < WARNING < SEVERE").default(Level.INFO)

    val httpClient by lazy {
        val portalHost = URI(portalAddress).host
        val portalAddress = InetAddress.getAllByName(portalHost)
        val canIpv4 = portalAddress.any { it is InetAddress && it is Inet4Address }
        val canIpv6 = portalAddress.any { it is InetAddress && it is Inet6Address }

        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .let {
                if (networkInterface == null) {
                    it
                } else {
                    if (canIpv4 && interfaceIpv4 != null) it.localAddress(interfaceIpv4)
                    else if (canIpv6 && interfaceIpv6 != null) it.localAddress(interfaceIpv6)
                    else throw IllegalArgumentException("Network interface $networkInterface not found")
                }
            }.build()
    }

    val outboundIp get() = ip ?: getOutboundIpAddress(httpClient)
    val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
    val interfaceMap: MutableMap<String, List<InetAddress>> = mutableMapOf()
    val interfaceIpv4 get() = interfaceMap[networkInterface]?.filterIsInstance<Inet4Address>()?.firstOrNull()
    val interfaceIpv6 get() = interfaceMap[networkInterface]?.filterIsInstance<Inet6Address>()?.firstOrNull()

    private val logger by lazy { Logging.getLogger("Entry", logLevel) }
    private val mutex = Mutex()

    override fun run() { }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun entry() {
        System.setProperty("java.util.logging.ConsoleHandler.level", logLevel.name)

        logger.info("SRun Login Tool Started // by Kenvix <i@kenvix.com>")

        val interfaces = withContext(Dispatchers.IO) { NetworkInterface.getNetworkInterfaces() }
            .asSequence()
            .filter { it.isUp && !it.isLoopback && it.inetAddresses.hasMoreElements() }
            .toList()
        interfaces.forEach {
            interfaceMap[it.displayName.trim()] = it.inetAddresses.asSequence().filter { ip ->
                !ip.isLoopbackAddress &&  !ip.isMulticastAddress && !ip.isAnyLocalAddress && !ip.isLinkLocalAddress
            }.toList()
        }

        logger.info("All enabled network interfaces with IP addresses on this machine: \n${interfaces.joinToString(separator = "\n") {
            val ipStr = interfaceMap[it.displayName]!!.joinToString(separator = "") { inetAddress -> "\tIP: ${inetAddress.hostAddress}\n" }
            "${it.displayName}\n${ipStr}"
        }}")

        if (networkInterface != null) {
            logger.info("Selected network interface: $networkInterface")
            if (interfaceMap[networkInterface] == null) {
                logger.severe("Network interface $networkInterface not found. Check your network interface name.")
                return
            } else {
                logger.fine("Selected network interface IPv4: $interfaceIpv4 \tIPv6: $interfaceIpv6")
            }
        }

        logger.info("studentId: $accountId    studentPassword: $accountPassword")
        logger.info("Outbound IP: $outboundIp")

        while (true) {
            try {
                performNetworkAuth()
            } catch (e: Exception) {
                logger.severe("Failed to perform network auth: ${e.message}")
            }

            if (retry > 0) {
                delay(retry * 1000L)
            } else {
                break
            }
        }

        coroutineScope {
            if (checkAlive > 0) {
                launch {
                    while (isActive) {
                        try {
                            logger.finer("Performing check alive request")
                            if (!isIntraNetworkReady()) {
                                logger.warning("Network is not ready, performing re-auth")
                                performNetworkAuth()
                            }
                        } catch (e: Exception) {
                            logger.severe("Failed to perform check alive: ${e.message}")
                        }

                        delay(checkAlive * 1000L)
                    }
                }
            }

            if (keepAlive > 0) {
                launch {
                    while (isActive) {
                        try {
                            performKeepAlive()
                        } catch (e: Exception) {
                            logger.severe("Failed to perform keep alive: ${e.message}")
                        }
                        delay(keepAlive * 1000L)
                    }
                }
            }
        }
    }

    suspend fun performKeepAlive() {
        val req = createRequestBuilderWithCommonHeaders(portalAddress)
            .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
            .build()

        httpClient.sendAsync(req, HttpResponse.BodyHandlers.discarding()).await()
    }

    suspend fun performNetworkAuth() {
        while (!isIntraNetworkReady()) {
            // performLogout()
            delay(500L)

            performLogin()

            if (logout) {
                performLogout()
            }

            delay(retryWaitTime * 1000L)
        }
    }

    fun getOutboundIpAddress(client: HttpClient = HttpClient.newBuilder().build()): String {
        try {
            return runBlocking { getSrunOutboundIpAddress(client) }
        } catch (e: Exception) {
            logger.severe("Failed to get outbound IP address from srun: ${e.message}")
            return getInterfaceOutboundIpAddress(portalAddress)
        }
    }

    suspend fun getSrunOutboundIpAddress(client: HttpClient = HttpClient.newBuilder().build()): String {
        val timestamp = System.currentTimeMillis()
        val callback = "jQuery112404013496966464385_$timestamp"
        val url = "$portalAddress/cgi-bin/rad_user_info?callback=$callback&_=$timestamp"

        val request = createRequestBuilderWithCommonHeaders(url)
            .GET()
            .build()

        return try {
            val response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
            val responseBody = response.await().body()

            // 去除回调函数名包裹，只留下 JSON 部分
            val jsonText = responseBody.substringAfter("$callback(").substringBeforeLast(")")

            // 解析 JSON 数据
            val json = JSONObject(jsonText)

            // 尝试获取 client_ip 或 online_ip
            json.optString("client_ip", json.optString("online_ip", null))
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to get IP address: ${e.message}", e)
        }
    }

    fun getInterfaceOutboundIpAddress(remoteHost: String, remotePort: Int = 80): String {
        return try {
            Socket().use { socket ->
                // 连接到目标地址的端口，获取实际出站IP
                socket.connect(InetSocketAddress(URI(remoteHost).host, remotePort), 1000)
                (socket.localAddress.hostAddress)
            }
        } catch (e: Exception) {
            throw IOException("Failed to determine outbound IP address: ${e.message}", e)
        }
    }

    private fun createRequestBuilderWithCommonHeaders(url: String): HttpRequest.Builder {
         return com.kenvix.nwafunet.srun.createRequestBuilderWithCommonHeaders(url, portalAddress)
    }

    suspend fun performLogin(): Boolean {
        mutex.withLock {
            logger.info("Performing login request")
            val result = SrunJsEngine().login(portalAddress, accountId, accountPassword, outboundIp, httpClient)
            if (result.contains("login_ok")) {
                logger.info("Login success")
                return true
            } else {
                logger.warning("Login failed: $result")
                return false
            }
        }
    }

    suspend fun performLogout() {
        logger.info("Performing logout request")
        val time = System.currentTimeMillis()
        val request = createRequestBuilderWithCommonHeaders("${portalAddress}/cgi-bin/srun_portal?callback=jQuery112405095399744250795_$time&action=logout&username=$accountId&ip=$outboundIp&ac_id=1&_=$time")
            .GET()
            .build()
        val rsp = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
        logger.debug("Logout request response # ${rsp.statusCode()}: ${rsp.body()}")
        val err = checkForError(rsp.body())
        if (err != null) {
            logger.warning("Failed to logout: ${err.code}: ${err.msg}")
        }
    }

    suspend fun isPublicNetworkReady(): Boolean {
        logger.fine("Checking Public network status")
        val request = createRequestBuilderWithCommonHeaders("http://connect.rom.miui.com/generate_204")
            .method("HEAD", HttpRequest.BodyPublishers.noBody())
            .build()

        try {
            // 发送请求并检查响应状态码
            val response: HttpResponse<Void> = httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding()).await()
            if (response.statusCode() == 204) {
                logger.fine("Public Network is reachable")
                return true
            } else {
                logger.warning("Public Network is hijacked: ${response.statusCode()}")
                return false
            }
        } catch (e: IOException) {
            logger.warning("Public Network is not reachable: ${e.message}")
            return false
        }
    }

    suspend fun isIntraNetworkReady(): Boolean {
        logger.fine("Checking Intra network status")

        val timestamp = System.currentTimeMillis()
        val request = createRequestBuilderWithCommonHeaders("$portalAddress/cgi-bin/rad_user_info?callback=jQuery112406390292035501186_$timestamp&_=$timestamp")
            .build()

        try {
            // 发送请求并检查响应状态码
            val response: HttpResponse<String> = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
            if ("online_device_total" in response.body() || "not_online_error" !in response.body()) {
                logger.fine("Intra Network is online")
                return true
            } else {
                logger.info("Intra Network is OFFLINE!")
                return false
            }
        } catch (e: IOException) {
            logger.warning("Intra Network is not reachable: ${e.message}")
            return false
        }
    }

    data class SrunError(val code: Int, val msg: String)

    fun checkForError(responseBody: String): SrunError? {
        val jsonText = responseBody.substringAfter("(").substringBeforeLast(")")

        // 解析 JSON 数据
        val jsonObject = JSONObject(jsonText)

        // 检查 error 字段，判断是否有错误
        val error = jsonObject.optString("error", "unknown_error")
        if (error.equals("ok", ignoreCase = true)) {
            return null // 没有错误，返回 null
        } else {
            val errorMsg = jsonObject.optString("error_msg", "No error message provided")
            val errorCode = jsonObject.optInt("ecode", -1)
            return SrunError(errorCode, "$error - $errorMsg")
        }
    }
}

suspend fun main(args: Array<String>) {
    Entry.main(args)
    Entry.entry()
}