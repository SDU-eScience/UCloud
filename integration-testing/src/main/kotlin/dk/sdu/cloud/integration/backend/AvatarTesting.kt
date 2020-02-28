package dk.sdu.cloud.integration.backend

import dk.sdu.cloud.avatar.api.AvatarDescriptions
import dk.sdu.cloud.avatar.api.FindBulkRequest
import dk.sdu.cloud.avatar.api.SerializedAvatar
import dk.sdu.cloud.avatar.api.UpdateRequest
import dk.sdu.cloud.calls.client.call
import dk.sdu.cloud.calls.client.orThrow
import dk.sdu.cloud.service.Loggable


class AvatarTesting(private val userAndClient: UserAndClient, private val otherUser: UserAndClient) {
    lateinit var avatar: SerializedAvatar

    suspend fun runTest() {
        fetchAvatar()
        updateAvatar()
        findBulk()
    }

    private suspend fun fetchAvatar() {
        log.info("Fetching avatar")
        avatar = AvatarDescriptions.findAvatar.call(Unit, userAndClient.client).orThrow()
        log.info("Fetched avatar")
    }

    private suspend fun updateAvatar() {
        log.info("Updating avatar")
        val top =
            if (avatar.top == "NoHair") "NoHair"
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
        check(fetchedTop == top) { "Expected $top, got $fetchedTop" }
        log.info("Successfully updated avatar.")
    }

    private suspend fun findBulk() {
        log.info("Finding multiple avatars")
        val result = AvatarDescriptions.findBulk.call(
            FindBulkRequest(listOf(userAndClient.username, otherUser.username)),
            userAndClient.client
        ).orThrow().avatars
        check(result.size == 2) { "Should find 2 avatars, found ${result.size}" }
        log.info("Successfully fetched multiple avatars.")
    }

    companion object : Loggable {
        override val log = logger()
    }
}