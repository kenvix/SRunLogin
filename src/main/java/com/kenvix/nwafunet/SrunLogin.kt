package com.kenvix.nwafunet

import com.kenvix.utils.log.Logging
import kotlinx.coroutines.future.await
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.regex.Pattern

class SrunLogin(
    private val initUrl: String,
    private val username: String,
    private val password: String,
    private var ip: String,
    private val client: HttpClient = HttpClient.newHttpClient(),
) {
    private val getChallengeApi = "$initUrl/cgi-bin/get_challenge"
    private val srunPortalApi = "$initUrl/cgi-bin/srun_portal"
    private val n = "200"
    private val type = "1"
    private val acId = "1"
    private val enc = "srun_bx1"

    private lateinit var token: String
    private lateinit var hmd5: String
    private lateinit var chksum: String
    private lateinit var info: String
    
    private val logger = Logging.getLogger("SrunLogin")

    private fun getMd5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val hash = md.digest(input.toByteArray(StandardCharsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun getSha1(input: String): String {
        val sha = MessageDigest.getInstance("SHA-1")
        val hash = sha.digest(input.toByteArray(StandardCharsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    private fun getBase64(input: String): String {
        return Base64.getEncoder().encodeToString(input.toByteArray(StandardCharsets.UTF_8))
    }

    private fun getChksum(): String {
        return token + username + token + hmd5 + token + acId + token + ip + token + n + token + type + token + info
    }

    private fun getInfo(): String {
        val infoMap = mapOf(
            "username" to username,
            "password" to password,
            "ip" to ip,
            "acid" to acId,
            "enc_ver" to enc
        )
        return "{SRBX1}" + getBase64(infoMap.toString().replace(" ", "").replace("=", ":"))
    }

    private suspend fun getToken() {
        val timestamp = Instant.now().toEpochMilli()
        val uri = URI.create(
            "$getChallengeApi?callback=jQuery112404953340710317169_$timestamp" +
                    "&username=$username" +
                    "&ip=$ip" +
                    "&_=$timestamp"
        )

        val request = HttpRequest.newBuilder().uri(uri).build()
        val response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
        val matcher = Pattern.compile("\"challenge\":\"(.*?)\"").matcher(response.body())
        if (matcher.find()) {
            token = matcher.group(1)
            logger.info("Token received: $token")
        } else {
            throw IllegalStateException("Failed to get token")
        }
    }

    private fun doComplexWork() {
        info = getInfo()
        hmd5 = getMd5(password + token)
        chksum = getSha1(getChksum())
        logger.info("Complex work completed with encrypted parameters")
    }

    suspend fun login(): String {
        getToken()
        doComplexWork()

        val timestamp = System.currentTimeMillis()
        val uri = URI.create(
            "$srunPortalApi?callback=jQuery11240645308969735664_$timestamp" +
                    "&action=login" +
                    "&username=$username" +
                    "&password={MD5}$hmd5" +
                    "&ac_id=$acId" +
                    "&ip=$ip" +
                    "&chksum=$chksum" +
                    "&info=$info" +
                    "&n=$n" +
                    "&type=$type" +
                    "&os=windows+10" +
                    "&name=windows" +
                    "&double_stack=0" +
                    "&_=$timestamp"
        )

        val request = HttpRequest.newBuilder().uri(uri).build()
        val response = client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).await()
        logger.info("Login response: ${response.body()}")
        return response.body()
    }
}