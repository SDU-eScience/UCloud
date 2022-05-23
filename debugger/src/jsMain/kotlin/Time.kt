package dk.sdu.cloud.debug

import kotlinx.browser.window

actual object Time {
    actual fun now(): Long {
        return window.performance.now().toLong() + window.performance.timing.navigationStart.toLong()
    }
}
