package dk.sdu.cloud

import kotlin.native.concurrent.freeze
import kotlin.native.concurrent.isFrozen

actual fun <T> T.freeze(): T = freeze()
actual fun <T> T.isFrozen(): Boolean = isFrozen
