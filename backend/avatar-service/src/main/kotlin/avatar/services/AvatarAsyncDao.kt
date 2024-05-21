package dk.sdu.cloud.avatar.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.avatar.api.*
import dk.sdu.cloud.messages.BinaryAllocator
import dk.sdu.cloud.messages.BinaryTypeDictionary
import dk.sdu.cloud.messages.dictOf
import dk.sdu.cloud.service.db.async.DBContext
import dk.sdu.cloud.service.db.async.SQLTable
import dk.sdu.cloud.service.db.async.getField
import dk.sdu.cloud.service.db.async.insert
import dk.sdu.cloud.service.db.async.sendPreparedStatement
import dk.sdu.cloud.service.db.async.text
import dk.sdu.cloud.service.db.async.withSession

object AvatarTable : SQLTable("avatars") {
    val username = text("username", notNull = true)
    val top = text("top", notNull = true)
    val topAccessory = text("top_accessory", notNull = true)
    val hairColor = text("hair_color", notNull = true)
    val facialHair = text("facial_hair", notNull = true)
    val facialHairColor = text("facial_hair_color", notNull = true)
    val clothes = text("clothes", notNull = true)
    val colorFabric = text("color_fabric", notNull = true)
    val eyes = text("eyes", notNull = true)
    val eyebrows = text("eyebrows", notNull = true)
    val mouthTypes = text("mouth_types", notNull = true)
    val skinColors = text("skin_colors", notNull = true)
    val clothesGraphic = text("clothes_graphic", notNull = true)
    val hatColor = text("hat_color", notNull = true)
}

private fun defaultAvatar(): Avatar =
    Avatar(
        Top.NO_HAIR,
        TopAccessory.BLANK,
        HairColor.BLACK,
        FacialHair.BLANK,
        FacialHairColor.BLACK,
        Clothes.SHIRT_CREW_NECK,
        ColorFabric.BLACK,
        Eyes.DEFAULT,
        Eyebrows.DEFAULT,
        MouthTypes.SMILE,
        SkinColors.LIGHT,
        ClothesGraphic.BEAR,
        HatColor.BLUE01
    )

// NOTE(Dan): Deals also with old avatars which might use a different encoding.
private fun rowToAvatar(row: RowData) = Avatar(
    row.getField(AvatarTable.top).let { field -> runCatching { Top.fromString(field) }.getOrNull() ?: Top.valueOf(field) },
    row.getField(AvatarTable.topAccessory).let { field -> runCatching { TopAccessory.fromString(field) }.getOrNull() ?: TopAccessory.valueOf(field) },
    row.getField(AvatarTable.hairColor).let { field -> runCatching { HairColor.fromString(field) }.getOrNull() ?: HairColor.valueOf(field) },
    row.getField(AvatarTable.facialHair).let { field -> runCatching { FacialHair.fromString(field) }.getOrNull() ?: FacialHair.valueOf(field) },
    row.getField(AvatarTable.facialHairColor).let { field -> runCatching { FacialHairColor.fromString(field) }.getOrNull() ?: FacialHairColor.valueOf(field) },
    row.getField(AvatarTable.clothes).let { field -> runCatching { Clothes.fromString(field) }.getOrNull() ?: Clothes.valueOf(field) },
    row.getField(AvatarTable.colorFabric).let { field -> runCatching { ColorFabric.fromString(field) }.getOrNull() ?: ColorFabric.valueOf(field) },
    row.getField(AvatarTable.eyes).let { field -> runCatching { Eyes.fromString(field) }.getOrNull() ?: Eyes.valueOf(field) },
    row.getField(AvatarTable.eyebrows).let { field -> runCatching { Eyebrows.fromString(field) }.getOrNull() ?: Eyebrows.valueOf(field) },
    row.getField(AvatarTable.mouthTypes).let { field -> runCatching { MouthTypes.fromString(field) }.getOrNull() ?: MouthTypes.valueOf(field) },
    row.getField(AvatarTable.skinColors).let { field -> runCatching { SkinColors.fromString(field) }.getOrNull() ?: SkinColors.valueOf(field) },
    row.getField(AvatarTable.clothesGraphic).let { field -> runCatching { ClothesGraphic.fromString(field) }.getOrNull() ?: ClothesGraphic.valueOf(field) },
    row.getField(AvatarTable.hatColor).let { field -> runCatching { HatColor.fromString(field) }.getOrNull() ?: HatColor.valueOf(field) },
)

class AvatarStore(private val db: DBContext,) {
    suspend fun upsert(
        user: String,
        avatar: Avatar,
        ctx: DBContext = db,
    ) {
        ctx.withSession { session ->
            session.sendPreparedStatement(
                {
                    setParameter("username", user)
                    setParameter("top", avatar.top.string)
                    setParameter("top_accessory", avatar.topAccessory.string)
                    setParameter("hair_color", avatar.hairColor.string)
                    setParameter("facial_hair", avatar.facialHair.string)
                    setParameter("facial_hair_color", avatar.facialHairColor.string)
                    setParameter("clothes", avatar.clothes.string)
                    setParameter("color_fabric", avatar.colorFabric.string)
                    setParameter("eyes", avatar.eyes.string)
                    setParameter("eyebrows", avatar.eyebrows.string)
                    setParameter("mouth_types", avatar.mouthTypes.string)
                    setParameter("skin_colors", avatar.skinColors.string)
                    setParameter("clothes_graphic", avatar.clothesGraphic.string)
                    setParameter("hat_color", avatar.hatColor.string)
                },
                """
                    insert into avatar.avatars
                    (username, clothes, clothes_graphic, color_fabric, eyebrows, eyes, facial_hair, facial_hair_color, hair_color, mouth_types, skin_colors, top, top_accessory, hat_color) 
                    values
                    (:username, :clothes, :clothes_graphic, :color_fabric, :eyebrows, :eyes, :facial_hair, :facial_hair_color, :hair_color, :mouth_types, :skin_colors, :top, :top_accessory, :hat_color) 
                    on conflict (username)
                    do update set
                        top = excluded.top,
                        top_accessory = excluded.top_accessory,
                        hair_color = excluded.hair_color,
                        facial_hair = excluded.facial_hair,
                        facial_hair_color = excluded.facial_hair_color,
                        clothes = excluded.clothes,
                        color_fabric = excluded.color_fabric,
                        eyes = excluded.eyes,
                        eyebrows = excluded.eyebrows,
                        mouth_types = excluded.mouth_types,
                        skin_colors = excluded.skin_colors,
                        clothes_graphic = excluded.clothes_graphic,
                        hat_color = excluded.hat_color
                """
            )
        }
    }

    suspend fun findByUser(
        user: String,
        ctx: DBContext = db,
    ): Avatar {
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    { setParameter("username", user) },
                    "select * from avatar.avatars where username = :username"
                ).rows
                .singleOrNull()
                ?.let { rowToAvatar(it) }
                ?: defaultAvatar()
        }
    }

    suspend fun bulkFind(
        users: List<String>,
        ctx: DBContext = db,
    ): FindBulkResponse {
        return ctx.withSession { session ->
            val avatars = session
                .sendPreparedStatement(
                    { setParameter("usernames", users) },
                    "select * from avatar.avatars where username = some(:usernames::text[])"
                ).rows
                .associate { it.getField(AvatarTable.username) to rowToAvatar(it) }

            FindBulkResponse(avatars)
        }
    }
}
