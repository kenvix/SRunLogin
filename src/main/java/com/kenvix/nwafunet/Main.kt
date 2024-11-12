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
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration


object Entry : CliktCommand() {
    val portalAddress: String by option().help("Login Portal URL. For example http://172.26.8.11").default("http://172.26.8.11")
    val outboundIp: String by option().help("Outbound IP").help("Your outbound IP address. Leave blank for auto detect.").defaultLazy { getOutboundIpAddress() }
    val accountId: String by option().prompt("Account ID").help("Account ID")
    val accountPassword: String by option().prompt("Password").help("Password")
    val logout: Boolean by option().boolean().default(false)
    val checkAlive: Int by option().int().help("Check whether network is still alive every N seconds. 0 for disabled.").default(0)
    val keepAlive: Int by option().int().help("Send heart packet to keep alive every N seconds. 0 for disabled.").default(0)
    val retryWaitTime: Int by option().int().help("Retry wait time in N seconds").default(2)

    val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build()
    val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    private val logger = Logging.getLogger("Entry")
    private val mutex = Mutex()

    override fun run() { }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun entry() {
        logger.info("SRun Login Tool Started // by Kenvix <i@kenvix.com>")
        logger.info("studentId: $accountId    studentPassword: $accountPassword")
        logger.info("Outbound IP: $outboundIp")

        performNetworkAuth()

        coroutineScope {
            if (checkAlive > 0) {
                launch {
                    while (isActive) {
                        logger.debug("Performing check alive request")
                        if (!isIntraNetworkReady()) {
                            logger.warning("Network is not ready, performing re-auth")
                            performNetworkAuth()
                        }
                        delay(checkAlive * 1000L)
                    }
                }
            }

            if (keepAlive > 0) {
                launch {
                    while (isActive) {
                        performKeepAlive()
                        delay(keepAlive * 1000L)
                    }
                }
            }
        }
    }

    suspend fun performKeepAlive() {
        val req = HttpRequest.newBuilder()
            .uri(URI.create(portalAddress))
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

    fun getOutboundIpAddress(): String {
        try {
            return runBlocking { getSrunOutboundIpAddress() }
        } catch (e: Exception) {
            logger.severe("Failed to get outbound IP address from srun: ${e.message}")
            return getInterfaceOutboundIpAddress(portalAddress)
        }
    }

    suspend fun getSrunOutboundIpAddress(): String {
        val timestamp = System.currentTimeMillis()
        val callback = "jQuery112404013496966464385_$timestamp"
        val url = "$portalAddress/cgi-bin/rad_user_info?callback=$callback&_=$timestamp"

        val client = HttpClient.newBuilder()
            .build()

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
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

    fun createRequestBuilderWithCommonHeaders(url: String): HttpRequest.Builder {
        return HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "text/javascript, application/javascript, application/ecmascript, application/x-ecmascript, */*; q=0.01")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6,fr-BE;q=0.5,fr;q=0.4")
            .header("DNT", "1")
            .header("Referer", "${portalAddress}/srun_portal_success?ac_id=1&theme=pro")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0")
            .header("X-Requested-With", "XMLHttpRequest")
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
        logger.info("Checking Public network status")
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://connect.rom.miui.com/generate_204"))
            .method("HEAD", HttpRequest.BodyPublishers.noBody())
            .build()

        try {
            // 发送请求并检查响应状态码
            val response: HttpResponse<Void> = httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding()).await()
            if (response.statusCode() == 204) {
                logger.info("Public Network is reachable")
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
        logger.info("Checking Intra network status")

        val timestamp = System.currentTimeMillis()
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$portalAddress/cgi-bin/rad_user_info?callback=jQuery112406390292035501186_$timestamp&_=$timestamp"))
            .build()

        try {
            // 发送请求并检查响应状态码
            val response: HttpResponse<String> = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
            if ("online_device_total" in response.body())
                return true
            return "not_online_error" !in response.body()
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