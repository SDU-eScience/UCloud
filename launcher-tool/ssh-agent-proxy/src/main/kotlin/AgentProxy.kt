/*
Copyright (c) 2012 ymnk, JCraft,Inc. All rights reserved.
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
package com.jcraft.jsch.agentproxy

class AgentProxy(@get:Synchronized val connector: Connector) {
    private val buf = ByteArray(1024)
    private val buffer: Buffer = Buffer(buf)

    @get:Synchronized
    val identities: Array<Identity?>
        get() {
            var identities: Array<Identity?>? = null
            val code1 = SSH2_AGENTC_REQUEST_IDENTITIES
            val code2 = SSH2_AGENT_IDENTITIES_ANSWER
            buffer.reset()
            buffer.putByte(code1)
            buffer.insertLength()
            try {
                connector.query(buffer)
            } catch (e: AgentProxyException) {
                buffer.rewind()
                buffer.putByte(SSH_AGENT_FAILURE)
                identities = arrayOfNulls<Identity>(0)
                return identities
            }
            val rcode: Int = buffer.byte
            check_reply(rcode)
            val count: Int = buffer.int

            //System.out.println(count);
            identities = arrayOfNulls<Identity>(count)
            for (i in identities.indices) {
                identities!![i] = Identity(buffer.string, buffer.string)
            }
            return identities
        }

    @Synchronized
    fun sign(blob: ByteArray, data: ByteArray): ByteArray? {
        var result: ByteArray? = null
        val code1 = SSH2_AGENTC_SIGN_REQUEST
        val code2 = SSH2_AGENT_SIGN_RESPONSE
        val required_size = 1 + 4 * 4 + blob.size + data.size
        buffer.reset()
        buffer.checkFreeSize(required_size)
        buffer.putByte(code1)
        buffer.putString(blob)
        buffer.putString(data)
        buffer.putInt(0) // SSH_AGENT_OLD_SIGNATURE
        buffer.insertLength()
        try {
            connector.query(buffer)
        } catch (e: AgentProxyException) {
            buffer.rewind()
            buffer.putByte(SSH_AGENT_FAILURE)
        }
        val rcode: Int = buffer.byte
        check_reply(rcode)

//System.out.println(rcode == code2);
        result = buffer.string
        return result
    }

    @Synchronized
    fun removeIdentity(blob: ByteArray): Boolean {
        val code1 = SSH2_AGENTC_REMOVE_IDENTITY
        val required_size = 1 + 4 * 2 + blob.size
        buffer.reset()
        buffer.checkFreeSize(required_size)
        buffer.putByte(code1)
        buffer.putString(blob)
        buffer.insertLength()
        try {
            connector.query(buffer)
        } catch (e: AgentProxyException) {
            buffer.rewind()
            buffer.putByte(SSH_AGENT_FAILURE)
        }
        check_reply(buffer.byte)

        // TODO
        return true
    }

    @Synchronized
    fun removeAllIdentities() {
        val code1 = SSH2_AGENTC_REMOVE_ALL_IDENTITIES
        buffer.reset()
        buffer.putByte(code1)
        buffer.insertLength()
        try {
            connector.query(buffer)
        } catch (e: AgentProxyException) {
            buffer.rewind()
            buffer.putByte(SSH_AGENT_FAILURE)
        }
        check_reply(buffer.byte)
    }

    @Synchronized
    fun addIdentity(identity: ByteArray): Boolean {
        val code1 = SSH2_AGENTC_ADD_IDENTITY
        val required_size = 1 + 4 + identity.size
        buffer.reset()
        buffer.checkFreeSize(required_size)
        buffer.putByte(code1)
        buffer.putByte(identity)
        buffer.insertLength()
        try {
            connector.query(buffer)
        } catch (e: AgentProxyException) {
            buffer.rewind()
            buffer.putByte(SSH_AGENT_FAILURE)
        }
        check_reply(buffer.byte)
        return true
    }

    @get:Synchronized
    val isRunning: Boolean
        get() {
            if (!connector.isAvailable()) return false
            val code1 = SSH2_AGENTC_REQUEST_IDENTITIES
            buffer.reset()
            buffer.putByte(code1)
            buffer.insertLength()
            try {
                connector.query(buffer)
            } catch (e: AgentProxyException) {
                return false
            }
            return buffer.byte == SSH2_AGENT_IDENTITIES_ANSWER.toInt()
        }

    // TODO
    private fun check_reply(typ: Int): Boolean {
        // println("check_reply: "+typ)
        return true
    }

    companion object {
        private const val SSH_AGENTC_REQUEST_RSA_IDENTITIES: Byte = 1
        private const val SSH_AGENT_RSA_IDENTITIES_ANSWER: Byte = 2
        private const val SSH_AGENTC_RSA_CHALLENGE: Byte = 3
        private const val SSH_AGENT_RSA_RESPONSE: Byte = 4
        private const val SSH_AGENT_FAILURE: Byte = 5
        private const val SSH_AGENT_SUCCESS: Byte = 6
        private const val SSH_AGENTC_ADD_RSA_IDENTITY: Byte = 7
        private const val SSH_AGENTC_REMOVE_RSA_IDENTITY: Byte = 8
        private const val SSH_AGENTC_REMOVE_ALL_RSA_IDENTITIES: Byte = 9
        private const val SSH2_AGENTC_REQUEST_IDENTITIES: Byte = 11
        private const val SSH2_AGENT_IDENTITIES_ANSWER: Byte = 12
        private const val SSH2_AGENTC_SIGN_REQUEST: Byte = 13
        private const val SSH2_AGENT_SIGN_RESPONSE: Byte = 14
        private const val SSH2_AGENTC_ADD_IDENTITY: Byte = 17
        private const val SSH2_AGENTC_REMOVE_IDENTITY: Byte = 18
        private const val SSH2_AGENTC_REMOVE_ALL_IDENTITIES: Byte = 19
        private const val SSH_AGENTC_ADD_SMARTCARD_KEY: Byte = 20
        private const val SSH_AGENTC_REMOVE_SMARTCARD_KEY: Byte = 21
        private const val SSH_AGENTC_LOCK: Byte = 22
        private const val SSH_AGENTC_UNLOCK: Byte = 23
        private const val SSH_AGENTC_ADD_RSA_ID_CONSTRAINED: Byte = 24
        private const val SSH2_AGENTC_ADD_ID_CONSTRAINED: Byte = 25
        private const val SSH_AGENTC_ADD_SMARTCARD_KEY_CONSTRAINED: Byte = 26
        private const val SSH_AGENT_CONSTRAIN_LIFETIME: Byte = 1
        private const val SSH_AGENT_CONSTRAIN_CONFIRM: Byte = 2
        private const val SSH2_AGENT_FAILURE: Byte = 30
        private const val SSH_COM_AGENT2_FAILURE: Byte = 102
        private const val SSH_AGENT_OLD_SIGNATURE: Byte = 0x01
    }
}
