package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.avatar.api.AvatarDescriptions
import dk.sdu.cloud.avatar.api.FindBulkRequest
import dk.sdu.cloud.avatar.api.SerializedAvatar
import dk.sdu.cloud.avatar.api.UpdateRequest
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow


class AvatarTesting(private val userAndClient: UserAndClient, private val otherUser: UserAndClient) {
    lateinit var avatar: SerializedAvatar

    suspend fun runTest() {
        fetchAvatar()
        updateAvatar()
        findBulk()
    }

    private suspend fun fetchAvatar() {
        // TODO Logging
        try {
            avatar = AvatarDescriptions.findAvatar.call(Unit, userAndClient.client).orThrow()
        } catch (e: Exception) {
            throw Exception("Failed to fetch avatar\n $e")
        }
    }

    private suspend fun updateAvatar() {
        // TODO Logging
        val top = if (avatar.top == "NoHair")
            "NoHair"
        else "Hat"
        AvatarDescriptions.update.call(
            UpdateRequest(
                top,
                avatar.topAccessory,
                avatar.hairColor,
                avatar.facialHair,
                avatar.facialHairColor,
                avatar.clothes,
                avatar.colorFabric,
                avatar.eyes,
                avatar.eyebrows,
                avatar.mouthTypes,
                avatar.skinColors,
                avatar.clothesGraphic,
                avatar.hatColor
            ),
            userAndClient.client
        )

        val fetchedTop = AvatarDescriptions.findAvatar.call(Unit, userAndClient.client).orThrow().top
        if (fetchedTop != top)
            throw Exception("Expected $top, got $fetchedTop")
    }

    private suspend fun findBulk() {
        // TODO Logging
        try {
            val result = AvatarDescriptions.findBulk.call(
                FindBulkRequest(listOf(userAndClient.username, otherUser.username)),
                userAndClient.client
            ).orThrow().avatars
            if (result.size != 2) throw Exception("Should find 2 avatars, found ${result.size}")
        } catch (e: Exception) {
            throw Exception(e.toString())
        }
    }
}