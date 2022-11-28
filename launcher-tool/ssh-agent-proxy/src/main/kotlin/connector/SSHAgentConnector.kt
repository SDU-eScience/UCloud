/*
Copyright (c) 2011 ymnk, JCraft,Inc. All rights reserved.
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
  1. Redistributions of source code must retain the above copyright notice,
     this list of conditions and the following disclaimer.
  2. Redistributions in binary form must reproduce the above copyright
     notice, this list of conditions and the following disclaimer in
     the documentation and/or other materials provided with the distribution.
  3. The names of the authors may not be used to endorse or promote products
     derived from this software without specific prior written permission.
THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL JCRAFT,
INC. OR ANY CONTRIBUTORS TO THIS SOFTWARE BE LIABLE FOR ANY DIRECT, INDIRECT,
INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.jcraft.jsch.agentproxy.connector

import com.jcraft.jsch.agentproxy.AgentProxyException
import com.jcraft.jsch.agentproxy.Buffer
import com.jcraft.jsch.agentproxy.Connector
import com.jcraft.jsch.agentproxy.USocketFactory
import java.io.IOException

class SSHAgentConnector @JvmOverloads constructor(
    private val factory: USocketFactory,
    private val usocketPath: String? = null
) :
    Connector {
    init {

        // checking if factory is really functional.
        var sock: USocketFactory.Socket? = null
        sock = try {
            open()
        } catch (e: IOException) {
            throw AgentProxyException(e.toString())
        } catch (e: Exception) {
            throw AgentProxyException(e.toString())
        } finally {
            try {
                sock?.close()
            } catch (e: IOException) {
                throw AgentProxyException(e.toString())
            }
        }
    }

    override fun getName(): String {
        return "ssh-agent"
    }

    override fun isAvailable(): Boolean {
        return isConnectorAvailable
    }

    @Throws(IOException::class)
    private fun open(): USocketFactory.Socket? {
        var ssh_auth_sock = usocketPath
        if (ssh_auth_sock == null) {
            ssh_auth_sock = System.getenv("SSH_AUTH_SOCK")
        }
        if (ssh_auth_sock == null) {
            throw IOException("SSH_AUTH_SOCK is not defined.")
        }
        return factory.open(ssh_auth_sock)
    }

    @Throws(AgentProxyException::class)
    override fun query(buffer: Buffer) {
        var sock: USocketFactory.Socket? = null
        try {
            sock = open()
            sock!!.write(buffer.buffer, 0, buffer.length)
            buffer.rewind()
            var i = sock.readFull(buffer.buffer, 0, 4) // length
            i = buffer.int
            buffer.rewind()
            buffer.checkFreeSize(i)
            i = sock.readFull(buffer.buffer, 0, i)
        } catch (e: IOException) {
            throw AgentProxyException(e.toString())
        } finally {
            try {
                sock?.close()
            } catch (e: IOException) {
                throw AgentProxyException(e.toString())
            }
        }
    }

    companion object {
        val isConnectorAvailable: Boolean
            get() = isConnectorAvailable(null)

        fun isConnectorAvailable(usocketPath: String?): Boolean {
            return System.getenv("SSH_AUTH_SOCK") != null || usocketPath != null
        }
    }
}
