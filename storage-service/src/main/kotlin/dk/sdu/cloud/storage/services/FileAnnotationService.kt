package dk.sdu.cloud.storage.services

import dk.sdu.cloud.storage.api.validateAnnotation
import dk.sdu.cloud.storage.util.FSUserContext
import java.util.*

class FileAnnotationService(
    private val fs: LowLevelFileSystemInterface
) {
    fun annotateFiles(ctx: FSUserContext, path: String, annotation: String) {
        validateAnnotation(annotation)
        fs.setExtendedAttribute(ctx, path, "annotate${UUID.randomUUID().toString().replace("-", "")}", annotation)
    }
}