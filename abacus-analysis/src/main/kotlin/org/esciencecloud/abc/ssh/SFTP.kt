package org.esciencecloud.abc.ssh

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpATTRS
import com.jcraft.jsch.SftpException
import org.esciencecloud.abc.HPCConfig
import java.io.File

// TODO I'm not certain we should always disconnect after running a command. We might have to, but not sure.
fun <R> SSHConnection.sftp(body: ChannelSftp.() -> R): R = openSFTPChannel().run {
    connect()
    try {
        body()
    } finally {
        disconnect()
    }
}

fun SSHConnection.ls(path: String): List<ChannelSftp.LsEntry> {
    val allFiles = ArrayList<ChannelSftp.LsEntry>()
    sftp {
        ls(path) {
            allFiles.add(it); ChannelSftp.LsEntrySelector.CONTINUE
        }
    }
    return allFiles
}

fun SSHConnection.stat(path: String): SftpATTRS? =
    sftp {
        try {
            stat(path)
        } catch (ex: SftpException) {
            null
        }
    }

fun main(args: Array<String>) {
    val mapper = jacksonObjectMapper()
    val conf = mapper.readValue<HPCConfig>(File("hpc_conf.json"))
    val ssh = SSHConnectionPool(conf.ssh)
    ssh.use {
        ls("/home/dthrane/projects").forEach {
            println(it.filename)
            println(it.attrs.isDir)
        }

        val message = stat("/home/dthrane/projects")
        println(message)
    }
}