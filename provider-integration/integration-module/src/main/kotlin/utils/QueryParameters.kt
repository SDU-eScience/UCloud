package dk.sdu.cloud.utils

import dk.sdu.cloud.calls.client.urlEncode

fun encodeQueryParamsToString(queryPathMap: Map<String, List<String>>): String {
    return queryPathMap
        .flatMap { param ->
            param.value.map { v -> urlEncode(param.key) + "=" + urlEncode(v) }
        }
        .joinToString("&")
        .takeIf { it.isNotEmpty() }
        ?.let { "?$it" } ?: ""
}
