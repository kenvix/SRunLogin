
import org.junit.jupiter.api.Test
import javax.script.ScriptEngineManager


fun main() {
    val engineManager = ScriptEngineManager()
    val engine = engineManager.getEngineByName("javascript")

    val srunSupport = ClassLoader.getSystemClassLoader().getResource("SrunSupport.js")
        ?.openStream()?.reader()  ?: throw IllegalStateException("Failed to load SrunSupport.js")
    engine.eval(srunSupport)

    val srunLoginScript = ClassLoader.getSystemClassLoader().getResource("SrunLoginScript.js")
        ?.openStream()?.reader()  ?: throw IllegalStateException("Failed to load SrunLoginScript.js")
    engine.eval(srunLoginScript)

    engine.eval("let info = {\"username\":\"114514\",\"password\":\"1919810\",\"ip\":\"10.131.39.59\",\"acid\":\"1\",\"enc_ver\":\"srun_bx1\"}")
    engine.eval("let token = '5843669f7280440c0b79116b414310d948830df91117161bb7aea3ac0a0bbb8c'")
    engine.eval("console.log(srunEncodeLogin(info, token))")
    engine.eval("sha1('Hello Js!')")
}