package com.kenvix.nwafunet.srun


import com.kenvix.utils.log.Logging
import kotlinx.coroutines.future.await
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.regex.Pattern
import javax.script.Invocable
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager


class SrunJsEngine(
    val pathSrunLoginScript: String = "SrunLoginScript.js"
) {
    private val engineManager = ScriptEngineManager()
    private val engine: ScriptEngine =
        engineManager.getEngineByName("javascript") ?: throw IllegalStateException("Failed to get JavaScript engine")
    private val invocable: Invocable = engine as Invocable
    private val globalBindings = engine.createBindings()
    private val logger = Logging.getLogger("SrunJsEngine")

    init {
        val srunSupport = ClassLoader.getSystemClassLoader().getResource("SrunSupport.js")
            ?.openStream()?.reader() ?: throw IllegalStateException("Failed to load SrunSupport.js")
        engine.eval(srunSupport, globalBindings)

        val srunLoginScript = ClassLoader.getSystemClassLoader().getResource("SrunLoginScript.js")
            ?.openStream()?.reader() ?: throw IllegalStateException("Failed to load SrunLoginScript.js")
        engine.eval(srunLoginScript, globalBindings)
    }

    fun encodeLogin(info: String, token: String): String {
        globalBindings["info"] = info
        globalBindings["srunToken"] = token
        return engine.eval("srunEncodeLogin(info, srunToken)", globalBindings) as String
    }

    @Suppress("UNCHECKED_CAST")
    fun srunEncodeAuth(username: String, password: String, ip: String, token: String): Map<String, String> {
        globalBindings["username"] = username
        globalBindings["password"] = password
        globalBindings["ip"] = ip
        globalBindings["srunToken"] = token
        return engine.eval("srunEncodeAuth(username, password, ip, srunToken)", globalBindings) as Map<String, String>
    }

    fun encodeLogin(username: String, password: String, ip: String, token: String): String {
        globalBindings["username"] = username
        globalBindings["password"] = password
        globalBindings["ip"] = ip
        globalBindings["srunToken"] = token

        engine.eval("var info = {'username':username,'password':password,'ip':ip,'acid':'1','enc_ver':'srun_bx1'}", globalBindings)
        engine.eval("var token = srunToken", globalBindings)

        return engine.eval("srunEncodeLogin(info, token)", globalBindings) as String
    }

    suspend fun getToken(initUrl: String, username: String, password: String, ip: String, client: HttpClient = HttpClient.newHttpClient()): String {
        val timestamp = Instant.now().toEpochMilli()

        val uri = URI.create(
            "$initUrl/cgi-bin/get_challenge?callback=jQuery112404953340710317169_$timestamp" +
                    "&username=$username" +
                    "&ip=$ip" +
                    "&_=$timestamp"
        )

        val request = createRequestBuilderWithCommonHeaders(uri, initUrl).build()
        val response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
        val matcher = Pattern.compile("\"challenge\":\"(.*?)\"").matcher(response.body())
        if (matcher.find()) {
            return matcher.group(1)
        } else {
            throw IllegalStateException("Failed to get token")
        }
    }


    suspend fun login(initUrl: String, username: String, password: String, ip: String, client: HttpClient = HttpClient.newHttpClient()): String {
        val token = getToken(initUrl, username, password, ip, client)
        val info = srunEncodeAuth(username, password, ip, token)

        val srunPortalApi = "$initUrl/cgi-bin/srun_portal"

        val timestamp = System.currentTimeMillis()
        val params = LinkedHashMap<String, Any>()

        params["callback"] = "jQuery11240645308969735664_$timestamp"
        params.putAll(info)
        params["_"] = timestamp.toString()

        val uri = URI.create(
            "$srunPortalApi?" + params.map { "${it.key}=${URLEncoder.encode(it.value.toString(), Charsets.UTF_8)}" }.joinToString("&")
        )

        logger.finest("Login URL: $uri")

        val request = createRequestBuilderWithCommonHeaders(uri, initUrl).GET().build()
        val response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
        return response.body()
    }
}