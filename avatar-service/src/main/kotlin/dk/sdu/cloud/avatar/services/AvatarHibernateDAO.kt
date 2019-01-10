package dk.sdu.cloud.avatar.services

import dk.sdu.cloud.avatar.api.Avatar
import dk.sdu.cloud.avatar.api.Clothes
import dk.sdu.cloud.avatar.api.ClothesGraphic
import dk.sdu.cloud.avatar.api.ColorFabric
import dk.sdu.cloud.avatar.api.Duplicate
import dk.sdu.cloud.avatar.api.Eyebrows
import dk.sdu.cloud.avatar.api.Eyes
import dk.sdu.cloud.avatar.api.FacialHair
import dk.sdu.cloud.avatar.api.FacialHairColor
import dk.sdu.cloud.avatar.api.HairColor
import dk.sdu.cloud.avatar.api.MouthTypes
import dk.sdu.cloud.avatar.api.SkinColors
import dk.sdu.cloud.avatar.api.Top
import dk.sdu.cloud.avatar.api.TopAccessory
import dk.sdu.cloud.avatar.api.UpdateRequest
import dk.sdu.cloud.service.db.HibernateEntity
import dk.sdu.cloud.service.db.HibernateSession
import dk.sdu.cloud.service.db.WithId
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.GeneratedValue
import javax.persistence.Id
import javax.persistence.Index
import javax.persistence.Table

@Entity
@Table(name = "avatars",
    indexes = [Index(columnList = "username")])
class AvatarEntity(
    @Column
    var username: String,

    var top: String,
    var topAccessory: String,
    var hairColor: String,
    var facialHair: String,
    var facialHairColor: String,
    var clothes: String,
    var colorFabric: String,
    var eyes: String,
    var eyebrows: String,
    var mouthTypes: String,
    var skinColors: String,
    var clothesGraphic: String,

    @Id
    @GeneratedValue
    var id: Long = 0
) {
    companion object : HibernateEntity<AvatarEntity>, WithId<Long>
}

fun AvatarEntity.toModel() : Avatar = Avatar(
    id,
    username,
    Top.fromString(top),
    TopAccessory.fromString(topAccessory),
    HairColor.fromString(hairColor),
    FacialHair.fromString(facialHair),
    FacialHairColor.fromString(facialHairColor),
    Clothes.fromString(clothes),
    ColorFabric.fromString(colorFabric),
    Eyes.fromString(eyes),
    Eyebrows.fromString(eyebrows),
    MouthTypes.fromString(mouthTypes),
    SkinColors.fromString(skinColors),
    ClothesGraphic.fromString(clothesGraphic)
)

fun Avatar.toEntity() : AvatarEntity = AvatarEntity(
    user,
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
    clothesGraphic.string
)

class AvatarHibernateDAO : AvatarDAO<HibernateSession>{

    override fun insert(
        session: HibernateSession,
        user: String,
        avatar: Avatar
    ) : Long {
        val exists = find(session, user) != null
        if (exists) throw Duplicate()
        val entity = avatar.toEntity()
        return session.save(entity) as Long
    }

    override fun update(
        session: HibernateSession,
        user: String,
        avatar: Avatar
    ) {
        val result = find(session, user)
        result?.id
    }

    override fun find(
        session: HibernateSession,
        user: String
    ) : AvatarEntity? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}
