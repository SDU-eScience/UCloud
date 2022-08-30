package dk.sdu.cloud

import kotlin.random.Random
import kotlin.system.exitProcess

fun testEqual(arr: ByteArray) {
    val commonCode = base64EncodeCommon(arr)
    val jvmCode = base64Encode(arr)
    if (commonCode != jvmCode) {
        println("${arr.toList()} (${arr.size}): ${jvmCode} != ${commonCode}")
    } else {
        val decoded = base64DecodeCommon(commonCode)
        if (!decoded.contentEquals(arr)) {
            println("${arr.toList()} != ${decoded.toList()}")
        }
    }
}

fun main() {
    for (i in 1 until 1000) {
        repeat(1_000) {
            val arr = ByteArray(i) { Random.nextInt().toByte() }
            try {
                testEqual(arr)
            } catch (ex: Throwable) {
                ex.printStackTrace()
                println(arr.toList())
                println(arr.size)
                exitProcess(0)
            }
        }
    }
    println("Done")
}
