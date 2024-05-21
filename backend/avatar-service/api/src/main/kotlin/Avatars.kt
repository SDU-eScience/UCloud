package dk.sdu.cloud.avatar.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Avatar(
    val top: Top,
    val topAccessory: TopAccessory,
    val hairColor: HairColor,
    val facialHair: FacialHair,
    val facialHairColor: FacialHairColor,
    val clothes: Clothes,
    val colorFabric: ColorFabric,
    val eyes: Eyes,
    val eyebrows: Eyebrows,
    val mouthTypes: MouthTypes,
    val skinColors: SkinColors,
    val clothesGraphic: ClothesGraphic,
    val hatColor: HatColor
)

@Serializable
enum class Top(val string: String) {
    @SerialName("NoHair") NO_HAIR("NoHair"),
    @SerialName("Eyepatch") EYEPATCH("Eyepatch"), // Excludes accessories
    @SerialName("Hat") HAT("Hat"),
    @SerialName("Hijab") HIJAB("Hijab"),
    @SerialName("Turban") TURBAN("Turban"),
    @SerialName("WinterHat1") WINTER_HAT1("WinterHat1"),
    @SerialName("WinterHat2") WINTER_HAT2("WinterHat2"),
    @SerialName("WinterHat3") WINTER_HAT3("WinterHat3"),
    @SerialName("WinterHat4") WINTER_HAT4("WinterHat4"),
    @SerialName("LongHairBigHair") LONG_HAIR_BIG_HAIR("LongHairBigHair"),
    @SerialName("LongHairBob") LONG_HAIR_BOB("LongHairBob"),
    @SerialName("LongHairBun") LONG_HAIR_BUN("LongHairBun"),
    @SerialName("LongHairCurly") LONG_HAIR_CURLY("LongHairCurly"),
    @SerialName("LongHairCurvy") LONG_HAIR_CURVY("LongHairCurvy"),
    @SerialName("LongHairDreads") LONG_HAIR_DREADS("LongHairDreads"),
    @SerialName("LongHairFrida") LONG_HAIR_FRIDA("LongHairFrida"),
    @SerialName("LongHairFro") LONG_HAIR_FRO("LongHairFro"),
    @SerialName("LongHairFroBand") LONG_HAIR_FRO_BAND("LongHairFroBand"),
    @SerialName("LongHairNotTooLong") LONG_HAIR_NOT_TOO_LONG("LongHairNotTooLong"),
    @SerialName("LongHairShavedSides") LONG_HAIR_SHAVED_SIDES("LongHairShavedSides"),
    @SerialName("LongHairMiaWallace") LONG_HAIR_MIA_WALLACE("LongHairMiaWallace"),
    @SerialName("LongHairStraight") LONG_HAIR_STRAIGHT("LongHairStraight"),
    @SerialName("LongHairStraight2") LONG_HAIR_STRAIGHT2("LongHairStraight2"),
    @SerialName("LongHairStraightStrand") LONG_HAIR_STRAIGHT_STRAND("LongHairStraightStrand"),
    @SerialName("ShortHairDreads01") SHORT_HAIR_DREADS01("ShortHairDreads01"),
    @SerialName("ShortHairDreads02") SHORT_HAIR_DREADS02("ShortHairDreads02"),
    @SerialName("ShortHairFrizzle") SHORT_HAIR_FRIZZLE("ShortHairFrizzle"),
    @SerialName("ShortHairShaggyMullet") SHORT_HAIR_SHAGGY_MULLET("ShortHairShaggyMullet"),
    @SerialName("ShortHairShortCurly") SHORT_HAIR_SHORT_CURLY("ShortHairShortCurly"),
    @SerialName("ShortHairShortFlat") SHORT_HAIR_SHORT_FLAT("ShortHairShortFlat"),
    @SerialName("ShortHairShortRound") SHORT_HAIR_SHORT_ROUND("ShortHairShortRound"),
    @SerialName("ShortHairShortWaved") SHORT_HAIR_SHORT_WAVED("ShortHairShortWaved"),
    @SerialName("ShortHairSides") SHORT_HAIR_SIDES("ShortHairSides"),
    @SerialName("ShortHairTheCaesar") SHORT_HAIR_THE_CAESAR("ShortHairTheCaesar"),
    @SerialName("ShortHairTheCaesarSidePart") SHORT_HAIR_THE_CAESAR_SIDE_PART("ShortHairTheCaesarSidePart");

    companion object {
        private val map = Top.values().associateBy(Top::string)
        private val altMap = Top.values().associateBy(Top::name)
        fun fromString(type: String): Top = map[type] ?: altMap[type] ?: throw IllegalArgumentException()
    }
}

@Serializable
enum class TopAccessory(val string: String) {
    @SerialName("Blank") BLANK("Blank"),
    @SerialName("Kurt") KURT("Kurt"),
    @SerialName("Prescription01") PRESCRIPTION01("Prescription01"),
    @SerialName("Prescription02") PRESCRIPTION02("Prescription02"),
    @SerialName("Round") ROUND("Round"),
    @SerialName("Sunglasses") SUNGLASSES("Sunglasses"),
    @SerialName("Wayfarers") WAYFARERS("Wayfarers");

    companion object {
        private val map = values().associateBy(TopAccessory::string)
        private val altMap = values().associateBy(TopAccessory::name)
        fun fromString(type: String): TopAccessory = map[type] ?: altMap[type] ?: throw IllegalArgumentException()
    }
}

@Serializable
enum class HairColor(val string: String) {
    @SerialName("Auburn") AUBURN("Auburn"),
    @SerialName("Black") BLACK("Black"),
    @SerialName("Blonde") BLONDE("Blonde"),
    @SerialName("BlondeGolden") BLONDE_GOLDEN("BlondeGolden"),
    @SerialName("Brown") BROWN("Brown"),
    @SerialName("BrownDark") BROWN_DARK("BrownDark"),
    @SerialName("PastelPink") PASTEL_PINK("PastelPink"),
    @SerialName("Platinum") PLATINUM("Platinum"),
    @SerialName("Red") RED("Red"),
    @SerialName("SilverGray") SILVER_GRAY("SilverGray");

    companion object {
        private val map = HairColor.values().associateBy(HairColor::string)
        private val altMap = values().associateBy(HairColor::name)
        fun fromString(type: String) = map[type] ?: altMap[type] ?: throw IllegalArgumentException()
    }
}

@Serializable
enum class HatColor(val string: String) {
    @SerialName("Black") BLACK("Black"),
    @SerialName("Blue01") BLUE01("Blue01"),
    @SerialName("Blue02") BLUE02("Blue02"),
    @SerialName("Blue03") BLUE03("Blue03"),
    @SerialName("Gray01") GRAY01("Gray01"),
    @SerialName("Gray02") GRAY02("Gray02"),
    @SerialName("Heather") HEATHER("Heather"),
    @SerialName("PastelBlue") PASTELBLUE("PastelBlue"),
    @SerialName("PastelGreen") PASTELGREEN("PastelGreen"),
    @SerialName("PastelOrange") PASTELORANGE("PastelOrange"),
    @SerialName("PastelRed") PASTELRED("PastelRed"),
    @SerialName("PastelYellow") PASTELYELLOW("PastelYellow"),
    @SerialName("Pink") PINK("Pink"),
    @SerialName("Red") RED("Red"),
    @SerialName("White") WHITE("White");

    companion object {
        private val map = values().associateBy(HatColor::string)
        private val altMap = values().associateBy(HatColor::name)
        fun fromString(type: String) = map[type] ?:  altMap[type] ?:throw IllegalArgumentException()
    }
}

@Serializable
enum class FacialHair(val string: String) {
    @SerialName("Blank") BLANK("Blank"),
    @SerialName("BeardMedium") BEARD_MEDIUM("BeardMedium"),
    @SerialName("BeardLight") BEARD_LIGHT("BeardLight"),
    @SerialName("BeardMajestic") BEARD_MAJESTIC("BeardMajestic"),
    @SerialName("MoustacheFancy") MOUSTACHE_FANCY("MoustacheFancy"),
    @SerialName("MoustacheMagnum") MOUSTACHE_MAGNUM("MoustacheMagnum");

    companion object {
        private val map = values().associateBy(FacialHair::string)
        private val altMap = values().associateBy(FacialHair::name)
        fun fromString(type: String) = map[type] ?:  altMap[type] ?:throw IllegalArgumentException()
    }
}

@Serializable
enum class FacialHairColor(val string: String) {
    @SerialName("Auburn") AUBURN("Auburn"),
    @SerialName("Black") BLACK("Black"),
    @SerialName("Blonde") BLONDE("Blonde"),
    @SerialName("BlondeGolden") BLONDE_GOLDEN("BlondeGolden"),
    @SerialName("Brown") BROWN("Brown"),
    @SerialName("BrownDark") BROWN_DARK("BrownDark"),
    @SerialName("Platinum") PLATINUM("Platinum"),
    @SerialName("Red") RED("Red");

    companion object {
        private val map = FacialHairColor.values().associateBy(FacialHairColor::string)
        private val altMap = values().associateBy(FacialHairColor::name)
        fun fromString(type: String) = map[type] ?:  altMap[type] ?:throw IllegalArgumentException()
    }
}

@Serializable
enum class Clothes(val string: String) {
    @SerialName("BlazerShirt") BLAZER_SHIRT("BlazerShirt"),
    @SerialName("BlazerSweater") BLAZER_SWEATER("BlazerSweater"),
    @SerialName("CollarSweater") COLLAR_SWEATER("CollarSweater"),
    @SerialName("GraphicShirt") GRAPHIC_SHIRT("GraphicShirt"),
    @SerialName("Hoodie") HOODIE("Hoodie"),
    @SerialName("Overall") OVERALL("Overall"),
    @SerialName("ShirtCrewNeck") SHIRT_CREW_NECK("ShirtCrewNeck"),
    @SerialName("ShirtScoopNeck") SHIRT_SCOOP_NECK("ShirtScoopNeck"),
    @SerialName("ShirtVNeck") SHIRT_V_NECK("ShirtVNeck");

    companion object {
        private val map = Clothes.values().associateBy(Clothes::string)
        private val altMap = values().associateBy(Clothes::name)
        fun fromString(type: String): Clothes = map[type] ?:  altMap[type] ?:throw IllegalArgumentException()
    }
}

@Serializable
enum class ColorFabric(val string: String) {
    @SerialName("Black") BLACK("Black"),
    @SerialName("Blue01") BLUE01("Blue01"),
    @SerialName("Blue02") BLUE02("Blue02"),
    @SerialName("Blue03") BLUE03("Blue03"),
    @SerialName("Gray01") GRAY01("Gray01"),
    @SerialName("Gray02") GRAY02("Gray02"),
    @SerialName("Heather") HEATHER("Heather"),
    @SerialName("PastelBlue") PASTEL_BLUE("PastelBlue"),
    @SerialName("PastelGreen") PASTEL_GREEN("PastelGreen"),
    @SerialName("PastelOrange") PASTEL_ORANGE("PastelOrange"),
    @SerialName("PastelRed") PASTEL_RED("PastelRed"),
    @SerialName("PastelYellow") PASTEL_YELLOW("PastelYellow"),
    @SerialName("Pink") PINK("Pink"),
    @SerialName("Red") RED("Red"),
    @SerialName("White") WHITE("White");

    companion object {
        private val map = ColorFabric.values().associateBy(ColorFabric::string)
        private val altMap = values().associateBy(ColorFabric::name)
        fun fromString(type: String): ColorFabric = map[type] ?:  altMap[type] ?:throw IllegalArgumentException()
    }
}

@Serializable
enum class Eyes(val string: String) {
    @SerialName("Close") CLOSE("Close"),
    @SerialName("Cry") CRY("Cry"),
    @SerialName("Default") DEFAULT("Default"),
    @SerialName("Dizzy") DIZZY("Dizzy"),
    @SerialName("EyeRoll") EYE_ROLL("EyeRoll"),
    @SerialName("Happy") HAPPY("Happy"),
    @SerialName("Hearts") HEARTS("Hearts"),
    @SerialName("Side") SIDE("Side"),
    @SerialName("Squint") SQUINT("Squint"),
    @SerialName("Surprised") SURPRISED("Surprised"),
    @SerialName("Wink") WINK("Wink"),
    @SerialName("WinkWacky") WINK_WACKY("WinkWacky");

    companion object {
        private val map = Eyes.values().associateBy(Eyes::string)
        private val altMap = values().associateBy(Eyes::name)
        fun fromString(type: String): Eyes = map[type] ?:  altMap[type] ?:throw IllegalArgumentException()
    }
}

@Serializable
enum class Eyebrows(val string: String) {
    @SerialName("Angry") ANGRY("Angry"),
    @SerialName("AngryNatural") ANGRY_NATURAL("AngryNatural"),
    @SerialName("Default") DEFAULT("Default"),
    @SerialName("DefaultNatural") DEFAULT_NATURAL("DefaultNatural"),
    @SerialName("FlatNatural") FLAT_NATURAL("FlatNatural"),
    @SerialName("FrownNatural") FROWN_NATURAL("FrownNatural"),
    @SerialName("RaisedExcited") RAISED_EXCITED("RaisedExcited"),
    @SerialName("RaisedExcitedNatural") RAISED_EXCITED_NATURAL("RaisedExcitedNatural"),
    @SerialName("SadConcerned") SAD_CONCERNED("SadConcerned"),
    @SerialName("SadConcernedNatural") SAD_CONCERNED_NATURAL("SadConcernedNatural"),
    @SerialName("UnibrowNatural") UNIBROW_NATURAL("UnibrowNatural"),
    @SerialName("UpDown") UP_DOWN("UpDown"),
    @SerialName("UpDownNatural") UP_DOWN_NATURAL("UpDownNatural");

    companion object {
        private val map = Eyebrows.values().associateBy(Eyebrows::string)
        private val altMap = values().associateBy(Eyebrows::name)
        fun fromString(type: String): Eyebrows = map[type] ?:  altMap[type] ?:throw IllegalArgumentException()
    }
}

@Serializable
enum class MouthTypes(val string: String) {
    @SerialName("Concerned") CONCERNED("Concerned"),
    @SerialName("Default") DEFAULT("Default"),
    @SerialName("Disbelief") DISBELIEF("Disbelief"),
    @SerialName("Eating") EATING("Eating"),
    @SerialName("Grimace") GRIMACE("Grimace"),
    @SerialName("Sad") SAD("Sad"),
    @SerialName("ScreamOpen") SCREAM_OPEN("ScreamOpen"),
    @SerialName("Serious") SERIOUS("Serious"),
    @SerialName("Smile") SMILE("Smile"),
    @SerialName("Tongue") TONGUE("Tongue"),
    @SerialName("Twinkle") TWINKLE("Twinkle"),
    @SerialName("Vomit") VOMIT("Vomit");

    companion object {
        private val map = MouthTypes.values().associateBy(MouthTypes::string)
        private val altMap = values().associateBy(MouthTypes::name)
        fun fromString(type: String): MouthTypes = map[type] ?:  altMap[type] ?:throw IllegalArgumentException()
    }
}

@Serializable
enum class SkinColors(val string: String) {
    @SerialName("Tanned") TANNED("Tanned"),
    @SerialName("Yellow") YELLOW("Yellow"),
    @SerialName("Pale") PALE("Pale"),
    @SerialName("Light") LIGHT("Light"),
    @SerialName("Brown") BROWN("Brown"),
    @SerialName("DarkBrown") DARK_BROWN("DarkBrown"),
    @SerialName("Black") BLACK("Black");

    companion object {
        private val map = SkinColors.values().associateBy(SkinColors::string)
        private val altMap = values().associateBy(SkinColors::name)
        fun fromString(type: String): SkinColors = map[type] ?:  altMap[type] ?:throw IllegalArgumentException()
    }
}

@Serializable
enum class ClothesGraphic(val string: String) {
    @SerialName("Bat") BAT("Bat"),
    @SerialName("Cumbia") CUMBIA("Cumbia"),
    @SerialName("Deer") DEER("Deer"),
    @SerialName("Diamond") DIAMOND("Diamond"),
    @SerialName("Hola") HOLA("Hola"),
    @SerialName("Pizza") PIZZA("Pizza"),
    @SerialName("Resist") RESIST("Resist"),
    @SerialName("Selena") SELENA("Selena"),
    @SerialName("Bear") BEAR("Bear"),
    @SerialName("SkullOutline") SKULL_OUTLINE("SkullOutline"),
    @SerialName("Skull") SKULL("Skull"),
    @SerialName("Espie") ESPIE("Espie"),
    @SerialName("EScienceLogo") ESCIENCELOGO("EScienceLogo"),
    @SerialName("Teeth") TEETH("Teeth");

    companion object {
        private val map = ClothesGraphic.values().associateBy(ClothesGraphic::string)
        private val altMap = values().associateBy(ClothesGraphic::name)
        fun fromString(type: String): ClothesGraphic = map[type] ?:  altMap[type] ?:throw IllegalArgumentException()
    }
}
