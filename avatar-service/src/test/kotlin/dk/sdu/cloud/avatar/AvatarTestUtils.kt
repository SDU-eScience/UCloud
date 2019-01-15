package dk.sdu.cloud.avatar

import dk.sdu.cloud.avatar.api.Avatar
import dk.sdu.cloud.avatar.api.Clothes
import dk.sdu.cloud.avatar.api.ClothesGraphic
import dk.sdu.cloud.avatar.api.ColorFabric
import dk.sdu.cloud.avatar.api.Eyebrows
import dk.sdu.cloud.avatar.api.Eyes
import dk.sdu.cloud.avatar.api.FacialHair
import dk.sdu.cloud.avatar.api.FacialHairColor
import dk.sdu.cloud.avatar.api.FindResponse
import dk.sdu.cloud.avatar.api.HairColor
import dk.sdu.cloud.avatar.api.MouthTypes
import dk.sdu.cloud.avatar.api.SkinColors
import dk.sdu.cloud.avatar.api.Top
import dk.sdu.cloud.avatar.api.TopAccessory
import dk.sdu.cloud.avatar.api.UpdateRequest

val avatar = Avatar(
    Top.HAT,
    TopAccessory.KURT,
    HairColor.BLACK,
    FacialHair.BEARD_MEDIUM,
    FacialHairColor.BLACK,
    Clothes.BLAZER_SHIRT,
    ColorFabric.GRAY01,
    Eyes.DIZZY,
    Eyebrows.RAISED_EXCITED,
    MouthTypes.CONCERNED,
    SkinColors.LIGHT,
    ClothesGraphic.BEAR
)

val updateRequest = UpdateRequest(
    Top.EYEPATCH.string,
    TopAccessory.KURT.string,
    HairColor.RED.string,
    FacialHair.BEARD_MEDIUM.string,
    FacialHairColor.RED.string,
    Clothes.GRAPHIC_SHIRT.string,
    ColorFabric.GRAY01.string,
    Eyes.DIZZY.string,
    Eyebrows.FLAT_NATURAL.string,
    MouthTypes.EATING.string,
    SkinColors.BLACK.string,
    ClothesGraphic.DEER.string
)

val findResponse = FindResponse(
    Top.EYEPATCH.string,
    TopAccessory.KURT.string,
    HairColor.RED.string,
    FacialHair.BEARD_MEDIUM.string,
    FacialHairColor.RED.string,
    Clothes.GRAPHIC_SHIRT.string,
    ColorFabric.GRAY01.string,
    Eyes.DIZZY.string,
    Eyebrows.FLAT_NATURAL.string,
    MouthTypes.EATING.string,
    SkinColors.BLACK.string,
    ClothesGraphic.DEER.string
)
