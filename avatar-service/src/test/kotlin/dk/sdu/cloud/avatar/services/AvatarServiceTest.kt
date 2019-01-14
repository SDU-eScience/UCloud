package dk.sdu.cloud.avatar.services

import dk.sdu.cloud.avatar.api.Clothes
import dk.sdu.cloud.avatar.api.ClothesGraphic
import dk.sdu.cloud.avatar.api.ColorFabric
import dk.sdu.cloud.avatar.api.Eyebrows
import dk.sdu.cloud.avatar.api.Eyes
import dk.sdu.cloud.avatar.api.FacialHair
import dk.sdu.cloud.avatar.api.FacialHairColor
import dk.sdu.cloud.avatar.api.HairColor
import dk.sdu.cloud.avatar.api.MouthTypes
import dk.sdu.cloud.avatar.api.SkinColors
import dk.sdu.cloud.avatar.api.Top
import dk.sdu.cloud.avatar.api.TopAccessory
import dk.sdu.cloud.avatar.avatar
import dk.sdu.cloud.service.test.TestUsers
import dk.sdu.cloud.service.test.withDatabase
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AvatarServiceTest {
    private val user = TestUsers.user.username

    @Test
    fun `Insert and find`() {
        withDatabase { db ->

            val dao = AvatarHibernateDAO()
            val service = AvatarService(db, dao)

            // Nothing inserted - Finds Default
            var findResult = service.findByUser(user)
            assertNotNull(findResult)

            assertEquals(Top.HAT, findResult.top)
            assertEquals(TopAccessory.BLANK, findResult.topAccessory)
            assertEquals(HairColor.BLACK, findResult.hairColor)
            assertEquals(FacialHair.BLANK, findResult.facialHair)
            assertEquals(FacialHairColor.BLACK, findResult.facialHairColor)
            assertEquals(Clothes.SHIRT_CREW_NECK, findResult.clothes)
            assertEquals(ColorFabric.BLACK, findResult.colorFabric)
            assertEquals(Eyes.SURPRISED, findResult.eyes)
            assertEquals(Eyebrows.DEFAULT, findResult.eyebrows)
            assertEquals(MouthTypes.SMILE, findResult.mouthTypes)
            assertEquals(SkinColors.YELLOW, findResult.skinColors)
            assertEquals(ClothesGraphic.BEAR, findResult.clothesGraphic)

            service.upsert(user, avatar)

            findResult = service.findByUser(user)
            assertNotNull(findResult)

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
            assertNotNull(findResult)

            service.upsert(user, avatar)

            findResult = service.findByUser(user)
            assertNotNull(findResult)

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

            service.upsert(user, avatar.copy(hairColor = HairColor.RED))


            findResult = service.findByUser(user)
            assertNotNull(findResult)

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
}
