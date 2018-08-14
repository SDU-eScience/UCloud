package dk.sdu.cloud.service

import java.net.InetAddress
import java.net.UnknownHostException

fun testHostname(hostname: String): Boolean {
    return try {
        InetAddress.getByName(hostname)
        true
    } catch (ex: UnknownHostException) {
        false
    }
}

fun findValidHostname(hostnames: List<String>): String? {
    return hostnames.find { testHostname(it) }
}
