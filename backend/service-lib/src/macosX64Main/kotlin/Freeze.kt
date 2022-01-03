package dk.sdu.cloud

import kotlin.native.concurrent.freeze

actual fun <T> T.freeze(): T = freeze()
actual fun <T> T.isFrozen(): Boolean = false
