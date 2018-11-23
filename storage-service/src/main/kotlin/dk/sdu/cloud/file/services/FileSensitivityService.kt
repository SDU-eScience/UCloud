package dk.sdu.cloud.file.services

import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.file.api.StorageEvent
import dk.sdu.cloud.file.api.StorageEventProducer
import dk.sdu.cloud.service.Loggable
import dk.sdu.cloud.file.util.STORAGE_EVENT_MODE
import dk.sdu.cloud.file.util.unwrap
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.slf4j.Logger

class FileSensitivityService<Ctx : FSUserContext>(
    private val fs: LowLevelFileSystemInterface<Ctx>,
    private val storageEventProducer: StorageEventProducer
) {
    fun setSensitivityLevel(ctx: Ctx, path: String, level: SensitivityLevel, eventCausedBy: String?) {
        log.debug("setSensitivityLevel(path = $path, level = $level)")
        fs.setExtendedAttribute(ctx, path, SENSITIVITY_ATTRIBUTE, level.name)
        val stat = fs.stat(ctx, path, STORAGE_EVENT_MODE).unwrap()

        GlobalScope.launch {
            storageEventProducer.emit(
                StorageEvent.SensitivityUpdated(
                    stat.inode,
                    stat.path,
                    stat.owner,
                    System.currentTimeMillis(),
                    level,
                    eventCausedBy
                )
            )
        }
    }

    companion object : Loggable {
        override val log: Logger = logger()

        const val SENSITIVITY_ATTRIBUTE = "sensitivity"
    }
}
