package dk.sdu.cloud.avatars.api

import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.messages.decodeFromJson
import dk.sdu.cloud.messages.dictOf
import dk.sdu.cloud.messages.stringListOf
import dk.sdu.cloud.messages.useAllocator
import kotlinx.serialization.json.JsonElement

typealias CurrentFindBulkRequest = dk.sdu.cloud.avatar.api.FindBulkRequest
typealias CurrentAvatar = dk.sdu.cloud.avatar.api.Avatar

fun main() {
    val serializer = B.serializer()

    val encoded = useAllocator {
        val avatar = Avatar(
            Top.EYEPATCH,
            TopAccessory.BLANK,
            HairColor.AUBURN,
            FacialHair.BEARD_LIGHT,
            FacialHairColor.AUBURN,
            Clothes.BLAZER_SHIRT,
            ColorFabric.BLACK,
            Eyes.CLOSE,
            Eyebrows.ANGRY,
            MouthTypes.CONCERNED,
            SkinColors.BLACK,
            ClothesGraphic.BAT,
            HatColor.BLACK,
        )

        println(slicedBuffer().remaining())
    }

    /*
    println(encoded)

    defaultMapper.decodeFromString(serializer, encoded).apply {
        println(this.value)
    }

    useAllocator {
        val f = serializer.companion.decodeFromJson(this, encoded)
        println(f.value)
    }


    useAllocator {
        val f = FindBulkRequest(
            stringListOf("Fie", "Fie", "Fie", "hund")
        )

        f.username.forEachIndexed { index, text ->
            println("$index: ${text.decode()}")
        }

        slicedBuffer().also { println(it.remaining()) }
        val jsonEncoded = defaultMapper.encodeToString(JsonElement.serializer(), f.encodeToJson())
        println(jsonEncoded)
        println(jsonEncoded.length)
    }

     */
    /*
    useAllocator {
        val f = FindBulkResponse(dictOf(
            Avatar,
            "fie" to Avatar(
                Top.EYEPATCH,
                TopAccessory.BLANK,
                HairColor.AUBURN,
                FacialHair.BEARD_LIGHT,
                FacialHairColor.AUBURN,
                Clothes.BLAZER_SHIRT,
                ColorFabric.BLACK,
                Eyes.CLOSE,
                Eyebrows.ANGRY,
                MouthTypes.CONCERNED,
                SkinColors.BLACK,
                ClothesGraphic.BAT,
                HatColor.BLACK,
            ),
            "hund" to Avatar(
                Top.LONG_HAIR_BOB,
                TopAccessory.BLANK,
                HairColor.AUBURN,
                FacialHair.BEARD_LIGHT,
                FacialHairColor.AUBURN,
                Clothes.BLAZER_SHIRT,
                ColorFabric.BLACK,
                Eyes.CLOSE,
                Eyebrows.ANGRY,
                MouthTypes.CONCERNED,
                SkinColors.BLACK,
                ClothesGraphic.BAT,
                HatColor.BLACK,
            )
        ))

        for ((k, v) in f.avatars) {
            println("$k: ${v.top}")
        }

        println(slicedBuffer().remaining())

        val message = defaultMapper.encodeToString(JsonElement.serializer(), f.encodeToJson())
        println(message)
        println(message.length)
    }

     */
}
