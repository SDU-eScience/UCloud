package avatars

enum Top {
    @Json(name = "NoHair") NO_HAIR = 1,
    @Json(name = "Eyepatch") EYEPATCH = 2,
    @Json(name = "Hat") HAT = 3,
    @Json(name = "Hijab") HIJAB = 4,
    @Json(name = "Turban") TURBAN = 5,
    @Json(name = "WinterHat1") WINTER_HAT1 = 6,
    @Json(name = "WinterHat2") WINTER_HAT2 = 7,
    @Json(name = "WinterHat3") WINTER_HAT3 = 8,
    @Json(name = "WinterHat4") WINTER_HAT4 = 9,
    @Json(name = "LongHairBigHair") LONG_HAIR_BIG_HAIR = 10,
    @Json(name = "LongHairBob") LONG_HAIR_BOB = 11,
    @Json(name = "LongHairBun") LONG_HAIR_BUN = 12,
    @Json(name = "LongHairCurly") LONG_HAIR_CURLY = 13,
    @Json(name = "LongHairCurvy") LONG_HAIR_CURVY = 14,
    @Json(name = "LongHairDreads") LONG_HAIR_DREADS = 15,
    @Json(name = "LongHairFrida") LONG_HAIR_FRIDA = 16,
    @Json(name = "LongHairFro") LONG_HAIR_FRO = 17,
    @Json(name = "LongHairFroBand") LONG_HAIR_FRO_BAND = 18,
    @Json(name = "LongHairNotTooLong") LONG_HAIR_NOT_TOO_LONG = 19,
    @Json(name = "LongHairShavedSides") LONG_HAIR_SHAVED_SIDES = 20,
    @Json(name = "LongHairMiaWallace") LONG_HAIR_MIA_WALLACE = 21,
    @Json(name = "LongHairStraight") LONG_HAIR_STRAIGHT = 22,
    @Json(name = "LongHairStraight2") LONG_HAIR_STRAIGHT2 = 23,
    @Json(name = "LongHairStraightStrand") LONG_HAIR_STRAIGHT_STRAND = 24,
    @Json(name = "ShortHairDreads01") SHORT_HAIR_DREADS01 = 25,
    @Json(name = "ShortHairDreads02") SHORT_HAIR_DREADS02 = 26,
    @Json(name = "ShortHairFrizzle") SHORT_HAIR_FRIZZLE = 27,
    @Json(name = "ShortHairShaggyMullet") SHORT_HAIR_SHAGGY_MULLET = 28,
    @Json(name = "ShortHairShortCurly") SHORT_HAIR_SHORT_CURLY = 29,
    @Json(name = "ShortHairShortFlat") SHORT_HAIR_SHORT_FLAT = 30,
    @Json(name = "ShortHairShortRound") SHORT_HAIR_SHORT_ROUND = 31,
    @Json(name = "ShortHairShortWaved") SHORT_HAIR_SHORT_WAVED = 32,
    @Json(name = "ShortHairSides") SHORT_HAIR_SIDES = 33,
    @Json(name = "ShortHairTheCaesar") SHORT_HAIR_THE_CAESAR = 34,
    @Json(name = "ShortHairTheCaesarSidePart") SHORT_HAIR_THE_CAESAR_SIDE_PART = 35,
}

enum TopAccessory {
    @Json(name = "Blank") BLANK = 1,
    @Json(name = "Kurt") KURT = 2,
    @Json(name = "Prescription01") PRESCRIPTION01 = 3,
    @Json(name = "Prescription02") PRESCRIPTION02 = 4,
    @Json(name = "Round") ROUND = 5,
    @Json(name = "Sunglasses") SUNGLASSES = 6,
    @Json(name = "Wayfarers") WAYFARERS = 7,
}

enum HairColor {
    @Json(name = "Auburn") AUBURN = 1,
    @Json(name = "Black") BLACK = 2,
    @Json(name = "Blonde") BLONDE = 3,
    @Json(name = "BlondeGolden") BLONDE_GOLDEN = 4,
    @Json(name = "Brown") BROWN = 5,
    @Json(name = "BrownDark") BROWN_DARK = 6,
    @Json(name = "PastelPink") PASTEL_PINK = 7,
    @Json(name = "Platinum") PLATINUM = 8,
    @Json(name = "Red") RED = 9,
    @Json(name = "SilverGray") SILVER_GRAY = 10,
}

enum HatColor {
    @Json(name = "Black") BLACK = 1,
    @Json(name = "Blue01") BLUE01 = 2,
    @Json(name = "Blue02") BLUE02 = 3,
    @Json(name = "Blue03") BLUE03 = 4,
    @Json(name = "Gray01") GRAY01 = 5,
    @Json(name = "Gray02") GRAY02 = 6,
    @Json(name = "Heather") HEATHER = 7,
    @Json(name = "PastelBlue") PASTELBLUE = 8,
    @Json(name = "PastelGreen") PASTELGREEN = 9,
    @Json(name = "PastelOrange") PASTELORANGE = 10,
    @Json(name = "PastelRed") PASTELRED = 11,
    @Json(name = "PastelYellow") PASTELYELLOW = 12,
    @Json(name = "Pink") PINK = 13,
    @Json(name = "Red") RED = 14,
    @Json(name = "White") WHITE = 15,
}

enum FacialHair {
    @Json(name = "Blank") BLANK = 1,
    @Json(name = "BeardMedium") BEARD_MEDIUM = 2,
    @Json(name = "BeardLight") BEARD_LIGHT = 3,
    @Json(name = "BeardMajestic") BEARD_MAJESTIC = 4,
    @Json(name = "MoustacheFancy") MOUSTACHE_FANCY = 5,
    @Json(name = "MoustacheMagnum") MOUSTACHE_MAGNUM = 6,
}

enum FacialHairColor {
    @Json(name = "Auburn") AUBURN = 1,
    @Json(name = "Black") BLACK = 2,
    @Json(name = "Blonde") BLONDE = 3,
    @Json(name = "BlondeGolden") BLONDE_GOLDEN = 4,
    @Json(name = "Brown") BROWN = 5,
    @Json(name = "BrownDark") BROWN_DARK = 6,
    @Json(name = "Platinum") PLATINUM = 7,
    @Json(name = "Red") RED = 8,
}

enum Clothes {
    @Json(name = "BlazerShirt") BLAZER_SHIRT = 1,
    @Json(name = "BlazerSweater") BLAZER_SWEATER = 2,
    @Json(name = "CollarSweater") COLLAR_SWEATER = 3,
    @Json(name = "GraphicShirt") GRAPHIC_SHIRT = 4,
    @Json(name = "Hoodie") HOODIE = 5,
    @Json(name = "Overall") OVERALL = 6,
    @Json(name = "ShirtCrewNeck") SHIRT_CREW_NECK = 7,
    @Json(name = "ShirtScoopNeck") SHIRT_SCOOP_NECK = 8,
    @Json(name = "ShirtVNeck") SHIRT_V_NECK = 9,
}

enum ColorFabric {
    @Json(name = "Black") BLACK = 1,
    @Json(name = "Blue01") BLUE01 = 2,
    @Json(name = "Blue02") BLUE02 = 3,
    @Json(name = "Blue03") BLUE03 = 4,
    @Json(name = "Gray01") GRAY01 = 5,
    @Json(name = "Gray02") GRAY02 = 6,
    @Json(name = "Heather") HEATHER = 7,
    @Json(name = "PastelBlue") PASTEL_BLUE = 8,
    @Json(name = "PastelGreen") PASTEL_GREEN = 9,
    @Json(name = "PastelOrange") PASTEL_ORANGE = 10,
    @Json(name = "PastelRed") PASTEL_RED = 11,
    @Json(name = "PastelYellow") PASTEL_YELLOW = 12,
    @Json(name = "Pink") PINK = 13,
    @Json(name = "Red") RED = 14,
    @Json(name = "White") WHITE = 15,
}

enum Eyes {
    @Json(name = "Close") CLOSE = 1,
    @Json(name = "Cry") CRY = 2,
    @Json(name = "Default") DEFAULT = 3,
    @Json(name = "Dizzy") DIZZY = 4,
    @Json(name = "EyeRoll") EYE_ROLL = 5,
    @Json(name = "Happy") HAPPY = 6,
    @Json(name = "Hearts") HEARTS = 7,
    @Json(name = "Side") SIDE = 8,
    @Json(name = "Squint") SQUINT = 9,
    @Json(name = "Surprised") SURPRISED = 10,
    @Json(name = "Wink") WINK = 11,
    @Json(name = "WinkWacky") WINK_WACKY = 12,
}

enum Eyebrows {
    @Json(name = "Angry") ANGRY = 1,
    @Json(name = "AngryNatural") ANGRY_NATURAL = 2,
    @Json(name = "Default") DEFAULT = 3,
    @Json(name = "DefaultNatural") DEFAULT_NATURAL = 4,
    @Json(name = "FlatNatural") FLAT_NATURAL = 5,
    @Json(name = "FrownNatural") FROWN_NATURAL = 6,
    @Json(name = "RaisedExcited") RAISED_EXCITED = 7,
    @Json(name = "RaisedExcitedNatural") RAISED_EXCITED_NATURAL = 8,
    @Json(name = "SadConcerned") SAD_CONCERNED = 9,
    @Json(name = "SadConcernedNatural") SAD_CONCERNED_NATURAL = 10,
    @Json(name = "UnibrowNatural") UNIBROW_NATURAL = 11,
    @Json(name = "UpDown") UP_DOWN = 12,
    @Json(name = "UpDownNatural") UP_DOWN_NATURAL = 13,
}

enum MouthTypes {
    @Json(name = "Concerned") CONCERNED = 1,
    @Json(name = "Default") DEFAULT = 2,
    @Json(name = "Disbelief") DISBELIEF = 3,
    @Json(name = "Eating") EATING = 4,
    @Json(name = "Grimace") GRIMACE = 5,
    @Json(name = "Sad") SAD = 6,
    @Json(name = "ScreamOpen") SCREAM_OPEN = 7,
    @Json(name = "Serious") SERIOUS = 8,
    @Json(name = "Smile") SMILE = 9,
    @Json(name = "Tongue") TONGUE = 10,
    @Json(name = "Twinkle") TWINKLE = 11,
    @Json(name = "Vomit") VOMIT = 12,
}

enum SkinColors {
    @Json(name = "Tanned") TANNED = 1,
    @Json(name = "Yellow") YELLOW = 2,
    @Json(name = "Pale") PALE = 3,
    @Json(name = "Light") LIGHT = 4,
    @Json(name = "Brown") BROWN = 5,
    @Json(name = "DarkBrown") DARK_BROWN = 6,
    @Json(name = "Black") BLACK = 7,
}

enum ClothesGraphic {
    @Json(name = "Bat") BAT = 1,
    @Json(name = "Cumbia") CUMBIA = 2,
    @Json(name = "Deer") DEER = 3,
    @Json(name = "Diamond") DIAMOND = 4,
    @Json(name = "Hola") HOLA = 5,
    @Json(name = "Pizza") PIZZA = 6,
    @Json(name = "Resist") RESIST = 7,
    @Json(name = "Selena") SELENA = 8,
    @Json(name = "Bear") BEAR = 9,
    @Json(name = "SkullOutline") SKULL_OUTLINE = 10,
    @Json(name = "Skull") SKULL = 11,
    @Json(name = "Espie") ESPIE = 12,
    @Json(name = "EScienceLogo") ESCIENCELOGO = 13,
    @Json(name = "Teeth") TEETH = 14,
}

record Avatar {
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
}

record FindBulkRequest {
    username: String[], // Array, equivalent to List<String>
}

record FindBulkResponse {
    avatars: Avatar{}, // Dictionary, which is equivalent to Map<String, Avatar>
}

record A {
    fie: B,
}

record B {
    value: Int,
    next: B?,
}
