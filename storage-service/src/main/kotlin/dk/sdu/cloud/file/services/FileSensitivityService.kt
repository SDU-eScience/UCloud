package dk.sdu.cloud.file.services

import dk.sdu.cloud.file.api.SensitivityLevel
import dk.sdu.cloud.service.Loggable
import org.slf4j.Logger

class FileSensitivityService<Ctx : FSUserContext>(
    private val fs: LowLevelFileSystemInterface<Ctx>
) {
    suspend fun setSensitivityLevel(ctx: Ctx, path: String, level: SensitivityLevel) {
        log.debug("setSensitivityLevel(path = $path, level = $level)")
        fs.setExtendedAttribute(ctx, path, XATTRIBUTE, level.name)
    }

    suspend fun clearSensitivityLevel(ctx: Ctx, path: String) {
        fs.deleteExtendedAttribute(ctx, path, XATTRIBUTE)
    }

    companion object : Loggable {
        override val log: Logger = logger()

        const val XATTRIBUTE = "sensitivity"
    }
}
