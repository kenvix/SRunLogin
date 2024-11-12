package com.kenvix.utils.tools

import java.net.URI
import java.net.URL

fun main() {
    @Suppress("DEPRECATION")
    val url = URL("http://172.26.8.11/cgi-bin/srun_portal?callback=jQuery11240645308969735664_1731342593886&action=login&username=114514&password={MD5}b2ea696fdeec9dd802a15210fac18211&ac_id=1&ip=10.131.39.59&chksum=5012ede8a354ad338d84a46ed4e8c9aeff1eab5d&info={SRBX1}e3VzZXJuYW1lOjIwMTIxMTAwMDMscGFzc3dvcmQ6bWwxOTgyNTMsaXA6MTAuMTMxLjM5LjU5LGFjaWQ6MSxlbmNfdmVyOnNydW5fYngxfQ==&n=200&type=1&os=windows+10&name=windows&double_stack=0&_=1731342593886")
    url.openConnection()
}