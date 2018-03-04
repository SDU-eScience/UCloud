package dk.sdu.cloud.tus.services

import dk.sdu.cloud.storage.ext.irods.ICATConnection

// TODO This should probably go into a service class.
private val DUPLICATE_NAMING_REGEX = Regex("""\((\d+)\)""")

fun ICATConnection.findAvailableIRodsFileName(collectionId: Long, desiredIRodsName: String): String {
    val desiredWithoutExtension = desiredIRodsName.substringBefore('.')
    val extension = '.' + desiredIRodsName.substringAfter('.', missingDelimiterValue = "")
    val names = findIRodsFileNamesLike(collectionId, desiredIRodsName)

    return if (names.isEmpty()) {
        desiredIRodsName
    } else {
        val namesMappedAsIndices = names.mapNotNull {
            val nameWithoutExtension = it.substringBefore('.')
            val nameWithoutPrefix = nameWithoutExtension.substringAfter(desiredWithoutExtension)

            if (nameWithoutPrefix.isEmpty()) {
                0 // We have an exact match on the file name
            } else {
                val match = DUPLICATE_NAMING_REGEX.matchEntire(nameWithoutPrefix)
                if (match == null) {
                    null // The file name doesn't match at all, i.e., the file doesn't collide with our desired name
                } else {
                    match.groupValues.getOrNull(1)?.toIntOrNull()
                }
            }
        }

        if (namesMappedAsIndices.isEmpty()) {
            desiredIRodsName
        } else {
            val currentMax = namesMappedAsIndices.max() ?: 0
            "$desiredWithoutExtension(${currentMax + 1})$extension"
        }
    }
}
