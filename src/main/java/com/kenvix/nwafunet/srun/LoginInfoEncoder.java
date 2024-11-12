package com.kenvix.nwafunet.srun;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class LoginInfoEncoder {

    // 自定义的 Base64 字符集
    private static final String CUSTOM_BASE64_ALPHABET = "LVoJPiCN2R8G90yg+hmFHuacZ1OWMnrsSTXkYpUq/3dlbfKwv6xztjI7DeBE45QA";

    public static String getLoginInfo(String info, String token) {
        // 将用户信息转换为 JSON 格式
        String jsonInfo = info;

        // 加密用户信息
        String encryptedInfo = encode(jsonInfo, token);

        // 自定义 Base64 编码并添加前缀
        return "{SRBX1}" + customBase64Encode(encryptedInfo.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static String encode(String str, String key) {
        if (str.isEmpty()) return "";

        int[] v = s(str, true);
        int[] k = s(key, false);
        if (k.length < 4) k = padKey(k); // 保证长度为4

        int n = v.length - 1;
        int z = v[n], y = v[0];
        int c = 0x86014019 | 0x183639A0;
        int q = (int) Math.floor(6 + 52.0 / (n + 1));
        int d = 0;

        while (q-- > 0) {
            d = (d + c) & 0xFFFFFFFF;
            int e = d >>> 2 & 3;

            for (int p = 0; p < n; p++) {
                y = v[p + 1];
                int m = ((z >>> 5 ^ y << 2) + (y >>> 3 ^ z << 4) ^ (d ^ y)) + (k[p & 3 ^ e] ^ z);
                z = v[p] = (v[p] + m) & 0xFFFFFFFF;
            }

            y = v[0];
            int m = ((z >>> 5 ^ y << 2) + (y >>> 3 ^ z << 4) ^ (d ^ y)) + (k[n & 3 ^ e] ^ z);
            z = v[n] = (v[n] + m) & 0xFFFFFFFF;
        }

        return l(v, false);
    }

    private static int[] padKey(int[] k) {
        int[] paddedKey = new int[4];
        System.arraycopy(k, 0, paddedKey, 0, k.length);
        return paddedKey;
    }

    private static int[] s(String a, boolean b) {
        int len = a.length();
        List<Integer> v = new ArrayList<>();

        for (int i = 0; i < len; i += 4) {
            int part = a.charAt(i)
                    | (i + 1 < len ? a.charAt(i + 1) << 8 : 0)
                    | (i + 2 < len ? a.charAt(i + 2) << 16 : 0)
                    | (i + 3 < len ? a.charAt(i + 3) << 24 : 0);
            v.add(part);
        }

        if (b) v.add(len);
        return v.stream().mapToInt(Integer::intValue).toArray();
    }

    private static String l(int[] a, boolean b) {
        int len = a.length;
        int c = (len - 1) << 2;

        if (b) {
            int m = a[len - 1];
            if (m < c - 3 || m > c) return null;
            c = m;
        }

        StringBuilder result = new StringBuilder();
        for (int value : a) {
            result.append((char) (value & 0xff))
                    .append((char) (value >>> 8 & 0xff))
                    .append((char) (value >>> 16 & 0xff))
                    .append((char) (value >>> 24 & 0xff));
        }

        return b ? result.substring(0, c) : result.toString();
    }

    // 自定义 Base64 编码函数，使用自定义的字符集
    private static String customBase64Encode(byte[] data) {
        StringBuilder encoded = new StringBuilder();
        int padding = 3 - (data.length % 3 == 0 ? 3 : data.length % 3);
        int fullGroups = data.length / 3;

        for (int i = 0; i < fullGroups; i++) {
            int byteChunk = ((data[i * 3] & 0xff) << 16) | ((data[i * 3 + 1] & 0xff) << 8) | (data[i * 3 + 2] & 0xff);
            encoded.append(CUSTOM_BASE64_ALPHABET.charAt((byteChunk >> 18) & 0x3F))
                    .append(CUSTOM_BASE64_ALPHABET.charAt((byteChunk >> 12) & 0x3F))
                    .append(CUSTOM_BASE64_ALPHABET.charAt((byteChunk >> 6) & 0x3F))
                    .append(CUSTOM_BASE64_ALPHABET.charAt(byteChunk & 0x3F));
        }

        if (padding > 0) {
            int byteChunk = (data[fullGroups * 3] & 0xff) << 16 | (padding == 1 ? (data[fullGroups * 3 + 1] & 0xff) << 8 : 0);
            encoded.append(CUSTOM_BASE64_ALPHABET.charAt((byteChunk >> 18) & 0x3F))
                    .append(CUSTOM_BASE64_ALPHABET.charAt((byteChunk >> 12) & 0x3F));
            encoded.append(padding == 1 ? CUSTOM_BASE64_ALPHABET.charAt((byteChunk >> 6) & 0x3F) : "=")
                    .append("=");
        }

        return encoded.toString();
    }
}
