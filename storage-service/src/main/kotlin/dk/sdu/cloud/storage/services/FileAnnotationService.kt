package dk.sdu.cloud.storage.services

import dk.sdu.cloud.storage.api.validateAnnotation
import java.util.*

class FileAnnotationService<Ctx : FSUserContext>(
    private val fs: LowLevelFileSystemInterface<Ctx>
) {
    fun annotateFiles(ctx: Ctx, path: String, annotation: String) {
        validateAnnotation(annotation)
        fs.setExtendedAttribute(ctx, path, "annotate${UUID.randomUUID().toString().replace("-", "")}", annotation)
    }
}