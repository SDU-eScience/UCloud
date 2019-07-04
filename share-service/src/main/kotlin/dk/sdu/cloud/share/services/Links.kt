package dk.sdu.cloud.share.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FindHomeFolderRequest
import dk.sdu.cloud.file.api.fileName
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.indexing.api.LookupDescriptions
import dk.sdu.cloud.indexing.api.ReverseLookupRequest

suspend fun findShareLink(
    existingShare: InternalShare,
    serviceClient: AuthenticatedClient
): String? {
    val linkId = existingShare.linkId ?: return null
    val result = LookupDescriptions.reverseLookup.call(
        ReverseLookupRequest(linkId),
        serviceClient
    ).orNull() ?: return null

    return result.canonicalPath.firstOrNull()
}

suspend fun defaultLinkToShare(share: InternalShare, serviceClient: AuthenticatedClient): String {
    val homeFolder = FileDescriptions.findHomeFolder.call(
        FindHomeFolderRequest(share.sharedWith),
        serviceClient
    ).orThrow().path
    return joinPath(homeFolder, share.path.fileName())
}

