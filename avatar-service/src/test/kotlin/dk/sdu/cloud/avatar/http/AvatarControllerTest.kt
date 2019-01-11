package dk.sdu.cloud.avatar.http

import com.fasterxml.jackson.module.kotlin.readValue
import dk.sdu.cloud.avatar.api.FindResponse
import dk.sdu.cloud.avatar.avatar
import dk.sdu.cloud.avatar.services.AvatarService
import dk.sdu.cloud.avatar.updateRequest
import dk.sdu.cloud.client.defaultMapper
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

class AvatarControllerTest{
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
}
