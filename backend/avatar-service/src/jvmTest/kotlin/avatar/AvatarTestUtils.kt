package dk.sdu.cloud.avatar

import dk.sdu.cloud.avatar.api.*

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
    ClothesGraphic.BEAR,
    HatColor.BLUE01
)

val serializedAvatar = SerializedAvatar(
    Top.HAT.string,
    TopAccessory.KURT.string,
    HairColor.BLACK.string,
    FacialHair.BEARD_MEDIUM.string,
    FacialHairColor.BLACK.string,
    Clothes.BLAZER_SHIRT.string,
    ColorFabric.GRAY01.string,
    Eyes.DIZZY.string,
    Eyebrows.RAISED_EXCITED.string,
    MouthTypes.CONCERNED.string,
    SkinColors.LIGHT.string,
    ClothesGraphic.BEAR.string,
    HatColor.BLUE01.string
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
    ClothesGraphic.DEER.string,
    HatColor.BLUE01.string
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
    ClothesGraphic.DEER.string,
    HatColor.BLUE01.string
)
