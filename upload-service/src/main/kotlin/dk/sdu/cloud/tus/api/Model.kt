package dk.sdu.cloud.tus.api

data class TransferSummary(
        val id: String,
        val length: Long,
        val offset: Long
)

data class TransferState(
        val id: String,

        val length: Long,
        val offset: Long,

        val user: String,
        val zone: String,

        val targetCollection: String,
        val targetName: String
)

data class CreationCommand(
        val fileName: String?,
        val sensitive: Boolean,
        val owner: String?,
        val location: String?,
        val length: Long
)