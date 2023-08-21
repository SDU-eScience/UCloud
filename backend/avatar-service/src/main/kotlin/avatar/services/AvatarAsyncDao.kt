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

private fun BinaryAllocator.defaultAvatar(): Avatar =
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
private fun rowToAvatar(allocator: BinaryAllocator, row: RowData) = allocator.Avatar(
    row.getField(AvatarTable.top).let { field -> runCatching { Top.fromSerialName(field) }.getOrNull() ?: Top.valueOf(field) },
    row.getField(AvatarTable.topAccessory).let { field -> runCatching { TopAccessory.fromSerialName(field) }.getOrNull() ?: TopAccessory.valueOf(field) },
    row.getField(AvatarTable.hairColor).let { field -> runCatching { HairColor.fromSerialName(field) }.getOrNull() ?: HairColor.valueOf(field) },
    row.getField(AvatarTable.facialHair).let { field -> runCatching { FacialHair.fromSerialName(field) }.getOrNull() ?: FacialHair.valueOf(field) },
    row.getField(AvatarTable.facialHairColor).let { field -> runCatching { FacialHairColor.fromSerialName(field) }.getOrNull() ?: FacialHairColor.valueOf(field) },
    row.getField(AvatarTable.clothes).let { field -> runCatching { Clothes.fromSerialName(field) }.getOrNull() ?: Clothes.valueOf(field) },
    row.getField(AvatarTable.colorFabric).let { field -> runCatching { ColorFabric.fromSerialName(field) }.getOrNull() ?: ColorFabric.valueOf(field) },
    row.getField(AvatarTable.eyes).let { field -> runCatching { Eyes.fromSerialName(field) }.getOrNull() ?: Eyes.valueOf(field) },
    row.getField(AvatarTable.eyebrows).let { field -> runCatching { Eyebrows.fromSerialName(field) }.getOrNull() ?: Eyebrows.valueOf(field) },
    row.getField(AvatarTable.mouthTypes).let { field -> runCatching { MouthTypes.fromSerialName(field) }.getOrNull() ?: MouthTypes.valueOf(field) },
    row.getField(AvatarTable.skinColors).let { field -> runCatching { SkinColors.fromSerialName(field) }.getOrNull() ?: SkinColors.valueOf(field) },
    row.getField(AvatarTable.clothesGraphic).let { field -> runCatching { ClothesGraphic.fromSerialName(field) }.getOrNull() ?: ClothesGraphic.valueOf(field) },
    row.getField(AvatarTable.hatColor).let { field -> runCatching { HatColor.fromSerialName(field) }.getOrNull() ?: HatColor.valueOf(field) },
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
                    setParameter("top", avatar.top.serialName)
                    setParameter("top_accessory", avatar.topAccessory.serialName)
                    setParameter("hair_color", avatar.hairColor.serialName)
                    setParameter("facial_hair", avatar.facialHair.serialName)
                    setParameter("facial_hair_color", avatar.facialHairColor.serialName)
                    setParameter("clothes", avatar.clothes.serialName)
                    setParameter("color_fabric", avatar.colorFabric.serialName)
                    setParameter("eyes", avatar.eyes.serialName)
                    setParameter("eyebrows", avatar.eyebrows.serialName)
                    setParameter("mouth_types", avatar.mouthTypes.serialName)
                    setParameter("skin_colors", avatar.skinColors.serialName)
                    setParameter("clothes_graphic", avatar.clothesGraphic.serialName)
                    setParameter("hat_color", avatar.hatColor.serialName)
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
        allocator: BinaryAllocator,
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
                ?.let { rowToAvatar(allocator, it) }
                ?: allocator.defaultAvatar()
        }
    }

    suspend fun bulkFind(
        allocator: BinaryAllocator,
        users: List<String>,
        ctx: DBContext = db,
    ): FindBulkResponse {
        with(allocator) {
            return ctx.withSession { session ->
                val avatars = session
                    .sendPreparedStatement(
                        { setParameter("usernames", users) },
                        "select * from avatar.avatars where username = some(:usernames::text[])"
                    ).rows
                    .map { it.getField(AvatarTable.username) to rowToAvatar(allocator, it) }

                FindBulkResponse(dictOf(Avatar, avatars))
            }
        }
    }
}
