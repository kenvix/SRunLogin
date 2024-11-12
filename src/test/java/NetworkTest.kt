import org.junit.jupiter.api.Test
import java.net.NetworkInterface
import java.util.*


class NetworkTest {
    @Test
    fun test() {

        // 获取所有网络接口
        val interfaces = NetworkInterface.getNetworkInterfaces()
        for (networkInterface in Collections.list(interfaces)) {
            // 打印网络接口的名称和显示名称
            println("Interface Name: " + networkInterface.name)
            println("Display Name: " + networkInterface.displayName)

            // 获取并打印该接口的 IP 地址
            val inetAddresses = networkInterface.inetAddresses
            for (inetAddress in Collections.list(inetAddresses)) {
                println("  IP Address: " + inetAddress.hostAddress)
            }
            println()
        }
    }
}