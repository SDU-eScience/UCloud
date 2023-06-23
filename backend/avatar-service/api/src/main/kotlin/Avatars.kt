// GENERATED CODE - DO NOT MODIFY - See Avatars.msg
// GENERATED CODE - DO NOT MODIFY - See Avatars.msg
// GENERATED CODE - DO NOT MODIFY - See Avatars.msg

package dk.sdu.cloud.avatar.api

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import dk.sdu.cloud.messages.*

enum class Top(val encoded: Int, val serialName: String) {
    NO_HAIR(1, "NoHair"),
    EYEPATCH(2, "Eyepatch"),
    HAT(3, "Hat"),
    HIJAB(4, "Hijab"),
    TURBAN(5, "Turban"),
    WINTER_HAT1(6, "WinterHat1"),
    WINTER_HAT2(7, "WinterHat2"),
    WINTER_HAT3(8, "WinterHat3"),
    WINTER_HAT4(9, "WinterHat4"),
    LONG_HAIR_BIG_HAIR(10, "LongHairBigHair"),
    LONG_HAIR_BOB(11, "LongHairBob"),
    LONG_HAIR_BUN(12, "LongHairBun"),
    LONG_HAIR_CURLY(13, "LongHairCurly"),
    LONG_HAIR_CURVY(14, "LongHairCurvy"),
    LONG_HAIR_DREADS(15, "LongHairDreads"),
    LONG_HAIR_FRIDA(16, "LongHairFrida"),
    LONG_HAIR_FRO(17, "LongHairFro"),
    LONG_HAIR_FRO_BAND(18, "LongHairFroBand"),
    LONG_HAIR_NOT_TOO_LONG(19, "LongHairNotTooLong"),
    LONG_HAIR_SHAVED_SIDES(20, "LongHairShavedSides"),
    LONG_HAIR_MIA_WALLACE(21, "LongHairMiaWallace"),
    LONG_HAIR_STRAIGHT(22, "LongHairStraight"),
    LONG_HAIR_STRAIGHT2(23, "LongHairStraight2"),
    LONG_HAIR_STRAIGHT_STRAND(24, "LongHairStraightStrand"),
    SHORT_HAIR_DREADS01(25, "ShortHairDreads01"),
    SHORT_HAIR_DREADS02(26, "ShortHairDreads02"),
    SHORT_HAIR_FRIZZLE(27, "ShortHairFrizzle"),
    SHORT_HAIR_SHAGGY_MULLET(28, "ShortHairShaggyMullet"),
    SHORT_HAIR_SHORT_CURLY(29, "ShortHairShortCurly"),
    SHORT_HAIR_SHORT_FLAT(30, "ShortHairShortFlat"),
    SHORT_HAIR_SHORT_ROUND(31, "ShortHairShortRound"),
    SHORT_HAIR_SHORT_WAVED(32, "ShortHairShortWaved"),
    SHORT_HAIR_SIDES(33, "ShortHairSides"),
    SHORT_HAIR_THE_CAESAR(34, "ShortHairTheCaesar"),
    SHORT_HAIR_THE_CAESAR_SIDE_PART(35, "ShortHairTheCaesarSidePart"),
    ;companion object {
        fun fromEncoded(encoded: Int): Top {
            return values().find { it.encoded == encoded } ?: error("Unknown enum encoding: $encoded")
        }

        fun fromSerialName(name: String): Top {
            return values().find { it.serialName == name } ?: error("Unknown enum encoding: $name")
        }
    }
}

enum class TopAccessory(val encoded: Int, val serialName: String) {
    BLANK(1, "Blank"),
    KURT(2, "Kurt"),
    PRESCRIPTION01(3, "Prescription01"),
    PRESCRIPTION02(4, "Prescription02"),
    ROUND(5, "Round"),
    SUNGLASSES(6, "Sunglasses"),
    WAYFARERS(7, "Wayfarers"),
    ;companion object {
        fun fromEncoded(encoded: Int): TopAccessory {
            return values().find { it.encoded == encoded } ?: error("Unknown enum encoding: $encoded")
        }

        fun fromSerialName(name: String): TopAccessory {
            return values().find { it.serialName == name } ?: error("Unknown enum encoding: $name")
        }
    }
}

enum class HairColor(val encoded: Int, val serialName: String) {
    AUBURN(1, "Auburn"),
    BLACK(2, "Black"),
    BLONDE(3, "Blonde"),
    BLONDE_GOLDEN(4, "BlondeGolden"),
    BROWN(5, "Brown"),
    BROWN_DARK(6, "BrownDark"),
    PASTEL_PINK(7, "PastelPink"),
    PLATINUM(8, "Platinum"),
    RED(9, "Red"),
    SILVER_GRAY(10, "SilverGray"),
    ;companion object {
        fun fromEncoded(encoded: Int): HairColor {
            return values().find { it.encoded == encoded } ?: error("Unknown enum encoding: $encoded")
        }

        fun fromSerialName(name: String): HairColor {
            return values().find { it.serialName == name } ?: error("Unknown enum encoding: $name")
        }
    }
}

enum class HatColor(val encoded: Int, val serialName: String) {
    BLACK(1, "Black"),
    BLUE01(2, "Blue01"),
    BLUE02(3, "Blue02"),
    BLUE03(4, "Blue03"),
    GRAY01(5, "Gray01"),
    GRAY02(6, "Gray02"),
    HEATHER(7, "Heather"),
    PASTELBLUE(8, "PastelBlue"),
    PASTELGREEN(9, "PastelGreen"),
    PASTELORANGE(10, "PastelOrange"),
    PASTELRED(11, "PastelRed"),
    PASTELYELLOW(12, "PastelYellow"),
    PINK(13, "Pink"),
    RED(14, "Red"),
    WHITE(15, "White"),
    ;companion object {
        fun fromEncoded(encoded: Int): HatColor {
            return values().find { it.encoded == encoded } ?: error("Unknown enum encoding: $encoded")
        }

        fun fromSerialName(name: String): HatColor {
            return values().find { it.serialName == name } ?: error("Unknown enum encoding: $name")
        }
    }
}

enum class FacialHair(val encoded: Int, val serialName: String) {
    BLANK(1, "Blank"),
    BEARD_MEDIUM(2, "BeardMedium"),
    BEARD_LIGHT(3, "BeardLight"),
    BEARD_MAJESTIC(4, "BeardMajestic"),
    MOUSTACHE_FANCY(5, "MoustacheFancy"),
    MOUSTACHE_MAGNUM(6, "MoustacheMagnum"),
    ;companion object {
        fun fromEncoded(encoded: Int): FacialHair {
            return values().find { it.encoded == encoded } ?: error("Unknown enum encoding: $encoded")
        }

        fun fromSerialName(name: String): FacialHair {
            return values().find { it.serialName == name } ?: error("Unknown enum encoding: $name")
        }
    }
}

enum class FacialHairColor(val encoded: Int, val serialName: String) {
    AUBURN(1, "Auburn"),
    BLACK(2, "Black"),
    BLONDE(3, "Blonde"),
    BLONDE_GOLDEN(4, "BlondeGolden"),
    BROWN(5, "Brown"),
    BROWN_DARK(6, "BrownDark"),
    PLATINUM(7, "Platinum"),
    RED(8, "Red"),
    ;companion object {
        fun fromEncoded(encoded: Int): FacialHairColor {
            return values().find { it.encoded == encoded } ?: error("Unknown enum encoding: $encoded")
        }

        fun fromSerialName(name: String): FacialHairColor {
            return values().find { it.serialName == name } ?: error("Unknown enum encoding: $name")
        }
    }
}

enum class Clothes(val encoded: Int, val serialName: String) {
    BLAZER_SHIRT(1, "BlazerShirt"),
    BLAZER_SWEATER(2, "BlazerSweater"),
    COLLAR_SWEATER(3, "CollarSweater"),
    GRAPHIC_SHIRT(4, "GraphicShirt"),
    HOODIE(5, "Hoodie"),
    OVERALL(6, "Overall"),
    SHIRT_CREW_NECK(7, "ShirtCrewNeck"),
    SHIRT_SCOOP_NECK(8, "ShirtScoopNeck"),
    SHIRT_V_NECK(9, "ShirtVNeck"),
    ;companion object {
        fun fromEncoded(encoded: Int): Clothes {
            return values().find { it.encoded == encoded } ?: error("Unknown enum encoding: $encoded")
        }

        fun fromSerialName(name: String): Clothes {
            return values().find { it.serialName == name } ?: error("Unknown enum encoding: $name")
        }
    }
}

enum class ColorFabric(val encoded: Int, val serialName: String) {
    BLACK(1, "Black"),
    BLUE01(2, "Blue01"),
    BLUE02(3, "Blue02"),
    BLUE03(4, "Blue03"),
    GRAY01(5, "Gray01"),
    GRAY02(6, "Gray02"),
    HEATHER(7, "Heather"),
    PASTEL_BLUE(8, "PastelBlue"),
    PASTEL_GREEN(9, "PastelGreen"),
    PASTEL_ORANGE(10, "PastelOrange"),
    PASTEL_RED(11, "PastelRed"),
    PASTEL_YELLOW(12, "PastelYellow"),
    PINK(13, "Pink"),
    RED(14, "Red"),
    WHITE(15, "White"),
    ;companion object {
        fun fromEncoded(encoded: Int): ColorFabric {
            return values().find { it.encoded == encoded } ?: error("Unknown enum encoding: $encoded")
        }

        fun fromSerialName(name: String): ColorFabric {
            return values().find { it.serialName == name } ?: error("Unknown enum encoding: $name")
        }
    }
}

enum class Eyes(val encoded: Int, val serialName: String) {
    CLOSE(1, "Close"),
    CRY(2, "Cry"),
    DEFAULT(3, "Default"),
    DIZZY(4, "Dizzy"),
    EYE_ROLL(5, "EyeRoll"),
    HAPPY(6, "Happy"),
    HEARTS(7, "Hearts"),
    SIDE(8, "Side"),
    SQUINT(9, "Squint"),
    SURPRISED(10, "Surprised"),
    WINK(11, "Wink"),
    WINK_WACKY(12, "WinkWacky"),
    ;companion object {
        fun fromEncoded(encoded: Int): Eyes {
            return values().find { it.encoded == encoded } ?: error("Unknown enum encoding: $encoded")
        }

        fun fromSerialName(name: String): Eyes {
            return values().find { it.serialName == name } ?: error("Unknown enum encoding: $name")
        }
    }
}

enum class Eyebrows(val encoded: Int, val serialName: String) {
    ANGRY(1, "Angry"),
    ANGRY_NATURAL(2, "AngryNatural"),
    DEFAULT(3, "Default"),
    DEFAULT_NATURAL(4, "DefaultNatural"),
    FLAT_NATURAL(5, "FlatNatural"),
    FROWN_NATURAL(6, "FrownNatural"),
    RAISED_EXCITED(7, "RaisedExcited"),
    RAISED_EXCITED_NATURAL(8, "RaisedExcitedNatural"),
    SAD_CONCERNED(9, "SadConcerned"),
    SAD_CONCERNED_NATURAL(10, "SadConcernedNatural"),
    UNIBROW_NATURAL(11, "UnibrowNatural"),
    UP_DOWN(12, "UpDown"),
    UP_DOWN_NATURAL(13, "UpDownNatural"),
    ;companion object {
        fun fromEncoded(encoded: Int): Eyebrows {
            return values().find { it.encoded == encoded } ?: error("Unknown enum encoding: $encoded")
        }

        fun fromSerialName(name: String): Eyebrows {
            return values().find { it.serialName == name } ?: error("Unknown enum encoding: $name")
        }
    }
}

enum class MouthTypes(val encoded: Int, val serialName: String) {
    CONCERNED(1, "Concerned"),
    DEFAULT(2, "Default"),
    DISBELIEF(3, "Disbelief"),
    EATING(4, "Eating"),
    GRIMACE(5, "Grimace"),
    SAD(6, "Sad"),
    SCREAM_OPEN(7, "ScreamOpen"),
    SERIOUS(8, "Serious"),
    SMILE(9, "Smile"),
    TONGUE(10, "Tongue"),
    TWINKLE(11, "Twinkle"),
    VOMIT(12, "Vomit"),
    ;companion object {
        fun fromEncoded(encoded: Int): MouthTypes {
            return values().find { it.encoded == encoded } ?: error("Unknown enum encoding: $encoded")
        }

        fun fromSerialName(name: String): MouthTypes {
            return values().find { it.serialName == name } ?: error("Unknown enum encoding: $name")
        }
    }
}

enum class SkinColors(val encoded: Int, val serialName: String) {
    TANNED(1, "Tanned"),
    YELLOW(2, "Yellow"),
    PALE(3, "Pale"),
    LIGHT(4, "Light"),
    BROWN(5, "Brown"),
    DARK_BROWN(6, "DarkBrown"),
    BLACK(7, "Black"),
    ;companion object {
        fun fromEncoded(encoded: Int): SkinColors {
            return values().find { it.encoded == encoded } ?: error("Unknown enum encoding: $encoded")
        }

        fun fromSerialName(name: String): SkinColors {
            return values().find { it.serialName == name } ?: error("Unknown enum encoding: $name")
        }
    }
}

enum class ClothesGraphic(val encoded: Int, val serialName: String) {
    BAT(1, "Bat"),
    CUMBIA(2, "Cumbia"),
    DEER(3, "Deer"),
    DIAMOND(4, "Diamond"),
    HOLA(5, "Hola"),
    PIZZA(6, "Pizza"),
    RESIST(7, "Resist"),
    SELENA(8, "Selena"),
    BEAR(9, "Bear"),
    SKULL_OUTLINE(10, "SkullOutline"),
    SKULL(11, "Skull"),
    ESPIE(12, "Espie"),
    ESCIENCELOGO(13, "EScienceLogo"),
    TEETH(14, "Teeth"),
    ;companion object {
        fun fromEncoded(encoded: Int): ClothesGraphic {
            return values().find { it.encoded == encoded } ?: error("Unknown enum encoding: $encoded")
        }

        fun fromSerialName(name: String): ClothesGraphic {
            return values().find { it.serialName == name } ?: error("Unknown enum encoding: $name")
        }
    }
}

@JvmInline
value class Avatar(override val buffer: BufferAndOffset) : BinaryType {
    var top: Top
        inline get() = buffer.data.getShort(0 + buffer.offset).let { Top.fromEncoded(it.toInt()) }
        inline set (value) { buffer.data.putShort(0 + buffer.offset, value.encoded.toShort()) }

    var topAccessory: TopAccessory
        inline get() = buffer.data.getShort(2 + buffer.offset).let { TopAccessory.fromEncoded(it.toInt()) }
        inline set (value) { buffer.data.putShort(2 + buffer.offset, value.encoded.toShort()) }

    var hairColor: HairColor
        inline get() = buffer.data.getShort(4 + buffer.offset).let { HairColor.fromEncoded(it.toInt()) }
        inline set (value) { buffer.data.putShort(4 + buffer.offset, value.encoded.toShort()) }

    var facialHair: FacialHair
        inline get() = buffer.data.getShort(6 + buffer.offset).let { FacialHair.fromEncoded(it.toInt()) }
        inline set (value) { buffer.data.putShort(6 + buffer.offset, value.encoded.toShort()) }

    var facialHairColor: FacialHairColor
        inline get() = buffer.data.getShort(8 + buffer.offset).let { FacialHairColor.fromEncoded(it.toInt()) }
        inline set (value) { buffer.data.putShort(8 + buffer.offset, value.encoded.toShort()) }

    var clothes: Clothes
        inline get() = buffer.data.getShort(10 + buffer.offset).let { Clothes.fromEncoded(it.toInt()) }
        inline set (value) { buffer.data.putShort(10 + buffer.offset, value.encoded.toShort()) }

    var colorFabric: ColorFabric
        inline get() = buffer.data.getShort(12 + buffer.offset).let { ColorFabric.fromEncoded(it.toInt()) }
        inline set (value) { buffer.data.putShort(12 + buffer.offset, value.encoded.toShort()) }

    var eyes: Eyes
        inline get() = buffer.data.getShort(14 + buffer.offset).let { Eyes.fromEncoded(it.toInt()) }
        inline set (value) { buffer.data.putShort(14 + buffer.offset, value.encoded.toShort()) }

    var eyebrows: Eyebrows
        inline get() = buffer.data.getShort(16 + buffer.offset).let { Eyebrows.fromEncoded(it.toInt()) }
        inline set (value) { buffer.data.putShort(16 + buffer.offset, value.encoded.toShort()) }

    var mouthTypes: MouthTypes
        inline get() = buffer.data.getShort(18 + buffer.offset).let { MouthTypes.fromEncoded(it.toInt()) }
        inline set (value) { buffer.data.putShort(18 + buffer.offset, value.encoded.toShort()) }

    var skinColors: SkinColors
        inline get() = buffer.data.getShort(20 + buffer.offset).let { SkinColors.fromEncoded(it.toInt()) }
        inline set (value) { buffer.data.putShort(20 + buffer.offset, value.encoded.toShort()) }

    var clothesGraphic: ClothesGraphic
        inline get() = buffer.data.getShort(22 + buffer.offset).let { ClothesGraphic.fromEncoded(it.toInt()) }
        inline set (value) { buffer.data.putShort(22 + buffer.offset, value.encoded.toShort()) }

    var hatColor: HatColor
        inline get() = buffer.data.getShort(24 + buffer.offset).let { HatColor.fromEncoded(it.toInt()) }
        inline set (value) { buffer.data.putShort(24 + buffer.offset, value.encoded.toShort()) }

    override fun encodeToJson(): JsonElement = JsonObject(mapOf(
        "top" to (top.let { JsonPrimitive(it.serialName) }),
        "topAccessory" to (topAccessory.let { JsonPrimitive(it.serialName) }),
        "hairColor" to (hairColor.let { JsonPrimitive(it.serialName) }),
        "facialHair" to (facialHair.let { JsonPrimitive(it.serialName) }),
        "facialHairColor" to (facialHairColor.let { JsonPrimitive(it.serialName) }),
        "clothes" to (clothes.let { JsonPrimitive(it.serialName) }),
        "colorFabric" to (colorFabric.let { JsonPrimitive(it.serialName) }),
        "eyes" to (eyes.let { JsonPrimitive(it.serialName) }),
        "eyebrows" to (eyebrows.let { JsonPrimitive(it.serialName) }),
        "mouthTypes" to (mouthTypes.let { JsonPrimitive(it.serialName) }),
        "skinColors" to (skinColors.let { JsonPrimitive(it.serialName) }),
        "clothesGraphic" to (clothesGraphic.let { JsonPrimitive(it.serialName) }),
        "hatColor" to (hatColor.let { JsonPrimitive(it.serialName) }),
    ))

    companion object : BinaryTypeCompanion<Avatar> {
        override val size = 26
        private val mySerializer = BinaryTypeSerializer(this)
        fun serializer() = mySerializer
        override fun create(buffer: BufferAndOffset) = Avatar(buffer)
        override fun decodeFromJson(allocator: BinaryAllocator, json: JsonElement): Avatar {
            if (json !is JsonObject) error("Avatar must be decoded from an object")
            val top = run {
                val element = json["top"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'top' to be a primitive")
                    element.content.let { Top.fromSerialName(it) }
                }
            } ?: error("Missing required property: top in Avatar")
            val topAccessory = run {
                val element = json["topAccessory"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'topAccessory' to be a primitive")
                    element.content.let { TopAccessory.fromSerialName(it) }
                }
            } ?: error("Missing required property: topAccessory in Avatar")
            val hairColor = run {
                val element = json["hairColor"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'hairColor' to be a primitive")
                    element.content.let { HairColor.fromSerialName(it) }
                }
            } ?: error("Missing required property: hairColor in Avatar")
            val facialHair = run {
                val element = json["facialHair"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'facialHair' to be a primitive")
                    element.content.let { FacialHair.fromSerialName(it) }
                }
            } ?: error("Missing required property: facialHair in Avatar")
            val facialHairColor = run {
                val element = json["facialHairColor"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'facialHairColor' to be a primitive")
                    element.content.let { FacialHairColor.fromSerialName(it) }
                }
            } ?: error("Missing required property: facialHairColor in Avatar")
            val clothes = run {
                val element = json["clothes"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'clothes' to be a primitive")
                    element.content.let { Clothes.fromSerialName(it) }
                }
            } ?: error("Missing required property: clothes in Avatar")
            val colorFabric = run {
                val element = json["colorFabric"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'colorFabric' to be a primitive")
                    element.content.let { ColorFabric.fromSerialName(it) }
                }
            } ?: error("Missing required property: colorFabric in Avatar")
            val eyes = run {
                val element = json["eyes"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'eyes' to be a primitive")
                    element.content.let { Eyes.fromSerialName(it) }
                }
            } ?: error("Missing required property: eyes in Avatar")
            val eyebrows = run {
                val element = json["eyebrows"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'eyebrows' to be a primitive")
                    element.content.let { Eyebrows.fromSerialName(it) }
                }
            } ?: error("Missing required property: eyebrows in Avatar")
            val mouthTypes = run {
                val element = json["mouthTypes"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'mouthTypes' to be a primitive")
                    element.content.let { MouthTypes.fromSerialName(it) }
                }
            } ?: error("Missing required property: mouthTypes in Avatar")
            val skinColors = run {
                val element = json["skinColors"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'skinColors' to be a primitive")
                    element.content.let { SkinColors.fromSerialName(it) }
                }
            } ?: error("Missing required property: skinColors in Avatar")
            val clothesGraphic = run {
                val element = json["clothesGraphic"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'clothesGraphic' to be a primitive")
                    element.content.let { ClothesGraphic.fromSerialName(it) }
                }
            } ?: error("Missing required property: clothesGraphic in Avatar")
            val hatColor = run {
                val element = json["hatColor"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'hatColor' to be a primitive")
                    element.content.let { HatColor.fromSerialName(it) }
                }
            } ?: error("Missing required property: hatColor in Avatar")
            return allocator.Avatar(
                top = top,
                topAccessory = topAccessory,
                hairColor = hairColor,
                facialHair = facialHair,
                facialHairColor = facialHairColor,
                clothes = clothes,
                colorFabric = colorFabric,
                eyes = eyes,
                eyebrows = eyebrows,
                mouthTypes = mouthTypes,
                skinColors = skinColors,
                clothesGraphic = clothesGraphic,
                hatColor = hatColor,
            )
        }
    }
}


fun BinaryAllocator.Avatar(
    top: Top,
    topAccessory: TopAccessory,
    hairColor: HairColor,
    facialHair: FacialHair,
    facialHairColor: FacialHairColor,
    clothes: Clothes,
    colorFabric: ColorFabric,
    eyes: Eyes,
    eyebrows: Eyebrows,
    mouthTypes: MouthTypes,
    skinColors: SkinColors,
    clothesGraphic: ClothesGraphic,
    hatColor: HatColor,
): Avatar {
    val result = this.allocate(Avatar)
    result.top = top
    result.topAccessory = topAccessory
    result.hairColor = hairColor
    result.facialHair = facialHair
    result.facialHairColor = facialHairColor
    result.clothes = clothes
    result.colorFabric = colorFabric
    result.eyes = eyes
    result.eyebrows = eyebrows
    result.mouthTypes = mouthTypes
    result.skinColors = skinColors
    result.clothesGraphic = clothesGraphic
    result.hatColor = hatColor
    return result
}

@JvmInline
value class FindBulkRequest(override val buffer: BufferAndOffset) : BinaryType {
    var usernames: BinaryTypeList<Text>
        inline get() {
            val offset = buffer.data.getInt(0 + buffer.offset)
            return (if (offset == 0) null else BinaryTypeList(Text, buffer.copy(offset = offset))) ?: error("Missing property usernames")
        }
        inline set(value) { buffer.data.putInt(0 + buffer.offset, value.buffer.offset) }

    override fun encodeToJson(): JsonElement = JsonObject(mapOf(
        "usernames" to (usernames.let { it.encodeToJson() }),
    ))

    companion object : BinaryTypeCompanion<FindBulkRequest> {
        override val size = 4
        private val mySerializer = BinaryTypeSerializer(this)
        fun serializer() = mySerializer
        override fun create(buffer: BufferAndOffset) = FindBulkRequest(buffer)
        override fun decodeFromJson(allocator: BinaryAllocator, json: JsonElement): FindBulkRequest {
            if (json !is JsonObject) error("FindBulkRequest must be decoded from an object")
            val usernames = run {
                val element = json["usernames"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    BinaryTypeList.decodeFromJson(Text, allocator, element)
                }
            } ?: error("Missing required property: usernames in FindBulkRequest")
            return allocator.FindBulkRequest(
                usernames = usernames,
            )
        }
    }
}


fun BinaryAllocator.FindBulkRequest(
    usernames: BinaryTypeList<Text>,
): FindBulkRequest {
    val result = this.allocate(FindBulkRequest)
    result.usernames = usernames
    return result
}

@JvmInline
value class FindBulkResponse(override val buffer: BufferAndOffset) : BinaryType {
    var avatars: BinaryTypeDictionary<Avatar>
        inline get() {
            val offset = buffer.data.getInt(0 + buffer.offset)
            return (if (offset == 0) null else BinaryTypeDictionary(Avatar, buffer.copy(offset = offset))) ?: error("Missing property avatars")
        }
        inline set(value) { buffer.data.putInt(0 + buffer.offset, value.buffer.offset) }

    override fun encodeToJson(): JsonElement = JsonObject(mapOf(
        "avatars" to (avatars.let { it.encodeToJson() }),
    ))

    companion object : BinaryTypeCompanion<FindBulkResponse> {
        override val size = 4
        private val mySerializer = BinaryTypeSerializer(this)
        fun serializer() = mySerializer
        override fun create(buffer: BufferAndOffset) = FindBulkResponse(buffer)
        override fun decodeFromJson(allocator: BinaryAllocator, json: JsonElement): FindBulkResponse {
            if (json !is JsonObject) error("FindBulkResponse must be decoded from an object")
            val avatars = run {
                val element = json["avatars"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    BinaryTypeDictionary.decodeFromJson(Avatar, allocator, element)
                }
            } ?: error("Missing required property: avatars in FindBulkResponse")
            return allocator.FindBulkResponse(
                avatars = avatars,
            )
        }
    }
}


fun BinaryAllocator.FindBulkResponse(
    avatars: BinaryTypeDictionary<Avatar>,
): FindBulkResponse {
    val result = this.allocate(FindBulkResponse)
    result.avatars = avatars
    return result
}

@JvmInline
value class Simple(override val buffer: BufferAndOffset) : BinaryType {
    var fie: Int
        inline get() = buffer.data.getInt(0 + buffer.offset)
        inline set (value) { buffer.data.putInt(0 + buffer.offset, value) }

    var _hund: Text
        inline get() = Text(buffer.copy(offset = buffer.data.getInt(4 + buffer.offset)))
        inline set(value) { buffer.data.putInt(4 + buffer.offset, value.buffer.offset) }
    val hund: String
        inline get() = _hund.decode()

    var enumeration: Top
        inline get() = buffer.data.getShort(8 + buffer.offset).let { Top.fromEncoded(it.toInt()) }
        inline set (value) { buffer.data.putShort(8 + buffer.offset, value.encoded.toShort()) }

    override fun encodeToJson(): JsonElement = JsonObject(mapOf(
        "fie" to (fie.let { JsonPrimitive(it) }),
        "hund" to (hund.let { JsonPrimitive(it) }),
        "enumeration" to (enumeration.let { JsonPrimitive(it.serialName) }),
    ))

    companion object : BinaryTypeCompanion<Simple> {
        override val size = 10
        private val mySerializer = BinaryTypeSerializer(this)
        fun serializer() = mySerializer
        override fun create(buffer: BufferAndOffset) = Simple(buffer)
        override fun decodeFromJson(allocator: BinaryAllocator, json: JsonElement): Simple {
            if (json !is JsonObject) error("Simple must be decoded from an object")
            val fie = run {
                val element = json["fie"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'fie' to be a primitive")
                    element.content.toInt()
                }
            } ?: error("Missing required property: fie in Simple")
            val hund = run {
                val element = json["hund"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'hund' to be a primitive")
                    element.content
                }
            } ?: error("Missing required property: hund in Simple")
            val enumeration = run {
                val element = json["enumeration"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    if (element !is JsonPrimitive) error("Expected 'enumeration' to be a primitive")
                    element.content.let { Top.fromSerialName(it) }
                }
            } ?: error("Missing required property: enumeration in Simple")
            return allocator.Simple(
                fie = fie,
                hund = hund,
                enumeration = enumeration,
            )
        }
    }
}


fun BinaryAllocator.Simple(
    fie: Int,
    hund: String,
    enumeration: Top,
): Simple {
    val result = this.allocate(Simple)
    result.fie = fie
    result._hund = hund.let { allocateText(it) }
    result.enumeration = enumeration
    return result
}

sealed interface AppParameter : BinaryType {
    @JvmInline
    value class File(override val buffer: BufferAndOffset) : BinaryType, AppParameter {
        init {
            buffer.data.put(0 + buffer.offset, 1)
        }

        var _path: Text
            inline get() = Text(buffer.copy(offset = buffer.data.getInt(1 + buffer.offset)))
            inline set(value) { buffer.data.putInt(1 + buffer.offset, value.buffer.offset) }
        val path: String
            inline get() = _path.decode()

        override fun encodeToJson(): JsonElement = JsonObject(mapOf(
            "type" to JsonPrimitive("file"),
            "path" to (path.let { JsonPrimitive(it) }),
        ))

        companion object : BinaryTypeCompanion<File> {
            override val size = 5
            private val mySerializer = BinaryTypeSerializer(this)
            fun serializer() = mySerializer
            override fun create(buffer: BufferAndOffset) = File(buffer)
            override fun decodeFromJson(allocator: BinaryAllocator, json: JsonElement): File {
                if (json !is JsonObject) error("File must be decoded from an object")
                val path = run {
                    val element = json["path"]
                    if (element == null || element == JsonNull) {
                        null
                    } else {
                        if (element !is JsonPrimitive) error("Expected 'path' to be a primitive")
                        element.content
                    }
                } ?: error("Missing required property: path in File")
                return allocator.AppParameterFile(
                    path = path,
                )
            }
        }
    }

    @JvmInline
    value class TextString(override val buffer: BufferAndOffset) : BinaryType, AppParameter {
        init {
            buffer.data.put(0 + buffer.offset, 2)
        }

        var _value: Text
            inline get() = Text(buffer.copy(offset = buffer.data.getInt(1 + buffer.offset)))
            inline set(value) { buffer.data.putInt(1 + buffer.offset, value.buffer.offset) }
        val value: String
            inline get() = _value.decode()

        override fun encodeToJson(): JsonElement = JsonObject(mapOf(
            "type" to JsonPrimitive("text"),
            "value" to (value.let { JsonPrimitive(it) }),
        ))

        companion object : BinaryTypeCompanion<TextString> {
            override val size = 5
            private val mySerializer = BinaryTypeSerializer(this)
            fun serializer() = mySerializer
            override fun create(buffer: BufferAndOffset) = TextString(buffer)
            override fun decodeFromJson(allocator: BinaryAllocator, json: JsonElement): TextString {
                if (json !is JsonObject) error("TextString must be decoded from an object")
                val value = run {
                    val element = json["value"]
                    if (element == null || element == JsonNull) {
                        null
                    } else {
                        if (element !is JsonPrimitive) error("Expected 'value' to be a primitive")
                        element.content
                    }
                } ?: error("Missing required property: value in TextString")
                return allocator.AppParameterTextString(
                    value = value,
                )
            }
        }
    }

    @JvmInline
    value class IntegerNumber(override val buffer: BufferAndOffset) : BinaryType, AppParameter {
        init {
            buffer.data.put(0 + buffer.offset, 3)
        }

        var value: Int
            inline get() = buffer.data.getInt(1 + buffer.offset)
            inline set (value) { buffer.data.putInt(1 + buffer.offset, value) }

        override fun encodeToJson(): JsonElement = JsonObject(mapOf(
            "type" to JsonPrimitive("int"),
            "value" to (value.let { JsonPrimitive(it) }),
        ))

        companion object : BinaryTypeCompanion<IntegerNumber> {
            override val size = 5
            private val mySerializer = BinaryTypeSerializer(this)
            fun serializer() = mySerializer
            override fun create(buffer: BufferAndOffset) = IntegerNumber(buffer)
            override fun decodeFromJson(allocator: BinaryAllocator, json: JsonElement): IntegerNumber {
                if (json !is JsonObject) error("IntegerNumber must be decoded from an object")
                val value = run {
                    val element = json["value"]
                    if (element == null || element == JsonNull) {
                        null
                    } else {
                        if (element !is JsonPrimitive) error("Expected 'value' to be a primitive")
                        element.content.toInt()
                    }
                } ?: error("Missing required property: value in IntegerNumber")
                return allocator.AppParameterIntegerNumber(
                    value = value,
                )
            }
        }
    }

    companion object : BinaryTypeCompanion<AppParameter> {
        override val size = 0
        override fun create(buffer: BufferAndOffset) = interpret(buffer)

        fun interpret(ptr: BufferAndOffset): AppParameter {
            return when (val tag = ptr.data.get(ptr.offset).toInt()) {
                1 -> File(ptr)
                2 -> TextString(ptr)
                3 -> IntegerNumber(ptr)
                else -> error("Invalid AppParameter representation stored: $tag")
            }
        }

        override fun decodeFromJson(allocator: BinaryAllocator, json: JsonElement): AppParameter {
            if (json !is JsonObject) error("AppParameter must be decoded from an object")
            val type = json["type"]
            if (type !is JsonPrimitive) error("Missing type tag")
            return when (type.content) {
                "file" -> File.decodeFromJson(allocator, json)"text" -> TextString.decodeFromJson(allocator, json)"int" -> IntegerNumber.decodeFromJson(allocator, json)else -> error("Unknown type: ${type.content}")
            }
        }
    }
}

fun BinaryAllocator.AppParameterFile(
    path: String,
): AppParameter.File {
    val result = this.allocate(AppParameter.File)
    result._path = path.let { allocateText(it) }
    return result
}
fun BinaryAllocator.AppParameterTextString(
    value: String,
): AppParameter.TextString {
    val result = this.allocate(AppParameter.TextString)
    result._value = value.let { allocateText(it) }
    return result
}
fun BinaryAllocator.AppParameterIntegerNumber(
    value: Int,
): AppParameter.IntegerNumber {
    val result = this.allocate(AppParameter.IntegerNumber)
    result.value = value
    return result
}

@JvmInline
value class Wrapper(override val buffer: BufferAndOffset) : BinaryType {
    var wrapThis: AppParameter?
        inline get() {
            val offset = buffer.data.getInt(0 + buffer.offset)
            return (if (offset == 0) {
                null
            } else {
                AppParameter.interpret(buffer.copy(offset = offset))
            })
        }
        inline set(value) {
            buffer.data.putInt(0 + buffer.offset, value?.buffer?.offset ?: 0)
        }

    override fun encodeToJson(): JsonElement = JsonObject(mapOf(
        "wrapThis" to (wrapThis?.let { it.encodeToJson() } ?: JsonNull),
    ))

    companion object : BinaryTypeCompanion<Wrapper> {
        override val size = 4
        private val mySerializer = BinaryTypeSerializer(this)
        fun serializer() = mySerializer
        override fun create(buffer: BufferAndOffset) = Wrapper(buffer)
        override fun decodeFromJson(allocator: BinaryAllocator, json: JsonElement): Wrapper {
            if (json !is JsonObject) error("Wrapper must be decoded from an object")
            val wrapThis = run {
                val element = json["wrapThis"]
                if (element == null || element == JsonNull) {
                    null
                } else {
                    AppParameter.decodeFromJson(allocator, element)
                }
            }
            return allocator.Wrapper(
                wrapThis = wrapThis,
            )
        }
    }
}


fun BinaryAllocator.Wrapper(
    wrapThis: AppParameter? = null,
): Wrapper {
    val result = this.allocate(Wrapper)
    result.wrapThis = wrapThis
    return result
}

