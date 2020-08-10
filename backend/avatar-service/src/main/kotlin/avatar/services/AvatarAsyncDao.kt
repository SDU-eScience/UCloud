package dk.sdu.cloud.avatar.services

import com.github.jasync.sql.db.RowData
import dk.sdu.cloud.avatar.api.*
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


private fun RowData.toAvatar(): Avatar = Avatar(
    Top.fromString(getField(AvatarTable.top)),
    TopAccessory.fromString(getField(AvatarTable.topAccessory)),
    HairColor.fromString(getField(AvatarTable.hairColor)),
    FacialHair.fromString(getField(AvatarTable.facialHair)),
    FacialHairColor.fromString(getField(AvatarTable.facialHairColor)),
    Clothes.fromString(getField(AvatarTable.clothes)),
    ColorFabric.fromString(getField(AvatarTable.colorFabric)),
    Eyes.fromString(getField(AvatarTable.eyes)),
    Eyebrows.fromString(getField(AvatarTable.eyebrows)),
    MouthTypes.fromString(getField(AvatarTable.mouthTypes)),
    SkinColors.fromString(getField(AvatarTable.skinColors)),
    ClothesGraphic.fromString(getField(AvatarTable.clothesGraphic)),
    HatColor.fromString(getField(AvatarTable.hatColor))
)

class AvatarAsyncDao {

    suspend fun upsert(
        ctx: DBContext,
        user: String,
        avatar: Avatar
    ) {
        ctx.withSession { session ->
            val foundAvatar = findInternal(session, user)
            if (foundAvatar != null) {
                session.sendPreparedStatement(
                    {
                        setParameter("username", user)
                        setParameter("top", avatar.top.string)
                        setParameter("topAccessory", avatar.topAccessory.string)
                        setParameter("hairColor", avatar.hairColor.string)
                        setParameter("facialHair", avatar.facialHair.string)
                        setParameter("facialHairColor", avatar.facialHairColor.string)
                        setParameter("clothes", avatar.clothes.string)
                        setParameter("colorFabric", avatar.colorFabric.string)
                        setParameter("eyes", avatar.eyes.string)
                        setParameter("eyebrows", avatar.eyebrows.string)
                        setParameter("mouthTypes", avatar.mouthTypes.string)
                        setParameter("skinColors", avatar.skinColors.string)
                        setParameter("clothesGraphic", avatar.clothesGraphic.string)
                        setParameter("hatColor", avatar.hatColor.string)
                    },
                    """
                        UPDATE avatars
                        SET
                            top = :top,
                            top_accessory = :topAccessory,
                            hair_color = :hairColor,
                            facial_hair = :facialHair,
                            facial_hair_color = :facialHairColor,
                            clothes = :clothes,
                            color_fabric = :colorFabric,
                            eyes = :eyes,
                            eyebrows = :eyebrows,
                            mouth_types = :mouthTypes,
                            skin_colors = :skinColors,
                            clothes_graphic = :clothesGraphic,
                            hat_color = :hatColor
                        WHERE username = :username
                    """
                )
            } else {
                session.insert(AvatarTable) {
                    set(AvatarTable.username, user)
                    set(AvatarTable.top, avatar.top.string)
                    set(AvatarTable.topAccessory, avatar.topAccessory.string)
                    set(AvatarTable.hairColor, avatar.hairColor.string)
                    set(AvatarTable.facialHair, avatar.facialHair.string)
                    set(AvatarTable.facialHairColor, avatar.facialHairColor.string)
                    set(AvatarTable.clothes, avatar.clothes.string)
                    set(AvatarTable.colorFabric, avatar.colorFabric.string)
                    set(AvatarTable.eyes, avatar.eyes.string)
                    set(AvatarTable.eyebrows, avatar.eyebrows.string)
                    set(AvatarTable.mouthTypes, avatar.mouthTypes.string)
                    set(AvatarTable.skinColors, avatar.skinColors.string)
                    set(AvatarTable.clothesGraphic, avatar.clothesGraphic.string)
                    set(AvatarTable.hatColor, avatar.hatColor.string)
                }
            }
        }
    }

    private suspend fun findInternal(
        ctx: DBContext,
        user: String
    ): Avatar? {
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("username", user)
                    },
                    """
                        SELECT *
                        FROM avatars
                        WHERE username = :username
                    """
                ).rows
                .singleOrNull()
                ?.toAvatar()
        }
    }

    suspend fun findByUser(
        ctx: DBContext,
        user: String
    ): Avatar {
        return ctx.withSession { session ->
            session
                .sendPreparedStatement(
                    {
                        setParameter("username", user)
                    },
                    """
                        SELECT *
                        FROM avatars
                        WHERE username = :username
                    """
                ).rows
                .singleOrNull()
                ?.toAvatar() ?: defaultAvatar()
        }
    }

    suspend fun bulkFind(
        ctx: DBContext,
        users: List<String>
    ): Map<String, SerializedAvatar> {
        return ctx.withSession { session ->
            val avatars = session
                .sendPreparedStatement(
                    {
                        setParameter("usernames", users)
                    },
                    """
                        SELECT *
                        FROM avatars
                        WHERE username in (select unnest(:usernames::text[]))
                    """
                ).rows
                .map {
                    it.toUserAndAvatar()
                }

            users.associateWith { user ->
                avatars.find { it.first == user }?.second ?: defaultAvatar().toSerializedAvatar() }
        }
    }

    private fun Avatar.toSerializedAvatar() = SerializedAvatar(
        top.string,
        topAccessory.string,
        hairColor.string,
        facialHair.string,
        facialHairColor.string,
        clothes.string,
        colorFabric.string,
        eyes.string,
        eyebrows.string,
        mouthTypes.string,
        skinColors.string,
        clothesGraphic.string,
        hatColor.string
    )

    private fun RowData.toUserAndAvatar(): Pair<String, SerializedAvatar> =
        getField(AvatarTable.username) to SerializedAvatar(
            getField(AvatarTable.top),
            getField(AvatarTable.topAccessory),
            getField(AvatarTable.hairColor),
            getField(AvatarTable.facialHair),
            getField(AvatarTable.facialHairColor),
            getField(AvatarTable.clothes),
            getField(AvatarTable.colorFabric),
            getField(AvatarTable.eyes),
            getField(AvatarTable.eyebrows),
            getField(AvatarTable.mouthTypes),
            getField(AvatarTable.skinColors),
            getField(AvatarTable.clothesGraphic),
            getField(AvatarTable.hatColor)
        )
}
