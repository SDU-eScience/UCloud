package dk.sdu.cloud.avatars.api

import dk.sdu.cloud.defaultMapper
import dk.sdu.cloud.messages.decodeFromJson
import dk.sdu.cloud.messages.useAllocator

fun main() {
    val serializer = B.serializer()

    val encoded = useAllocator {
        val value = A(B(42))
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

        defaultMapper.encodeToString(serializer, value.fie)
    }

    println(encoded)

    defaultMapper.decodeFromString(serializer, encoded).apply {
        println(this.value)
    }

    useAllocator {
        val f = serializer.companion.decodeFromJson(this, encoded)
        println(f.value)
    }
}
