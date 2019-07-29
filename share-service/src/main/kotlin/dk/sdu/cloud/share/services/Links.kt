package dk.sdu.cloud.share.services

import dk.sdu.cloud.calls.client.AuthenticatedClient
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orNull
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.file.api.FileDescriptions
import dk.sdu.cloud.file.api.FindHomeFolderRequest
import dk.sdu.cloud.file.api.StatRequest
import dk.sdu.cloud.file.api.StorageFile
import dk.sdu.cloud.file.api.fileName
import dk.sdu.cloud.file.api.joinPath
import dk.sdu.cloud.indexing.api.LookupDescriptions
import dk.sdu.cloud.indexing.api.ReverseLookupRequest

suspend fun findShareLink(
    existingShare: InternalShare,
    serviceClient: AuthenticatedClient,
    userClient: AuthenticatedClient
): StorageFile? {
    val linkId = existingShare.linkId ?: return null
    val result = LookupDescriptions.reverseLookup.call(
        ReverseLookupRequest(linkId),
        serviceClient
    ).orNull() ?: return null

    val path = result.canonicalPath.firstOrNull()
    if (path != null) {
        return FileDescriptions.stat.call(StatRequest(path), userClient).orNull()
    } else {
        // It might happen in some cases that the link has not yet been indexed. In this case we hope that the link
        // at the path we originally recorded is still valid.
        val linkPath = existingShare.linkPath
        if (linkPath != null) {
            return FileDescriptions.stat.call(StatRequest(linkPath), userClient).orNull()
        }
    }

    return null
}

suspend fun defaultLinkToShare(share: InternalShare, serviceClient: AuthenticatedClient): String {
    val homeFolder = FileDescriptions.findHomeFolder.call(
        FindHomeFolderRequest(share.sharedWith),
        serviceClient
    ).orThrow().path
    return joinPath(homeFolder, share.path.fileName())
}

