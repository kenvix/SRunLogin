package com.kenvix.nwafunet.srun

import java.net.URI
import java.net.http.HttpRequest


fun createRequestBuilderWithCommonHeaders(url: URI, portalAddress: String): HttpRequest.Builder {
    return HttpRequest.newBuilder()
        .uri(url)
        .header("Accept", "text/javascript, application/javascript, application/ecmascript, application/x-ecmascript, */*; q=0.01")
        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8,en-GB;q=0.7,en-US;q=0.6,fr-BE;q=0.5,fr;q=0.4")
        .header("DNT", "1")
        .header("Referer", "$portalAddress/srun_portal_success?ac_id=1&theme=pro")
        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36 Edg/130.0.0.0")
        .header("X-Requested-With", "XMLHttpRequest")
}

fun createRequestBuilderWithCommonHeaders(url: String, portalAddress: String): HttpRequest.Builder {
    return createRequestBuilderWithCommonHeaders(URI.create(url), portalAddress)
}