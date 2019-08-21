package dk.sdu.cloud.avatar.api

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
    val clothesGraphic: ClothesGraphic
)

enum class Top(val string: String) {
    NO_HAIR("NoHair"),
    EYEPATCH("Eyepatch"), // Excludes accessories
    HAT("Hat"),
    HIJAB("Hijab"),
    TURBAN("Turban"),
    WINTER_HAT1("WinterHat1"),
    WINTER_HAT2("WinterHat2"),
    WINTER_HAT3("WinterHat3"),
    WINTER_HAT4("WinterHat4"),
    LONG_HAIR_BIG_HAIR("LongHairBigHair"),
    LONG_HAIR_BOB("LongHairBob"),
    LONG_HAIR_BUN("LongHairBun"),
    LONG_HAIR_CURLY("LongHairCurly"),
    LONG_HAIR_CURVY("LongHairCurvy"),
    LONG_HAIR_DREADS("LongHairDreads"),
    LONG_HAIR_FRIDA("LongHairFrida"),
    LONG_HAIR_FRO("LongHairFro"),
    LONG_HAIR_FRO_BAND("LongHairFroBand"),
    LONG_HAIR_NOT_TOO_LONG("LongHairNotTooLong"),
    LONG_HAIR_SHAVED_SIDES("LongHairShavedSides"),
    LONG_HAIR_MIA_WALLACE("LongHairMiaWallace"),
    LONG_HAIR_STRAIGHT("LongHairStraight"),
    LONG_HAIR_STRAIGHT2("LongHairStraight2"),
    LONG_HAIR_STRAIGHT_STRAND("LongHairStraightStrand"),
    SHORT_HAIR_DREADS01("ShortHairDreads01"),
    SHORT_HAIR_DREADS02("ShortHairDreads02"),
    SHORT_HAIR_FRIZZLE("ShortHairFrizzle"),
    SHORT_HAIR_SHAGGY_MULLET("ShortHairShaggyMullet"),
    SHORT_HAIR_SHORT_CURLY("ShortHairShortCurly"),
    SHORT_HAIR_SHORT_FLAT("ShortHairShortFlat"),
    SHORT_HAIR_SHORT_ROUND("ShortHairShortRound"),
    SHORT_HAIR_SHORT_WAVED("ShortHairShortWaved"),
    SHORT_HAIR_SIDES("ShortHairSides"),
    SHORT_HAIR_THE_CAESAR("ShortHairTheCaesar"),
    SHORT_HAIR_THE_CAESAR_SIDE_PART("ShortHairTheCaesarSidePart");

    companion object {
        private val map = Top.values().associateBy(Top::string)
        fun fromString(type: String): Top = map[type] ?: throw IllegalArgumentException()
    }
}

enum class TopAccessory(val string: String) {
    BLANK("Blank"),
    KURT("Kurt"),
    PRESCRIPTION01("Prescription01"),
    PRESCRIPTION02("Prescription02"),
    ROUND("Round"),
    SUNGLASSES("Sunglasses"),
    WAYFARERS("Wayfarers");

    companion object {
        private val map = TopAccessory.values().associateBy(TopAccessory::string)
        fun fromString(type: String): TopAccessory = map[type] ?: throw IllegalArgumentException()
    }
}

enum class HairColor(val string: String) {
    AUBURN("Auburn"),
    BLACK("Black"),
    BLONDE("Blonde"),
    BLONDE_GOLDEN("BlondeGolden"),
    BROWN("Brown"),
    BROWN_DARK("BrownDark"),
    PASTEL_PINK("PastelPink"),
    PLATINUM("Platinum"),
    RED("Red"),
    SILVER_GRAY("SilverGray");

    companion object {
        private val map = HairColor.values().associateBy(HairColor::string)
        fun fromString(type: String): HairColor = map[type] ?: throw IllegalArgumentException()
    }
}

enum class FacialHair(val string: String) {
    BLANK("Blank"),
    BEARD_MEDIUM("BeardMedium"),
    BEARD_LIGHT("BeardLight"),
    BEARD_MAJESTIC("BeardMajestic"),
    MOUSTACHE_FANCY("MoustacheFancy"),
    MOUSTACHE_MAGNUM("MoustacheMagnum");

    companion object {
        private val map = FacialHair.values().associateBy(FacialHair::string)
        fun fromString(type: String): FacialHair = map[type] ?: throw IllegalArgumentException()
    }
}

enum class FacialHairColor(val string: String) {
    AUBURN("Auburn"),
    BLACK("Black"),
    BLONDE("Blonde"),
    BLONDE_GOLDEN("BlondeGolden"),
    BROWN("Brown"),
    BROWN_DARK("BrownDark"),
    PLATINUM("Platinum"),
    RED("Red");

    companion object {
        private val map = FacialHairColor.values().associateBy(FacialHairColor::string)
        fun fromString(type: String): FacialHairColor = map[type] ?: throw IllegalArgumentException()
    }
}

enum class Clothes(val string: String) {
    BLAZER_SHIRT("BlazerShirt"),
    BLAZER_SWEATER("BlazerSweater"),
    COLLAR_SWEATER("CollarSweater"),
    GRAPHIC_SHIRT("GraphicShirt"),
    HOODIE("Hoodie"),
    OVERALL("Overall"),
    SHIRT_CREW_NECK("ShirtCrewNeck"),
    SHIRT_SCOOP_NECK("ShirtScoopNeck"),
    SHIRT_V_NECK("ShirtVNeck");

    companion object {
        private val map = Clothes.values().associateBy(Clothes::string)
        fun fromString(type: String): Clothes = map[type] ?: throw IllegalArgumentException()
    }
}

enum class ColorFabric(val string: String) {
    BLACK("Black"),
    BLUE01("Blue01"),
    BLUE02("Blue02"),
    BLUE03("Blue03"),
    GRAY01("Gray01"),
    GRAY02("Gray02"),
    HEATHER("Heather"),
    PASTEL_BLUE("PastelBlue"),
    PASTEL_GREEN("PastelGreen"),
    PASTEL_ORANGE("PastelOrange"),
    PASTEL_RED("PastelRed"),
    PASTEL_YELLOW("PastelYellow"),
    PINK("Pink"),
    RED("Red"),
    WHITE("White");

    companion object {
        private val map = ColorFabric.values().associateBy(ColorFabric::string)
        fun fromString(type: String): ColorFabric = map[type] ?: throw IllegalArgumentException()
    }
}

enum class Eyes(val string: String) {
    CLOSE("Close"),
    CRY("Cry"),
    DEFAULT("Default"),
    DIZZY("Dizzy"),
    EYE_ROLL("EyeRoll"),
    HAPPY("Happy"),
    HEARTS("Hearts"),
    SIDE("Side"),
    SQUINT("Squint"),
    SURPRISED("Surprised"),
    WINK("Wink"),
    WINK_WACKY("WinkWacky");

    companion object {
        private val map = Eyes.values().associateBy(Eyes::string)
        fun fromString(type: String): Eyes = map[type] ?: throw IllegalArgumentException()
    }
}

enum class Eyebrows(val string: String) {
    ANGRY("Angry"),
    ANGRY_NATURAL("AngryNatural"),
    DEFAULT("Default"),
    DEFAULT_NATURAL("DefaultNatural"),
    FLAT_NATURAL("FlatNatural"),
    RAISED_EXCITED("RaisedExcited"),
    RAISED_EXCITED_NATURAL("RaisedExcitedNatural"),
    SAD_CONCERNED("SadConcerned"),
    SAD_CONCERNED_NATURAL("SadConcernedNatural"),
    UNIBROW_NATURAL("UnibrowNatural"),
    UP_DOWN("UpDown"),
    UP_DOWN_NATURAL("UpDownNatural");

    companion object {
        private val map = Eyebrows.values().associateBy(Eyebrows::string)
        fun fromString(type: String): Eyebrows = map[type] ?: throw IllegalArgumentException()
    }
}

enum class MouthTypes(val string: String) {
    CONCERNED("Concerned"),
    DEFAULT("Default"),
    DISBELIEF("Disbelief"),
    EATING("Eating"),
    GRIMACE("Grimace"),
    SAD("Sad"),
    SCREAM_OPEN("ScreamOpen"),
    SERIOUS("Serious"),
    SMILE("Smile"),
    TONGUE("Tongue"),
    TWINKLE("Twinkle"),
    VOMIT("Vomit");

    companion object {
        private val map = MouthTypes.values().associateBy(MouthTypes::string)
        fun fromString(type: String): MouthTypes = map[type] ?: throw IllegalArgumentException()
    }
}

enum class SkinColors(val string: String) {
    TANNED("Tanned"),
    YELLOW("Yellow"),
    PALE("Pale"),
    LIGHT("Light"),
    BROWN("Brown"),
    DARK_BROWN("Dark_Brown"),
    BLACK("Black");

    companion object {
        private val map = SkinColors.values().associateBy(SkinColors::string)
        fun fromString(type: String): SkinColors = map[type] ?: throw IllegalArgumentException()
    }
}

enum class ClothesGraphic(val string: String) {
    BAT("Bat"),
    CUMBIA("Cumbia"),
    DEER("Deer"),
    DIAMOND("Diamond"),
    HOLA("Hola"),
    PIZZA("Pizza"),
    RESIST("Resist"),
    SELENA("Selena"),
    BEAR("Bear"),
    SKULL_OUTLINE("SkullOutline"),
    SKULL("Skull"),
    ESPIE("Espie"),
    ESCIENCELOGO("eScienceLogo"),
    TEETH("TEETH");

    companion object {
        private val map = ClothesGraphic.values().associateBy(ClothesGraphic::string)
        fun fromString(type: String): ClothesGraphic = map[type] ?: throw IllegalArgumentException()
    }
}
