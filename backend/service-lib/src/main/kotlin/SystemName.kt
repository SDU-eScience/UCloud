package dk.sdu.cloud

import java.util.concurrent.atomic.AtomicReference

private val _systemName = AtomicReference("ucloud")
val systemName: String get() = _systemName.get()
fun updateSystemName(newName: String) {
    _systemName.set(newName.lowercase().replace('-', '_').replace(' ', '_'))
}
