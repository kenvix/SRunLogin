package com.kenvix.nwafunet;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SrunLoginImpl {
    private static final String INIT_URL = "http://172.26.8.11";
    private static final String GET_CHALLENGE_API = "http://172.26.8.11/cgi-bin/get_challenge";
    private static final String SRUN_PORTAL_API = "http://172.26.8.11/cgi-bin/srun_portal";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/63.0.3239.26 Safari/537.36";
    
    private static String username = "114514";
    private static String password = "1919810";
    private static String ip;
    private static String token;
    private static String hmd5;
    private static String chksum;
    private static String i;
    
    private static String sendGetRequest(String url, Map<String, String> params) throws Exception {
        StringBuilder urlWithParams = new StringBuilder(url).append("?");
        for (Map.Entry<String, String> param : params.entrySet()) {
            urlWithParams.append(param.getKey()).append("=").append(param.getValue()).append("&");
        }
        URL obj = new URL(urlWithParams.toString());
        HttpURLConnection connection = (HttpURLConnection) obj.openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        
        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();
        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();
        return response.toString();
    }
    
    private static String getMd5(String data, String token) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        md.update(token.getBytes(StandardCharsets.UTF_8));
        byte[] digest = md.digest(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    private static String getSha1(String data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] digest = md.digest(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
    
    private static void initGetIp() throws Exception {
        String response = sendGetRequest(INIT_URL, new HashMap<>());
        Pattern pattern = Pattern.compile("id=\"user_ip\" value=\"(.*?)\"");
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            ip = matcher.group(1);
            System.out.println("IP initialized: " + ip);
        } else {
            System.out.println("IP not found.");
        }
    }
    
    private static void getToken() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("callback", "jQuery112404953340710317169_" + System.currentTimeMillis());
        params.put("username", username);
        params.put("ip", ip);
        params.put("_", String.valueOf(System.currentTimeMillis()));
        
        String response = sendGetRequest(GET_CHALLENGE_API, params);
        Pattern pattern = Pattern.compile("\"challenge\":\"(.*?)\"");
        Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            token = matcher.group(1);
            System.out.println("Token: " + token);
        }
    }
    
    private static void doComplexWork() throws Exception {
        i = getInfo();
        i = "{SRBX1}" + Base64.getEncoder().encodeToString(xEncode(i, token));
        hmd5 = getMd5(password, token);
        chksum = getSha1(getChksum());
        System.out.println("Encryption work completed");
    }
    
    private static String getChksum() {
        return token + username + token + hmd5 + token + "1" + token + ip + token + "200" + token + "1" + token + i;
    }
    
    private static String getInfo() {
        Map<String, String> info = new HashMap<>();
        info.put("username", username);
        info.put("password", password);
        info.put("ip", ip);
        info.put("acid", "1");
        info.put("enc_ver", "srun_bx1");
        return info.toString().replace("=", ":").replace(", ", ",").replace("{", "").replace("}", "");
    }
    
    private static byte[] xEncode(String msg, String key) {
        if (msg.isEmpty()) return new byte[0];

        List<Integer> pwd = sEncode(msg, true);
        List<Integer> pwdk = sEncode(key, false);
        while (pwdk.size() < 4) pwdk.add(0);

        int n = pwd.size() - 1;
        int z = pwd.get(n), y = pwd.get(0);
        int c = 0x86014019 | 0x183639A0, m, e, p;
        int q = (int) Math.floor(6 + 52 / (n + 1));
        int d = 0;

        while (q-- > 0) {
            d = d + c & (0x8CE0D9BF | 0x731F2640);
            e = d >> 2 & 3;
            for (p = 0; p < n; p++) {
                y = pwd.get(p + 1);
                m = (z >>> 5 ^ y << 2) + ((y >>> 3 ^ z << 4) ^ (d ^ y)) + (pwdk.get(p & 3 ^ e) ^ z);
                pwd.set(p, pwd.get(p) + m & (0xEFB8D130 | 0x10472ECF));
                z = pwd.get(p);
            }
            y = pwd.get(0);
            m = (z >>> 5 ^ y << 2) + ((y >>> 3 ^ z << 4) ^ (d ^ y)) + (pwdk.get(p & 3 ^ e) ^ z);
            pwd.set(n, pwd.get(n) + m & (0xBB390742 | 0x44C6F8BD));
            z = pwd.get(n);
        }
        return lEncode(pwd, false);
    }

    private static List<Integer> sEncode(String msg, boolean addLength) {
        int length = msg.length();
        List<Integer> pwd = new ArrayList<>();
        for (int i = 0; i < length; i += 4) {
            int val = ordAt(msg, i) | (ordAt(msg, i + 1) << 8) | (ordAt(msg, i + 2) << 16) | (ordAt(msg, i + 3) << 24);
            pwd.add(val);
        }
        if (addLength) pwd.add(length);
        return pwd;
    }

    private static byte[] lEncode(List<Integer> msg, boolean addLength) {
        ByteBuffer buffer = ByteBuffer.allocate(msg.size() * 4);
        for (Integer i : msg) {
            buffer.putInt(i);
        }
        return buffer.array();
    }

    private static int ordAt(String msg, int idx) {
        return (idx < msg.length()) ? msg.charAt(idx) : 0;
    }
    
    private static void login() throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("callback", "jQuery11240645308969735664_" + System.currentTimeMillis());
        params.put("action", "login");
        params.put("username", username);
        params.put("password", "{MD5}" + hmd5);
        params.put("ac_id", "1");
        params.put("ip", ip);
        params.put("chksum", chksum);
        params.put("info", i);
        params.put("n", "200");
        params.put("type", "1");
        params.put("os", "windows+10");
        params.put("name", "windows");
        params.put("double_stack", "0");
        params.put("_", String.valueOf(System.currentTimeMillis()));
        
        String response = sendGetRequest(SRUN_PORTAL_API, params);
        System.out.println(response);
    }
    
    public static void main(String[] args) {
        try {
            initGetIp();
            getToken();
            doComplexWork();
            login();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
