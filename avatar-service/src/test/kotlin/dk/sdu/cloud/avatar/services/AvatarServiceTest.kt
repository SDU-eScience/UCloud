package dk.sdu.cloud.avatar.services

import dk.sdu.cloud.avatar.api.AvatarRPCException
import dk.sdu.cloud.avatar.api.HairColor
import dk.sdu.cloud.avatar.avatar
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.withDatabase
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AvatarServiceTest {

    private val user = TestUsers.user.username

    @Test
    fun `Insert and find`() {
        withDatabase { db ->

            val dao = AvatarHibernateDAO()
            val service = AvatarService(db, dao)

            var findResult = service.findByUser(user)
            assertNull(findResult)

            val id = service.insert(user, avatar)

            findResult = service.findByUser(user)
            assertNotNull(findResult)

            assertEquals(1, id)
            assertEquals(avatar.top, findResult.top)
            assertEquals(avatar.topAccessory, findResult.topAccessory)
            assertEquals(avatar.hairColor, findResult.hairColor)
            assertEquals(avatar.facialHair, findResult.facialHair)
            assertEquals(avatar.facialHairColor, findResult.facialHairColor)
            assertEquals(avatar.clothes, findResult.clothes)
            assertEquals(avatar.colorFabric, findResult.colorFabric)
            assertEquals(avatar.eyes, findResult.eyes)
            assertEquals(avatar.eyebrows, findResult.eyebrows)
            assertEquals(avatar.mouthTypes, findResult.mouthTypes)
            assertEquals(avatar.skinColors, findResult.skinColors)
            assertEquals(avatar.clothesGraphic, findResult.clothesGraphic)

        }
    }

    @Test
    fun `insert, update and find test`() {
        withDatabase { db ->
            val dao = AvatarHibernateDAO()
            val service = AvatarService(db, dao)

            var findResult = service.findByUser(user)
            assertNull(findResult)

            val id = service.insert(user, avatar)

            findResult = service.findByUser(user)
            assertNotNull(findResult)

            assertEquals(1, id)
            assertEquals(avatar.top, findResult.top)
            assertEquals(avatar.topAccessory, findResult.topAccessory)
            assertEquals(avatar.hairColor, findResult.hairColor)
            assertEquals(avatar.facialHair, findResult.facialHair)
            assertEquals(avatar.facialHairColor, findResult.facialHairColor)
            assertEquals(avatar.clothes, findResult.clothes)
            assertEquals(avatar.colorFabric, findResult.colorFabric)
            assertEquals(avatar.eyes, findResult.eyes)
            assertEquals(avatar.eyebrows, findResult.eyebrows)
            assertEquals(avatar.mouthTypes, findResult.mouthTypes)
            assertEquals(avatar.skinColors, findResult.skinColors)
            assertEquals(avatar.clothesGraphic, findResult.clothesGraphic)

            service.update(user, avatar.copy(id = 1, hairColor = HairColor.RED))


            findResult = service.findByUser(user)
            assertNotNull(findResult)

            assertEquals(1, id)
            assertEquals(avatar.top, findResult.top)
            assertEquals(avatar.topAccessory, findResult.topAccessory)
            assertEquals(HairColor.RED, findResult.hairColor)
            assertEquals(avatar.facialHair, findResult.facialHair)
            assertEquals(avatar.facialHairColor, findResult.facialHairColor)
            assertEquals(avatar.clothes, findResult.clothes)
            assertEquals(avatar.colorFabric, findResult.colorFabric)
            assertEquals(avatar.eyes, findResult.eyes)
            assertEquals(avatar.eyebrows, findResult.eyebrows)
            assertEquals(avatar.mouthTypes, findResult.mouthTypes)
            assertEquals(avatar.skinColors, findResult.skinColors)
            assertEquals(avatar.clothesGraphic, findResult.clothesGraphic)

        }
    }

    @Test (expected = AvatarRPCException.Duplicate::class)
    fun `Insert twice`() {
        withDatabase { db ->

            val dao = AvatarHibernateDAO()
            val service = AvatarService(db, dao)

            var findResult = service.findByUser(user)
            assertNull(findResult)

            service.insert(user, avatar)

            findResult = service.findByUser(user)
            assertNotNull(findResult)

            service.insert(user, avatar)

        }
    }

    @Test (expected = AvatarRPCException.NotFound::class)
    fun `Update non existing`() {
        withDatabase { db ->

            val dao = AvatarHibernateDAO()
            val service = AvatarService(db, dao)

            val findResult = service.findByUser(user)
            assertNull(findResult)

            service.update(user, avatar)
        }
    }
}
