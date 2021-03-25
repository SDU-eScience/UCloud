package dk.sdu.cloud.app.store.api

import kotlinx.serialization.Serializable

@Serializable
data class FindBySupportedFileExtension(val files: List<String>)
