package dk.sdu.cloud.storage.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import dk.sdu.cloud.CommonErrorMessage
import dk.sdu.cloud.client.RESTDescriptions
import dk.sdu.cloud.service.KafkaRequest
import dk.sdu.cloud.storage.model.StorageFile
import io.netty.handler.codec.http.HttpMethod

data class FindByPath(val path: String)

object FileDescriptions : RESTDescriptions(StorageServiceDescription) {
    private val baseContext = "/api/files"

    val listAtPath = callDescription<FindByPath, List<StorageFile>, CommonErrorMessage> {
        prettyName = "filesListAtPath"
        path { using(baseContext) }
        params {
            +boundTo(FindByPath::path)
        }
    }

    val markAsFavorite = kafkaDescription<FavoriteCommand.Grant> {
        prettyName = "filesMarkAsFavorite"
        method = HttpMethod.POST

        path {
            using(baseContext)
            +"favorite"
        }

        params {
            +boundTo(FavoriteCommand.Grant::path)
        }
    }

    val removeFavorite = kafkaDescription<FavoriteCommand.Revoke> {
        prettyName = "filesRemoveAsFavorite"
        method = HttpMethod.DELETE

        path {
            using(baseContext)
            +"favorite"
        }

        params {
            +boundTo(FavoriteCommand.Revoke::path)
        }
    }

    val download = callDescription<DownloadByURI, Unit, CommonErrorMessage> {
        prettyName = "filesDownload"
        path {
            using(baseContext)
            +"download"
        }

        params {
            +boundTo(DownloadByURI::path)
            +boundTo(DownloadByURI::token)
        }
    }

    val favoriteBundle = listOf(markAsFavorite, removeFavorite)
}

data class DownloadByURI(val path: String, val token: String)

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = KafkaRequest.TYPE_PROPERTY
)
@JsonSubTypes(
    JsonSubTypes.Type(value = FavoriteCommand.Grant::class, name = "grant"),
    JsonSubTypes.Type(value = FavoriteCommand.Revoke::class, name = "revoke")
)
sealed class FavoriteCommand {
    abstract val path: String

    data class Grant(override val path: String) : FavoriteCommand()
    data class Revoke(override val path: String) : FavoriteCommand()
}