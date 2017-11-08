package org.esciencecloud.transactions

import org.slf4j.LoggerFactory
import java.io.File

fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger("GatewayServer")
    log.info("Starting Gateway service")

    val filePath = File(if (args.size == 1) args[0] else "/etc/gateway/conf.json")
    log.info("Reading configuration file from: ${filePath.absolutePath}")
    if (!filePath.exists()) {
        log.error("Could not find log file!")
        System.exit(1)
        return
    }

    val configuration = Configuration.parseFile(filePath)
    Server(configuration).start()
}

