package dk.sdu.cloud.avatar.http

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.avatar.api.FindBulkRequest
import dk.sdu.cloud.avatar.api.FindBulkResponse
import dk.sdu.cloud.avatar.api.FindResponse
import dk.sdu.cloud.avatar.api.Top
import dk.sdu.cloud.avatar.avatar
import dk.sdu.cloud.avatar.serializedAvatar
import dk.sdu.cloud.avatar.services.AvatarService
import dk.sdu.cloud.avatar.updateRequest
import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.service.Controller
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.test.KtorApplicationTestSetupContext
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.assertStatus
import dk.sdu.cloud.service.test.assertSuccess
import dk.sdu.cloud.service.test.sendJson
import dk.sdu.cloud.service.test.sendRequest
import dk.sdu.cloud.service.test.withKtorTest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.Test
import kotlin.test.assertEquals

class AvatarControllerTest {
    val service = mockk<AvatarService<HibernateSession>>()

    private val setup: KtorApplicationTestSetupContext.() -> List<Controller> = {
        listOf(AvatarController(service))
    }

    @Test
    fun `Update test - success`() {
        withKtorTest(
            setup,
            test = {
                every { service.upsert(any(), any()) } just Runs

                sendJson(
                    method = HttpMethod.Post,
                    path = "api/avatar/update",
                    user = TestUsers.user,
                    request = updateRequest
                ).assertSuccess()
            }
        )
    }

    @Test
    fun `Update test - bad input`() {
        withKtorTest(
            setup,
            test = {

                val createResponse = sendJson(
                    method = HttpMethod.Post,
                    path = "api/avatar/update",
                    user = TestUsers.user,
                    request = updateRequest.copy(top = "notAvail")
                )

                createResponse.assertStatus(HttpStatusCode.BadRequest)

            }
        )
    }

    @Test
    fun `FindByUser test`() {
        withKtorTest(
            setup,
            test = {

                every { service.findByUser(any()) } answers {
                    avatar
                }

                val findResponse = sendRequest(
                    method = HttpMethod.Get,
                    path = "api/avatar/find",
                    user = TestUsers.user
                )

                findResponse.assertSuccess()

                val response = defaultMapper.readValue<FindResponse>(findResponse.response.content!!)
                assertEquals(avatar.top.string, response.top)
                assertEquals(avatar.topAccessory.string, response.topAccessory)
                assertEquals(avatar.hairColor.string, response.hairColor)
                assertEquals(avatar.facialHair.string, response.facialHair)
                assertEquals(avatar.facialHairColor.string, response.facialHairColor)
                assertEquals(avatar.clothes.string, response.clothes)
                assertEquals(avatar.colorFabric.string, response.colorFabric)
                assertEquals(avatar.eyes.string, response.eyes)
                assertEquals(avatar.eyebrows.string, response.eyebrows)
                assertEquals(avatar.mouthTypes.string, response.mouthTypes)
                assertEquals(avatar.skinColors.string, response.skinColors)
                assertEquals(avatar.clothesGraphic.string, response.clothesGraphic)
            }
        )
    }

    @Test
    fun `Bulk Find Test`() {
        withKtorTest(
            setup,


            test = {
                every { service.bulkFind(any()) } answers {
                    mapOf(
                        TestUsers.user.username to serializedAvatar,
                        TestUsers.user2.username to serializedAvatar.copy(top = Top.LONG_HAIR_BIG_HAIR.string),
                        "NotFoundUser" to serializedAvatar.copy(top = Top.EYEPATCH.string)
                    )
                }

                val findBulkRequest =
                    sendJson(
                        method = HttpMethod.Post,
                        path = "api/avatar/bulk",
                        user = TestUsers.user,
                        request = FindBulkRequest(
                            listOf(
                                TestUsers.user.username,
                                TestUsers.user2.username,
                                "NotFoundUser"
                            )
                        )
                    )

                findBulkRequest.assertSuccess()
                val response = defaultMapper.readValue<FindBulkResponse>(findBulkRequest.response.content!!)

                val user1 = response.avatars[TestUsers.user.username]
                assertEquals(Top.HAT.string, user1?.top)

                val user2 = response.avatars[TestUsers.user2.username]
                assertEquals(Top.LONG_HAIR_BIG_HAIR.string, user2?.top)

                val avatarNotFoundUser = response.avatars["NotFoundUser"]
                assertEquals(Top.EYEPATCH.string, avatarNotFoundUser?.top)
            }
        )
    }
}
