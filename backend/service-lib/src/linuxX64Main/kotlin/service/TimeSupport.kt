package dk.sdu.cloud.service

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.posix.gettimeofday
import platform.posix.timeval

actual interface TimeProvider {
    actual fun now(): Long
}

actual object Time : TimeProvider {
    override fun now(): Long {
        return memScoped {
            val tv = alloc<timeval>()
            gettimeofday(tv.ptr, null)
            tv.tv_sec * 1000 + tv.tv_usec / 1000
        }
    }
}
