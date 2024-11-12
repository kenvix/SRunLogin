import static org.junit.jupiter.api.Assertions.assertEquals;

import com.kenvix.nwafunet.srun.LoginInfoEncoder;
import com.kenvix.nwafunet.srun.SrunJsEngine;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class LoginInfoEncoderTest {

    public static final String USERNAME = "114514";
    public static final String PASSWORD = "1919810";
    public static final String IP = "10.131.39.59";
    public static final String TOKEN = "5843669f7280440c0b79116b414310d948830df91117161bb7aea3ac0a0bbb8c";

    @Test
    public void testGetLoginInfo() {
        // Input values
        // Expected output
        String expectedOutput = "{SRBX1}mUWLJNHvZ9nRWSbHqcBSL08jeYMkGK6CW2FBVoO1iCXvj3en1ll2iFabOWkv8H1H8gCfsDEsk2ECdOJrO9l72jfduIOHv7UEljqAN+47H1e7InbnIGTX8PAAK5gboHp4ag52nS==";

        // Perform the encoding
        String result = new SrunJsEngine().encodeLogin(USERNAME, PASSWORD, IP, TOKEN);

        // Assert that the output matches the expected value
        assertEquals(expectedOutput, result);
    }

    @Test
    public void testGetAuthInfo() {
        SrunJsEngine engine = new SrunJsEngine();
        Map<String, String> a = engine.srunEncodeAuth(USERNAME, PASSWORD, IP, TOKEN);
        System.out.println(a);
    }
}
