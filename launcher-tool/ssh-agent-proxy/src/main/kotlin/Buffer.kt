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

package com.jcraft.jsch.agentproxy

class Buffer {
    val tmp = ByteArray(4)
    var buffer: ByteArray
    var index: Int
    var offSet: Int

    @JvmOverloads
    constructor(size: Int = 1024 * 10 * 2) {
        buffer = ByteArray(size)
        index = 0
        offSet = 0
    }

    constructor(buffer: ByteArray) {
        this.buffer = buffer
        index = 0
        offSet = 0
    }

    fun putByte(foo: Byte) {
        buffer[index++] = foo
    }

    @JvmOverloads
    fun putByte(foo: ByteArray, begin: Int = 0, length: Int = foo.size) {
        System.arraycopy(foo, begin, buffer, index, length)
        index += length
    }

    @JvmOverloads
    fun putString(foo: ByteArray, begin: Int = 0, length: Int = foo.size) {
        putInt(length)
        putByte(foo, begin, length)
    }

    fun putInt(`val`: Int) {
        tmp[0] = (`val` ushr 24).toByte()
        tmp[1] = (`val` ushr 16).toByte()
        tmp[2] = (`val` ushr 8).toByte()
        tmp[3] = `val`.toByte()
        System.arraycopy(tmp, 0, buffer, index, 4)
        index += 4
    }

    fun putLong(`val`: Long) {
        tmp[0] = (`val` ushr 56).toByte()
        tmp[1] = (`val` ushr 48).toByte()
        tmp[2] = (`val` ushr 40).toByte()
        tmp[3] = (`val` ushr 32).toByte()
        System.arraycopy(tmp, 0, buffer, index, 4)
        tmp[0] = (`val` ushr 24).toByte()
        tmp[1] = (`val` ushr 16).toByte()
        tmp[2] = (`val` ushr 8).toByte()
        tmp[3] = `val`.toByte()
        System.arraycopy(tmp, 0, buffer, index + 4, 4)
        index += 8
    }

    fun skip(n: Int) {
        index += n
    }

    fun putPad(n: Int) {
        var n = n
        while (n > 0) {
            buffer[index++] = 0.toByte()
            n--
        }
    }

    fun putMPInt(foo: ByteArray) {
        var i = foo.size
        if (foo[0].toInt() and 0x80 != 0) {
            i++
            putInt(i)
            putByte(0.toByte())
        } else {
            putInt(i)
        }
        putByte(foo)
    }

    val length: Int
        get() = index - offSet
    val long: Long
        get() {
            var foo = int.toLong() and 0xffffffffL
            foo = foo shl 32 or (int.toLong() and 0xffffffffL)
            return foo
        }
    val int: Int
        get() {
            var foo = short
            foo = foo shl 16 and -0x10000 or (short and 0xffff)
            return foo
        }
    val uInt: Long
        get() {
            var foo = 0L
            var bar = 0L
            foo = byte.toLong()
            foo = foo shl 8 and 0xff00L or (byte and 0xff).toLong()
            bar = byte.toLong()
            bar = bar shl 8 and 0xff00L or (byte and 0xff).toLong()
            foo = foo shl 16 and 0xffff0000L or (bar and 0xffffL)
            return foo
        }
    val short: Int
        get() {
            var foo = byte
            foo = foo shl 8 and 0xff00 or (byte and 0xff)
            return foo
        }
    val byte: Int
        get() = buffer[offSet++].toInt() and 0xff

    fun getByte(foo: ByteArray) {
        getByte(foo, 0, foo.size)
    }

    fun getByte(foo: ByteArray?, start: Int, len: Int) {
        System.arraycopy(buffer, offSet, foo, start, len)
        offSet += len
    }

    fun getByte(len: Int): Int {
        val foo = offSet
        offSet += len
        return foo
    }// TODO: an exception should be thrown.

    // the session will be broken, but working around OOME.
// bigger than 0x7fffffff
    // uint32
    val mPInt: ByteArray
        get() {
            var i = int // uint32
            if (i < 0 ||  // bigger than 0x7fffffff
                i > 8 * 1024
            ) {
                // TODO: an exception should be thrown.
                i = 8 * 1024 // the session will be broken, but working around OOME.
            }
            val foo = ByteArray(i)
            getByte(foo, 0, i)
            return foo
        }

    // ??
    val mPIntBits: ByteArray
        get() {
            val bits = int
            val bytes = (bits + 7) / 8
            var foo = ByteArray(bytes)
            getByte(foo, 0, bytes)
            if (foo[0].toInt() and 0x80 != 0) {
                val bar = ByteArray(foo.size + 1)
                bar[0] = 0 // ??
                System.arraycopy(foo, 0, bar, 1, foo.size)
                foo = bar
            }
            return foo
        }// TODO: an exception should be thrown.
    // the session will be broken, but working around OOME.
// bigger than 0x7fffffff

    // uint32
    val string: ByteArray
        get() {
            var i = int // uint32
            if (i < 0 ||  // bigger than 0x7fffffff
                i > 256 * 1024
            ) {
                // TODO: an exception should be thrown.
                i = 256 * 1024 // the session will be broken, but working around OOME.
            }
            val foo = ByteArray(i)
            getByte(foo, 0, i)
            return foo
        }

    fun getString(start: IntArray, len: IntArray): ByteArray {
        val i = int
        start[0] = getByte(i)
        len[0] = i
        return buffer
    }

    fun reset() {
        index = 0
        offSet = 0
    }

    fun shift() {
        if (offSet == 0) return
        System.arraycopy(buffer, offSet, buffer, 0, index - offSet)
        index = index - offSet
        offSet = 0
    }

    fun rewind() {
        offSet = 0
    }

    val command: Byte
        get() = buffer[5]

    fun checkFreeSize(n: Int) {
        if (buffer.size < index + n) {
            val tmp = ByteArray((index + n) * 2)
            System.arraycopy(buffer, 0, tmp, 0, index)
            buffer = tmp
        }
    }

    fun insertLength() {
        val length = length
        System.arraycopy(buffer, 0, buffer, 4, length)
        reset()
        putInt(length)
        skip(length)
    }
}
