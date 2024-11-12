function srunEncodeLogin(info, token) {
    // 克隆自 $.base64，防止污染
    var base64 = $.base64; // base64 设置 Alpha
    base64.setAlpha('LVoJPiCN2R8G90yg+hmFHuacZ1OWMnrsSTXkYpUq/3dlbfKwv6xztjI7DeBE45QA'); // 用户信息转 JSON

    info = JSON.stringify(info);

    function encode(str, key) {
        if (str === '') return '';
        var v = s(str, true);
        var k = s(key, false);
        if (k.length < 4) k.length = 4;
        var n = v.length - 1,
            z = v[n],
            y = v[0],
            c = 0x86014019 | 0x183639A0,
            m,
            e,
            p,
            q = Math.floor(6 + 52 / (n + 1)),
            d = 0;

        while (0 < q--) {
            d = d + c & (0x8CE0D9BF | 0x731F2640);
            e = d >>> 2 & 3;

            for (p = 0; p < n; p++) {
                y = v[p + 1];
                m = z >>> 5 ^ y << 2;
                m += y >>> 3 ^ z << 4 ^ (d ^ y);
                m += k[p & 3 ^ e] ^ z;
                z = v[p] = v[p] + m & (0xEFB8D130 | 0x10472ECF);
            }

            y = v[0];
            m = z >>> 5 ^ y << 2;
            m += y >>> 3 ^ z << 4 ^ (d ^ y);
            m += k[p & 3 ^ e] ^ z;
            z = v[n] = v[n] + m & (0xBB390742 | 0x44C6F8BD);
        }

        return l(v, false);
    }

    function s(a, b) {
        var c = a.length;
        var v = [];

        for (var i = 0; i < c; i += 4) {
            v[i >> 2] = a.charCodeAt(i) | a.charCodeAt(i + 1) << 8 | a.charCodeAt(i + 2) << 16 | a.charCodeAt(i + 3) << 24;
        }

        if (b) v[v.length] = c;
        return v;
    }

    function l(a, b) {
        var d = a.length;
        var c = d - 1 << 2;

        if (b) {
            var m = a[d - 1];
            if (m < c - 3 || m > c) return null;
            c = m;
        }

        for (var i = 0; i < d; i++) {
            a[i] = String.fromCharCode(a[i] & 0xff, a[i] >>> 8 & 0xff, a[i] >>> 16 & 0xff, a[i] >>> 24 & 0xff);
        }

        return b ? a.join('').substring(0, c) : a.join('');
    }

    return '{SRBX1}' + base64.encode(encode(info, token));
}

function srunEncodeAuth(username, password, ip, token) {
    const n = 200;
    const ac_id = 1;
    const type = 1;


    // 用户密码 MD5 加密
    var hmd5 = md5(password, token); // 用户信息加密

    // console.debug(ip)

    var i = srunEncodeLogin({'username':username,'password':password,'ip':ip,'acid':'1','enc_ver':'srun_bx1'}, token)

    var str = token + username;
    str += token + hmd5;
    str += token + ac_id;
    str += token + ip;
    str += token + n;
    str += token + type;
    str += token + i; // 防止 IPv6 请求网络不通进行 try catch

    return {
        action: 'login',
        username: username,
        password: '{MD5}' + hmd5,
        os: 'Windows 10',
        name: 'Windows',
        // 未开启双栈认证，参数为 0
        // 开启双栈认证，向 Portal 当前页面 IP 认证时，参数为 1
        // 开启双栈认证，向 Portal 另外一种 IP 认证时，参数为 0
        double_stack: 0,
        chksum: sha1(str),
        info: i,
        ac_id: ac_id,
        ip: ip,
        n: n,
        type: type
    }

} // 使用 Portal 页面 IP 类型认证