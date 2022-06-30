package dk.sdu.cloud.ipc

import jdk.net.ExtendedSocketOptions
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.ServerSocketChannel

fun main() {
    val channel = ServerSocketChannel
        .open(StandardProtocolFamily.UNIX)
        .bind(UnixDomainSocketAddress.of("./ucloud.sock"))
    val client = channel.accept()
    val principal = client.getOption(ExtendedSocketOptions.SO_PEERCRED)
    println(principal)
    val remoteAddress = client.remoteAddress
    println("Got something")
    println(remoteAddress)
    println("---")
}