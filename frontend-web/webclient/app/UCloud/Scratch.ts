// GENERATED CODE - DO NOT MODIFY - See Avatars.msg
// GENERATED CODE - DO NOT MODIFY - See Avatars.msg
// GENERATED CODE - DO NOT MODIFY - See Avatars.msg

import { BinaryAllocator, UBinaryType, BinaryTypeCompanion, UText, UTextCompanion, BufferAndOffset, BinaryTypeList, BinaryTypeDictionary } from "@/UCloud/Messages";

export enum Top {
    NO_HAIR,
    EYEPATCH,
    HAT,
    HIJAB,
    TURBAN,
    WINTER_HAT1,
    WINTER_HAT2,
    WINTER_HAT3,
    WINTER_HAT4,
    LONG_HAIR_BIG_HAIR,
    LONG_HAIR_BOB,
    LONG_HAIR_BUN,
    LONG_HAIR_CURLY,
    LONG_HAIR_CURVY,
    LONG_HAIR_DREADS,
    LONG_HAIR_FRIDA,
    LONG_HAIR_FRO,
    LONG_HAIR_FRO_BAND,
    LONG_HAIR_NOT_TOO_LONG,
    LONG_HAIR_SHAVED_SIDES,
    LONG_HAIR_MIA_WALLACE,
    LONG_HAIR_STRAIGHT,
    LONG_HAIR_STRAIGHT2,
    LONG_HAIR_STRAIGHT_STRAND,
    SHORT_HAIR_DREADS01,
    SHORT_HAIR_DREADS02,
    SHORT_HAIR_FRIZZLE,
    SHORT_HAIR_SHAGGY_MULLET,
    SHORT_HAIR_SHORT_CURLY,
    SHORT_HAIR_SHORT_FLAT,
    SHORT_HAIR_SHORT_ROUND,
    SHORT_HAIR_SHORT_WAVED,
    SHORT_HAIR_SIDES,
    SHORT_HAIR_THE_CAESAR,
    SHORT_HAIR_THE_CAESAR_SIDE_PART,
}

export const TopCompanion = {
    name(element: Top): string {
        switch (element) {
            case Top.NO_HAIR: return "NO_HAIR";
            case Top.EYEPATCH: return "EYEPATCH";
            case Top.HAT: return "HAT";
            case Top.HIJAB: return "HIJAB";
            case Top.TURBAN: return "TURBAN";
            case Top.WINTER_HAT1: return "WINTER_HAT1";
            case Top.WINTER_HAT2: return "WINTER_HAT2";
            case Top.WINTER_HAT3: return "WINTER_HAT3";
            case Top.WINTER_HAT4: return "WINTER_HAT4";
            case Top.LONG_HAIR_BIG_HAIR: return "LONG_HAIR_BIG_HAIR";
            case Top.LONG_HAIR_BOB: return "LONG_HAIR_BOB";
            case Top.LONG_HAIR_BUN: return "LONG_HAIR_BUN";
            case Top.LONG_HAIR_CURLY: return "LONG_HAIR_CURLY";
            case Top.LONG_HAIR_CURVY: return "LONG_HAIR_CURVY";
            case Top.LONG_HAIR_DREADS: return "LONG_HAIR_DREADS";
            case Top.LONG_HAIR_FRIDA: return "LONG_HAIR_FRIDA";
            case Top.LONG_HAIR_FRO: return "LONG_HAIR_FRO";
            case Top.LONG_HAIR_FRO_BAND: return "LONG_HAIR_FRO_BAND";
            case Top.LONG_HAIR_NOT_TOO_LONG: return "LONG_HAIR_NOT_TOO_LONG";
            case Top.LONG_HAIR_SHAVED_SIDES: return "LONG_HAIR_SHAVED_SIDES";
            case Top.LONG_HAIR_MIA_WALLACE: return "LONG_HAIR_MIA_WALLACE";
            case Top.LONG_HAIR_STRAIGHT: return "LONG_HAIR_STRAIGHT";
            case Top.LONG_HAIR_STRAIGHT2: return "LONG_HAIR_STRAIGHT2";
            case Top.LONG_HAIR_STRAIGHT_STRAND: return "LONG_HAIR_STRAIGHT_STRAND";
            case Top.SHORT_HAIR_DREADS01: return "SHORT_HAIR_DREADS01";
            case Top.SHORT_HAIR_DREADS02: return "SHORT_HAIR_DREADS02";
            case Top.SHORT_HAIR_FRIZZLE: return "SHORT_HAIR_FRIZZLE";
            case Top.SHORT_HAIR_SHAGGY_MULLET: return "SHORT_HAIR_SHAGGY_MULLET";
            case Top.SHORT_HAIR_SHORT_CURLY: return "SHORT_HAIR_SHORT_CURLY";
            case Top.SHORT_HAIR_SHORT_FLAT: return "SHORT_HAIR_SHORT_FLAT";
            case Top.SHORT_HAIR_SHORT_ROUND: return "SHORT_HAIR_SHORT_ROUND";
            case Top.SHORT_HAIR_SHORT_WAVED: return "SHORT_HAIR_SHORT_WAVED";
            case Top.SHORT_HAIR_SIDES: return "SHORT_HAIR_SIDES";
            case Top.SHORT_HAIR_THE_CAESAR: return "SHORT_HAIR_THE_CAESAR";
            case Top.SHORT_HAIR_THE_CAESAR_SIDE_PART: return "SHORT_HAIR_THE_CAESAR_SIDE_PART";
        }
    },

    serialName(element: Top): string {
        switch (element) {
            case Top.NO_HAIR: return "NoHair";
            case Top.EYEPATCH: return "Eyepatch";
            case Top.HAT: return "Hat";
            case Top.HIJAB: return "Hijab";
            case Top.TURBAN: return "Turban";
            case Top.WINTER_HAT1: return "WinterHat1";
            case Top.WINTER_HAT2: return "WinterHat2";
            case Top.WINTER_HAT3: return "WinterHat3";
            case Top.WINTER_HAT4: return "WinterHat4";
            case Top.LONG_HAIR_BIG_HAIR: return "LongHairBigHair";
            case Top.LONG_HAIR_BOB: return "LongHairBob";
            case Top.LONG_HAIR_BUN: return "LongHairBun";
            case Top.LONG_HAIR_CURLY: return "LongHairCurly";
            case Top.LONG_HAIR_CURVY: return "LongHairCurvy";
            case Top.LONG_HAIR_DREADS: return "LongHairDreads";
            case Top.LONG_HAIR_FRIDA: return "LongHairFrida";
            case Top.LONG_HAIR_FRO: return "LongHairFro";
            case Top.LONG_HAIR_FRO_BAND: return "LongHairFroBand";
            case Top.LONG_HAIR_NOT_TOO_LONG: return "LongHairNotTooLong";
            case Top.LONG_HAIR_SHAVED_SIDES: return "LongHairShavedSides";
            case Top.LONG_HAIR_MIA_WALLACE: return "LongHairMiaWallace";
            case Top.LONG_HAIR_STRAIGHT: return "LongHairStraight";
            case Top.LONG_HAIR_STRAIGHT2: return "LongHairStraight2";
            case Top.LONG_HAIR_STRAIGHT_STRAND: return "LongHairStraightStrand";
            case Top.SHORT_HAIR_DREADS01: return "ShortHairDreads01";
            case Top.SHORT_HAIR_DREADS02: return "ShortHairDreads02";
            case Top.SHORT_HAIR_FRIZZLE: return "ShortHairFrizzle";
            case Top.SHORT_HAIR_SHAGGY_MULLET: return "ShortHairShaggyMullet";
            case Top.SHORT_HAIR_SHORT_CURLY: return "ShortHairShortCurly";
            case Top.SHORT_HAIR_SHORT_FLAT: return "ShortHairShortFlat";
            case Top.SHORT_HAIR_SHORT_ROUND: return "ShortHairShortRound";
            case Top.SHORT_HAIR_SHORT_WAVED: return "ShortHairShortWaved";
            case Top.SHORT_HAIR_SIDES: return "ShortHairSides";
            case Top.SHORT_HAIR_THE_CAESAR: return "ShortHairTheCaesar";
            case Top.SHORT_HAIR_THE_CAESAR_SIDE_PART: return "ShortHairTheCaesarSidePart";
        }
    },

    encoded(element: Top): number {
        switch (element) {
            case Top.NO_HAIR: return 1;
            case Top.EYEPATCH: return 2;
            case Top.HAT: return 3;
            case Top.HIJAB: return 4;
            case Top.TURBAN: return 5;
            case Top.WINTER_HAT1: return 6;
            case Top.WINTER_HAT2: return 7;
            case Top.WINTER_HAT3: return 8;
            case Top.WINTER_HAT4: return 9;
            case Top.LONG_HAIR_BIG_HAIR: return 10;
            case Top.LONG_HAIR_BOB: return 11;
            case Top.LONG_HAIR_BUN: return 12;
            case Top.LONG_HAIR_CURLY: return 13;
            case Top.LONG_HAIR_CURVY: return 14;
            case Top.LONG_HAIR_DREADS: return 15;
            case Top.LONG_HAIR_FRIDA: return 16;
            case Top.LONG_HAIR_FRO: return 17;
            case Top.LONG_HAIR_FRO_BAND: return 18;
            case Top.LONG_HAIR_NOT_TOO_LONG: return 19;
            case Top.LONG_HAIR_SHAVED_SIDES: return 20;
            case Top.LONG_HAIR_MIA_WALLACE: return 21;
            case Top.LONG_HAIR_STRAIGHT: return 22;
            case Top.LONG_HAIR_STRAIGHT2: return 23;
            case Top.LONG_HAIR_STRAIGHT_STRAND: return 24;
            case Top.SHORT_HAIR_DREADS01: return 25;
            case Top.SHORT_HAIR_DREADS02: return 26;
            case Top.SHORT_HAIR_FRIZZLE: return 27;
            case Top.SHORT_HAIR_SHAGGY_MULLET: return 28;
            case Top.SHORT_HAIR_SHORT_CURLY: return 29;
            case Top.SHORT_HAIR_SHORT_FLAT: return 30;
            case Top.SHORT_HAIR_SHORT_ROUND: return 31;
            case Top.SHORT_HAIR_SHORT_WAVED: return 32;
            case Top.SHORT_HAIR_SIDES: return 33;
            case Top.SHORT_HAIR_THE_CAESAR: return 34;
            case Top.SHORT_HAIR_THE_CAESAR_SIDE_PART: return 35;
        }
    },

    fromSerialName(name: string): Top | null {
        switch (name) {
            case "NoHair": return Top.NO_HAIR;
            case "Eyepatch": return Top.EYEPATCH;
            case "Hat": return Top.HAT;
            case "Hijab": return Top.HIJAB;
            case "Turban": return Top.TURBAN;
            case "WinterHat1": return Top.WINTER_HAT1;
            case "WinterHat2": return Top.WINTER_HAT2;
            case "WinterHat3": return Top.WINTER_HAT3;
            case "WinterHat4": return Top.WINTER_HAT4;
            case "LongHairBigHair": return Top.LONG_HAIR_BIG_HAIR;
            case "LongHairBob": return Top.LONG_HAIR_BOB;
            case "LongHairBun": return Top.LONG_HAIR_BUN;
            case "LongHairCurly": return Top.LONG_HAIR_CURLY;
            case "LongHairCurvy": return Top.LONG_HAIR_CURVY;
            case "LongHairDreads": return Top.LONG_HAIR_DREADS;
            case "LongHairFrida": return Top.LONG_HAIR_FRIDA;
            case "LongHairFro": return Top.LONG_HAIR_FRO;
            case "LongHairFroBand": return Top.LONG_HAIR_FRO_BAND;
            case "LongHairNotTooLong": return Top.LONG_HAIR_NOT_TOO_LONG;
            case "LongHairShavedSides": return Top.LONG_HAIR_SHAVED_SIDES;
            case "LongHairMiaWallace": return Top.LONG_HAIR_MIA_WALLACE;
            case "LongHairStraight": return Top.LONG_HAIR_STRAIGHT;
            case "LongHairStraight2": return Top.LONG_HAIR_STRAIGHT2;
            case "LongHairStraightStrand": return Top.LONG_HAIR_STRAIGHT_STRAND;
            case "ShortHairDreads01": return Top.SHORT_HAIR_DREADS01;
            case "ShortHairDreads02": return Top.SHORT_HAIR_DREADS02;
            case "ShortHairFrizzle": return Top.SHORT_HAIR_FRIZZLE;
            case "ShortHairShaggyMullet": return Top.SHORT_HAIR_SHAGGY_MULLET;
            case "ShortHairShortCurly": return Top.SHORT_HAIR_SHORT_CURLY;
            case "ShortHairShortFlat": return Top.SHORT_HAIR_SHORT_FLAT;
            case "ShortHairShortRound": return Top.SHORT_HAIR_SHORT_ROUND;
            case "ShortHairShortWaved": return Top.SHORT_HAIR_SHORT_WAVED;
            case "ShortHairSides": return Top.SHORT_HAIR_SIDES;
            case "ShortHairTheCaesar": return Top.SHORT_HAIR_THE_CAESAR;
            case "ShortHairTheCaesarSidePart": return Top.SHORT_HAIR_THE_CAESAR_SIDE_PART;
            default: return null;
        }
    },

    fromEncoded(encoded: number): Top | null {
        switch (encoded) {
            case 1: return Top.NO_HAIR;
            case 2: return Top.EYEPATCH;
            case 3: return Top.HAT;
            case 4: return Top.HIJAB;
            case 5: return Top.TURBAN;
            case 6: return Top.WINTER_HAT1;
            case 7: return Top.WINTER_HAT2;
            case 8: return Top.WINTER_HAT3;
            case 9: return Top.WINTER_HAT4;
            case 10: return Top.LONG_HAIR_BIG_HAIR;
            case 11: return Top.LONG_HAIR_BOB;
            case 12: return Top.LONG_HAIR_BUN;
            case 13: return Top.LONG_HAIR_CURLY;
            case 14: return Top.LONG_HAIR_CURVY;
            case 15: return Top.LONG_HAIR_DREADS;
            case 16: return Top.LONG_HAIR_FRIDA;
            case 17: return Top.LONG_HAIR_FRO;
            case 18: return Top.LONG_HAIR_FRO_BAND;
            case 19: return Top.LONG_HAIR_NOT_TOO_LONG;
            case 20: return Top.LONG_HAIR_SHAVED_SIDES;
            case 21: return Top.LONG_HAIR_MIA_WALLACE;
            case 22: return Top.LONG_HAIR_STRAIGHT;
            case 23: return Top.LONG_HAIR_STRAIGHT2;
            case 24: return Top.LONG_HAIR_STRAIGHT_STRAND;
            case 25: return Top.SHORT_HAIR_DREADS01;
            case 26: return Top.SHORT_HAIR_DREADS02;
            case 27: return Top.SHORT_HAIR_FRIZZLE;
            case 28: return Top.SHORT_HAIR_SHAGGY_MULLET;
            case 29: return Top.SHORT_HAIR_SHORT_CURLY;
            case 30: return Top.SHORT_HAIR_SHORT_FLAT;
            case 31: return Top.SHORT_HAIR_SHORT_ROUND;
            case 32: return Top.SHORT_HAIR_SHORT_WAVED;
            case 33: return Top.SHORT_HAIR_SIDES;
            case 34: return Top.SHORT_HAIR_THE_CAESAR;
            case 35: return Top.SHORT_HAIR_THE_CAESAR_SIDE_PART;
            default: return null;
        }
    },
};

export enum TopAccessory {
    BLANK,
    KURT,
    PRESCRIPTION01,
    PRESCRIPTION02,
    ROUND,
    SUNGLASSES,
    WAYFARERS,
}

export const TopAccessoryCompanion = {
    name(element: TopAccessory): string {
        switch (element) {
            case TopAccessory.BLANK: return "BLANK";
            case TopAccessory.KURT: return "KURT";
            case TopAccessory.PRESCRIPTION01: return "PRESCRIPTION01";
            case TopAccessory.PRESCRIPTION02: return "PRESCRIPTION02";
            case TopAccessory.ROUND: return "ROUND";
            case TopAccessory.SUNGLASSES: return "SUNGLASSES";
            case TopAccessory.WAYFARERS: return "WAYFARERS";
        }
    },

    serialName(element: TopAccessory): string {
        switch (element) {
            case TopAccessory.BLANK: return "Blank";
            case TopAccessory.KURT: return "Kurt";
            case TopAccessory.PRESCRIPTION01: return "Prescription01";
            case TopAccessory.PRESCRIPTION02: return "Prescription02";
            case TopAccessory.ROUND: return "Round";
            case TopAccessory.SUNGLASSES: return "Sunglasses";
            case TopAccessory.WAYFARERS: return "Wayfarers";
        }
    },

    encoded(element: TopAccessory): number {
        switch (element) {
            case TopAccessory.BLANK: return 1;
            case TopAccessory.KURT: return 2;
            case TopAccessory.PRESCRIPTION01: return 3;
            case TopAccessory.PRESCRIPTION02: return 4;
            case TopAccessory.ROUND: return 5;
            case TopAccessory.SUNGLASSES: return 6;
            case TopAccessory.WAYFARERS: return 7;
        }
    },

    fromSerialName(name: string): TopAccessory | null {
        switch (name) {
            case "Blank": return TopAccessory.BLANK;
            case "Kurt": return TopAccessory.KURT;
            case "Prescription01": return TopAccessory.PRESCRIPTION01;
            case "Prescription02": return TopAccessory.PRESCRIPTION02;
            case "Round": return TopAccessory.ROUND;
            case "Sunglasses": return TopAccessory.SUNGLASSES;
            case "Wayfarers": return TopAccessory.WAYFARERS;
            default: return null;
        }
    },

    fromEncoded(encoded: number): TopAccessory | null {
        switch (encoded) {
            case 1: return TopAccessory.BLANK;
            case 2: return TopAccessory.KURT;
            case 3: return TopAccessory.PRESCRIPTION01;
            case 4: return TopAccessory.PRESCRIPTION02;
            case 5: return TopAccessory.ROUND;
            case 6: return TopAccessory.SUNGLASSES;
            case 7: return TopAccessory.WAYFARERS;
            default: return null;
        }
    },
};

export enum HairColor {
    AUBURN,
    BLACK,
    BLONDE,
    BLONDE_GOLDEN,
    BROWN,
    BROWN_DARK,
    PASTEL_PINK,
    PLATINUM,
    RED,
    SILVER_GRAY,
}

export const HairColorCompanion = {
    name(element: HairColor): string {
        switch (element) {
            case HairColor.AUBURN: return "AUBURN";
            case HairColor.BLACK: return "BLACK";
            case HairColor.BLONDE: return "BLONDE";
            case HairColor.BLONDE_GOLDEN: return "BLONDE_GOLDEN";
            case HairColor.BROWN: return "BROWN";
            case HairColor.BROWN_DARK: return "BROWN_DARK";
            case HairColor.PASTEL_PINK: return "PASTEL_PINK";
            case HairColor.PLATINUM: return "PLATINUM";
            case HairColor.RED: return "RED";
            case HairColor.SILVER_GRAY: return "SILVER_GRAY";
        }
    },

    serialName(element: HairColor): string {
        switch (element) {
            case HairColor.AUBURN: return "Auburn";
            case HairColor.BLACK: return "Black";
            case HairColor.BLONDE: return "Blonde";
            case HairColor.BLONDE_GOLDEN: return "BlondeGolden";
            case HairColor.BROWN: return "Brown";
            case HairColor.BROWN_DARK: return "BrownDark";
            case HairColor.PASTEL_PINK: return "PastelPink";
            case HairColor.PLATINUM: return "Platinum";
            case HairColor.RED: return "Red";
            case HairColor.SILVER_GRAY: return "SilverGray";
        }
    },

    encoded(element: HairColor): number {
        switch (element) {
            case HairColor.AUBURN: return 1;
            case HairColor.BLACK: return 2;
            case HairColor.BLONDE: return 3;
            case HairColor.BLONDE_GOLDEN: return 4;
            case HairColor.BROWN: return 5;
            case HairColor.BROWN_DARK: return 6;
            case HairColor.PASTEL_PINK: return 7;
            case HairColor.PLATINUM: return 8;
            case HairColor.RED: return 9;
            case HairColor.SILVER_GRAY: return 10;
        }
    },

    fromSerialName(name: string): HairColor | null {
        switch (name) {
            case "Auburn": return HairColor.AUBURN;
            case "Black": return HairColor.BLACK;
            case "Blonde": return HairColor.BLONDE;
            case "BlondeGolden": return HairColor.BLONDE_GOLDEN;
            case "Brown": return HairColor.BROWN;
            case "BrownDark": return HairColor.BROWN_DARK;
            case "PastelPink": return HairColor.PASTEL_PINK;
            case "Platinum": return HairColor.PLATINUM;
            case "Red": return HairColor.RED;
            case "SilverGray": return HairColor.SILVER_GRAY;
            default: return null;
        }
    },

    fromEncoded(encoded: number): HairColor | null {
        switch (encoded) {
            case 1: return HairColor.AUBURN;
            case 2: return HairColor.BLACK;
            case 3: return HairColor.BLONDE;
            case 4: return HairColor.BLONDE_GOLDEN;
            case 5: return HairColor.BROWN;
            case 6: return HairColor.BROWN_DARK;
            case 7: return HairColor.PASTEL_PINK;
            case 8: return HairColor.PLATINUM;
            case 9: return HairColor.RED;
            case 10: return HairColor.SILVER_GRAY;
            default: return null;
        }
    },
};

export enum HatColor {
    BLACK,
    BLUE01,
    BLUE02,
    BLUE03,
    GRAY01,
    GRAY02,
    HEATHER,
    PASTELBLUE,
    PASTELGREEN,
    PASTELORANGE,
    PASTELRED,
    PASTELYELLOW,
    PINK,
    RED,
    WHITE,
}

export const HatColorCompanion = {
    name(element: HatColor): string {
        switch (element) {
            case HatColor.BLACK: return "BLACK";
            case HatColor.BLUE01: return "BLUE01";
            case HatColor.BLUE02: return "BLUE02";
            case HatColor.BLUE03: return "BLUE03";
            case HatColor.GRAY01: return "GRAY01";
            case HatColor.GRAY02: return "GRAY02";
            case HatColor.HEATHER: return "HEATHER";
            case HatColor.PASTELBLUE: return "PASTELBLUE";
            case HatColor.PASTELGREEN: return "PASTELGREEN";
            case HatColor.PASTELORANGE: return "PASTELORANGE";
            case HatColor.PASTELRED: return "PASTELRED";
            case HatColor.PASTELYELLOW: return "PASTELYELLOW";
            case HatColor.PINK: return "PINK";
            case HatColor.RED: return "RED";
            case HatColor.WHITE: return "WHITE";
        }
    },

    serialName(element: HatColor): string {
        switch (element) {
            case HatColor.BLACK: return "Black";
            case HatColor.BLUE01: return "Blue01";
            case HatColor.BLUE02: return "Blue02";
            case HatColor.BLUE03: return "Blue03";
            case HatColor.GRAY01: return "Gray01";
            case HatColor.GRAY02: return "Gray02";
            case HatColor.HEATHER: return "Heather";
            case HatColor.PASTELBLUE: return "PastelBlue";
            case HatColor.PASTELGREEN: return "PastelGreen";
            case HatColor.PASTELORANGE: return "PastelOrange";
            case HatColor.PASTELRED: return "PastelRed";
            case HatColor.PASTELYELLOW: return "PastelYellow";
            case HatColor.PINK: return "Pink";
            case HatColor.RED: return "Red";
            case HatColor.WHITE: return "White";
        }
    },

    encoded(element: HatColor): number {
        switch (element) {
            case HatColor.BLACK: return 1;
            case HatColor.BLUE01: return 2;
            case HatColor.BLUE02: return 3;
            case HatColor.BLUE03: return 4;
            case HatColor.GRAY01: return 5;
            case HatColor.GRAY02: return 6;
            case HatColor.HEATHER: return 7;
            case HatColor.PASTELBLUE: return 8;
            case HatColor.PASTELGREEN: return 9;
            case HatColor.PASTELORANGE: return 10;
            case HatColor.PASTELRED: return 11;
            case HatColor.PASTELYELLOW: return 12;
            case HatColor.PINK: return 13;
            case HatColor.RED: return 14;
            case HatColor.WHITE: return 15;
        }
    },

    fromSerialName(name: string): HatColor | null {
        switch (name) {
            case "Black": return HatColor.BLACK;
            case "Blue01": return HatColor.BLUE01;
            case "Blue02": return HatColor.BLUE02;
            case "Blue03": return HatColor.BLUE03;
            case "Gray01": return HatColor.GRAY01;
            case "Gray02": return HatColor.GRAY02;
            case "Heather": return HatColor.HEATHER;
            case "PastelBlue": return HatColor.PASTELBLUE;
            case "PastelGreen": return HatColor.PASTELGREEN;
            case "PastelOrange": return HatColor.PASTELORANGE;
            case "PastelRed": return HatColor.PASTELRED;
            case "PastelYellow": return HatColor.PASTELYELLOW;
            case "Pink": return HatColor.PINK;
            case "Red": return HatColor.RED;
            case "White": return HatColor.WHITE;
            default: return null;
        }
    },

    fromEncoded(encoded: number): HatColor | null {
        switch (encoded) {
            case 1: return HatColor.BLACK;
            case 2: return HatColor.BLUE01;
            case 3: return HatColor.BLUE02;
            case 4: return HatColor.BLUE03;
            case 5: return HatColor.GRAY01;
            case 6: return HatColor.GRAY02;
            case 7: return HatColor.HEATHER;
            case 8: return HatColor.PASTELBLUE;
            case 9: return HatColor.PASTELGREEN;
            case 10: return HatColor.PASTELORANGE;
            case 11: return HatColor.PASTELRED;
            case 12: return HatColor.PASTELYELLOW;
            case 13: return HatColor.PINK;
            case 14: return HatColor.RED;
            case 15: return HatColor.WHITE;
            default: return null;
        }
    },
};

export enum FacialHair {
    BLANK,
    BEARD_MEDIUM,
    BEARD_LIGHT,
    BEARD_MAJESTIC,
    MOUSTACHE_FANCY,
    MOUSTACHE_MAGNUM,
}

export const FacialHairCompanion = {
    name(element: FacialHair): string {
        switch (element) {
            case FacialHair.BLANK: return "BLANK";
            case FacialHair.BEARD_MEDIUM: return "BEARD_MEDIUM";
            case FacialHair.BEARD_LIGHT: return "BEARD_LIGHT";
            case FacialHair.BEARD_MAJESTIC: return "BEARD_MAJESTIC";
            case FacialHair.MOUSTACHE_FANCY: return "MOUSTACHE_FANCY";
            case FacialHair.MOUSTACHE_MAGNUM: return "MOUSTACHE_MAGNUM";
        }
    },

    serialName(element: FacialHair): string {
        switch (element) {
            case FacialHair.BLANK: return "Blank";
            case FacialHair.BEARD_MEDIUM: return "BeardMedium";
            case FacialHair.BEARD_LIGHT: return "BeardLight";
            case FacialHair.BEARD_MAJESTIC: return "BeardMajestic";
            case FacialHair.MOUSTACHE_FANCY: return "MoustacheFancy";
            case FacialHair.MOUSTACHE_MAGNUM: return "MoustacheMagnum";
        }
    },

    encoded(element: FacialHair): number {
        switch (element) {
            case FacialHair.BLANK: return 1;
            case FacialHair.BEARD_MEDIUM: return 2;
            case FacialHair.BEARD_LIGHT: return 3;
            case FacialHair.BEARD_MAJESTIC: return 4;
            case FacialHair.MOUSTACHE_FANCY: return 5;
            case FacialHair.MOUSTACHE_MAGNUM: return 6;
        }
    },

    fromSerialName(name: string): FacialHair | null {
        switch (name) {
            case "Blank": return FacialHair.BLANK;
            case "BeardMedium": return FacialHair.BEARD_MEDIUM;
            case "BeardLight": return FacialHair.BEARD_LIGHT;
            case "BeardMajestic": return FacialHair.BEARD_MAJESTIC;
            case "MoustacheFancy": return FacialHair.MOUSTACHE_FANCY;
            case "MoustacheMagnum": return FacialHair.MOUSTACHE_MAGNUM;
            default: return null;
        }
    },

    fromEncoded(encoded: number): FacialHair | null {
        switch (encoded) {
            case 1: return FacialHair.BLANK;
            case 2: return FacialHair.BEARD_MEDIUM;
            case 3: return FacialHair.BEARD_LIGHT;
            case 4: return FacialHair.BEARD_MAJESTIC;
            case 5: return FacialHair.MOUSTACHE_FANCY;
            case 6: return FacialHair.MOUSTACHE_MAGNUM;
            default: return null;
        }
    },
};

export enum FacialHairColor {
    AUBURN,
    BLACK,
    BLONDE,
    BLONDE_GOLDEN,
    BROWN,
    BROWN_DARK,
    PLATINUM,
    RED,
}

export const FacialHairColorCompanion = {
    name(element: FacialHairColor): string {
        switch (element) {
            case FacialHairColor.AUBURN: return "AUBURN";
            case FacialHairColor.BLACK: return "BLACK";
            case FacialHairColor.BLONDE: return "BLONDE";
            case FacialHairColor.BLONDE_GOLDEN: return "BLONDE_GOLDEN";
            case FacialHairColor.BROWN: return "BROWN";
            case FacialHairColor.BROWN_DARK: return "BROWN_DARK";
            case FacialHairColor.PLATINUM: return "PLATINUM";
            case FacialHairColor.RED: return "RED";
        }
    },

    serialName(element: FacialHairColor): string {
        switch (element) {
            case FacialHairColor.AUBURN: return "Auburn";
            case FacialHairColor.BLACK: return "Black";
            case FacialHairColor.BLONDE: return "Blonde";
            case FacialHairColor.BLONDE_GOLDEN: return "BlondeGolden";
            case FacialHairColor.BROWN: return "Brown";
            case FacialHairColor.BROWN_DARK: return "BrownDark";
            case FacialHairColor.PLATINUM: return "Platinum";
            case FacialHairColor.RED: return "Red";
        }
    },

    encoded(element: FacialHairColor): number {
        switch (element) {
            case FacialHairColor.AUBURN: return 1;
            case FacialHairColor.BLACK: return 2;
            case FacialHairColor.BLONDE: return 3;
            case FacialHairColor.BLONDE_GOLDEN: return 4;
            case FacialHairColor.BROWN: return 5;
            case FacialHairColor.BROWN_DARK: return 6;
            case FacialHairColor.PLATINUM: return 7;
            case FacialHairColor.RED: return 8;
        }
    },

    fromSerialName(name: string): FacialHairColor | null {
        switch (name) {
            case "Auburn": return FacialHairColor.AUBURN;
            case "Black": return FacialHairColor.BLACK;
            case "Blonde": return FacialHairColor.BLONDE;
            case "BlondeGolden": return FacialHairColor.BLONDE_GOLDEN;
            case "Brown": return FacialHairColor.BROWN;
            case "BrownDark": return FacialHairColor.BROWN_DARK;
            case "Platinum": return FacialHairColor.PLATINUM;
            case "Red": return FacialHairColor.RED;
            default: return null;
        }
    },

    fromEncoded(encoded: number): FacialHairColor | null {
        switch (encoded) {
            case 1: return FacialHairColor.AUBURN;
            case 2: return FacialHairColor.BLACK;
            case 3: return FacialHairColor.BLONDE;
            case 4: return FacialHairColor.BLONDE_GOLDEN;
            case 5: return FacialHairColor.BROWN;
            case 6: return FacialHairColor.BROWN_DARK;
            case 7: return FacialHairColor.PLATINUM;
            case 8: return FacialHairColor.RED;
            default: return null;
        }
    },
};

export enum Clothes {
    BLAZER_SHIRT,
    BLAZER_SWEATER,
    COLLAR_SWEATER,
    GRAPHIC_SHIRT,
    HOODIE,
    OVERALL,
    SHIRT_CREW_NECK,
    SHIRT_SCOOP_NECK,
    SHIRT_V_NECK,
}

export const ClothesCompanion = {
    name(element: Clothes): string {
        switch (element) {
            case Clothes.BLAZER_SHIRT: return "BLAZER_SHIRT";
            case Clothes.BLAZER_SWEATER: return "BLAZER_SWEATER";
            case Clothes.COLLAR_SWEATER: return "COLLAR_SWEATER";
            case Clothes.GRAPHIC_SHIRT: return "GRAPHIC_SHIRT";
            case Clothes.HOODIE: return "HOODIE";
            case Clothes.OVERALL: return "OVERALL";
            case Clothes.SHIRT_CREW_NECK: return "SHIRT_CREW_NECK";
            case Clothes.SHIRT_SCOOP_NECK: return "SHIRT_SCOOP_NECK";
            case Clothes.SHIRT_V_NECK: return "SHIRT_V_NECK";
        }
    },

    serialName(element: Clothes): string {
        switch (element) {
            case Clothes.BLAZER_SHIRT: return "BlazerShirt";
            case Clothes.BLAZER_SWEATER: return "BlazerSweater";
            case Clothes.COLLAR_SWEATER: return "CollarSweater";
            case Clothes.GRAPHIC_SHIRT: return "GraphicShirt";
            case Clothes.HOODIE: return "Hoodie";
            case Clothes.OVERALL: return "Overall";
            case Clothes.SHIRT_CREW_NECK: return "ShirtCrewNeck";
            case Clothes.SHIRT_SCOOP_NECK: return "ShirtScoopNeck";
            case Clothes.SHIRT_V_NECK: return "ShirtVNeck";
        }
    },

    encoded(element: Clothes): number {
        switch (element) {
            case Clothes.BLAZER_SHIRT: return 1;
            case Clothes.BLAZER_SWEATER: return 2;
            case Clothes.COLLAR_SWEATER: return 3;
            case Clothes.GRAPHIC_SHIRT: return 4;
            case Clothes.HOODIE: return 5;
            case Clothes.OVERALL: return 6;
            case Clothes.SHIRT_CREW_NECK: return 7;
            case Clothes.SHIRT_SCOOP_NECK: return 8;
            case Clothes.SHIRT_V_NECK: return 9;
        }
    },

    fromSerialName(name: string): Clothes | null {
        switch (name) {
            case "BlazerShirt": return Clothes.BLAZER_SHIRT;
            case "BlazerSweater": return Clothes.BLAZER_SWEATER;
            case "CollarSweater": return Clothes.COLLAR_SWEATER;
            case "GraphicShirt": return Clothes.GRAPHIC_SHIRT;
            case "Hoodie": return Clothes.HOODIE;
            case "Overall": return Clothes.OVERALL;
            case "ShirtCrewNeck": return Clothes.SHIRT_CREW_NECK;
            case "ShirtScoopNeck": return Clothes.SHIRT_SCOOP_NECK;
            case "ShirtVNeck": return Clothes.SHIRT_V_NECK;
            default: return null;
        }
    },

    fromEncoded(encoded: number): Clothes | null {
        switch (encoded) {
            case 1: return Clothes.BLAZER_SHIRT;
            case 2: return Clothes.BLAZER_SWEATER;
            case 3: return Clothes.COLLAR_SWEATER;
            case 4: return Clothes.GRAPHIC_SHIRT;
            case 5: return Clothes.HOODIE;
            case 6: return Clothes.OVERALL;
            case 7: return Clothes.SHIRT_CREW_NECK;
            case 8: return Clothes.SHIRT_SCOOP_NECK;
            case 9: return Clothes.SHIRT_V_NECK;
            default: return null;
        }
    },
};

export enum ColorFabric {
    BLACK,
    BLUE01,
    BLUE02,
    BLUE03,
    GRAY01,
    GRAY02,
    HEATHER,
    PASTEL_BLUE,
    PASTEL_GREEN,
    PASTEL_ORANGE,
    PASTEL_RED,
    PASTEL_YELLOW,
    PINK,
    RED,
    WHITE,
}

export const ColorFabricCompanion = {
    name(element: ColorFabric): string {
        switch (element) {
            case ColorFabric.BLACK: return "BLACK";
            case ColorFabric.BLUE01: return "BLUE01";
            case ColorFabric.BLUE02: return "BLUE02";
            case ColorFabric.BLUE03: return "BLUE03";
            case ColorFabric.GRAY01: return "GRAY01";
            case ColorFabric.GRAY02: return "GRAY02";
            case ColorFabric.HEATHER: return "HEATHER";
            case ColorFabric.PASTEL_BLUE: return "PASTEL_BLUE";
            case ColorFabric.PASTEL_GREEN: return "PASTEL_GREEN";
            case ColorFabric.PASTEL_ORANGE: return "PASTEL_ORANGE";
            case ColorFabric.PASTEL_RED: return "PASTEL_RED";
            case ColorFabric.PASTEL_YELLOW: return "PASTEL_YELLOW";
            case ColorFabric.PINK: return "PINK";
            case ColorFabric.RED: return "RED";
            case ColorFabric.WHITE: return "WHITE";
        }
    },

    serialName(element: ColorFabric): string {
        switch (element) {
            case ColorFabric.BLACK: return "Black";
            case ColorFabric.BLUE01: return "Blue01";
            case ColorFabric.BLUE02: return "Blue02";
            case ColorFabric.BLUE03: return "Blue03";
            case ColorFabric.GRAY01: return "Gray01";
            case ColorFabric.GRAY02: return "Gray02";
            case ColorFabric.HEATHER: return "Heather";
            case ColorFabric.PASTEL_BLUE: return "PastelBlue";
            case ColorFabric.PASTEL_GREEN: return "PastelGreen";
            case ColorFabric.PASTEL_ORANGE: return "PastelOrange";
            case ColorFabric.PASTEL_RED: return "PastelRed";
            case ColorFabric.PASTEL_YELLOW: return "PastelYellow";
            case ColorFabric.PINK: return "Pink";
            case ColorFabric.RED: return "Red";
            case ColorFabric.WHITE: return "White";
        }
    },

    encoded(element: ColorFabric): number {
        switch (element) {
            case ColorFabric.BLACK: return 1;
            case ColorFabric.BLUE01: return 2;
            case ColorFabric.BLUE02: return 3;
            case ColorFabric.BLUE03: return 4;
            case ColorFabric.GRAY01: return 5;
            case ColorFabric.GRAY02: return 6;
            case ColorFabric.HEATHER: return 7;
            case ColorFabric.PASTEL_BLUE: return 8;
            case ColorFabric.PASTEL_GREEN: return 9;
            case ColorFabric.PASTEL_ORANGE: return 10;
            case ColorFabric.PASTEL_RED: return 11;
            case ColorFabric.PASTEL_YELLOW: return 12;
            case ColorFabric.PINK: return 13;
            case ColorFabric.RED: return 14;
            case ColorFabric.WHITE: return 15;
        }
    },

    fromSerialName(name: string): ColorFabric | null {
        switch (name) {
            case "Black": return ColorFabric.BLACK;
            case "Blue01": return ColorFabric.BLUE01;
            case "Blue02": return ColorFabric.BLUE02;
            case "Blue03": return ColorFabric.BLUE03;
            case "Gray01": return ColorFabric.GRAY01;
            case "Gray02": return ColorFabric.GRAY02;
            case "Heather": return ColorFabric.HEATHER;
            case "PastelBlue": return ColorFabric.PASTEL_BLUE;
            case "PastelGreen": return ColorFabric.PASTEL_GREEN;
            case "PastelOrange": return ColorFabric.PASTEL_ORANGE;
            case "PastelRed": return ColorFabric.PASTEL_RED;
            case "PastelYellow": return ColorFabric.PASTEL_YELLOW;
            case "Pink": return ColorFabric.PINK;
            case "Red": return ColorFabric.RED;
            case "White": return ColorFabric.WHITE;
            default: return null;
        }
    },

    fromEncoded(encoded: number): ColorFabric | null {
        switch (encoded) {
            case 1: return ColorFabric.BLACK;
            case 2: return ColorFabric.BLUE01;
            case 3: return ColorFabric.BLUE02;
            case 4: return ColorFabric.BLUE03;
            case 5: return ColorFabric.GRAY01;
            case 6: return ColorFabric.GRAY02;
            case 7: return ColorFabric.HEATHER;
            case 8: return ColorFabric.PASTEL_BLUE;
            case 9: return ColorFabric.PASTEL_GREEN;
            case 10: return ColorFabric.PASTEL_ORANGE;
            case 11: return ColorFabric.PASTEL_RED;
            case 12: return ColorFabric.PASTEL_YELLOW;
            case 13: return ColorFabric.PINK;
            case 14: return ColorFabric.RED;
            case 15: return ColorFabric.WHITE;
            default: return null;
        }
    },
};

export enum Eyes {
    CLOSE,
    CRY,
    DEFAULT,
    DIZZY,
    EYE_ROLL,
    HAPPY,
    HEARTS,
    SIDE,
    SQUINT,
    SURPRISED,
    WINK,
    WINK_WACKY,
}

export const EyesCompanion = {
    name(element: Eyes): string {
        switch (element) {
            case Eyes.CLOSE: return "CLOSE";
            case Eyes.CRY: return "CRY";
            case Eyes.DEFAULT: return "DEFAULT";
            case Eyes.DIZZY: return "DIZZY";
            case Eyes.EYE_ROLL: return "EYE_ROLL";
            case Eyes.HAPPY: return "HAPPY";
            case Eyes.HEARTS: return "HEARTS";
            case Eyes.SIDE: return "SIDE";
            case Eyes.SQUINT: return "SQUINT";
            case Eyes.SURPRISED: return "SURPRISED";
            case Eyes.WINK: return "WINK";
            case Eyes.WINK_WACKY: return "WINK_WACKY";
        }
    },

    serialName(element: Eyes): string {
        switch (element) {
            case Eyes.CLOSE: return "Close";
            case Eyes.CRY: return "Cry";
            case Eyes.DEFAULT: return "Default";
            case Eyes.DIZZY: return "Dizzy";
            case Eyes.EYE_ROLL: return "EyeRoll";
            case Eyes.HAPPY: return "Happy";
            case Eyes.HEARTS: return "Hearts";
            case Eyes.SIDE: return "Side";
            case Eyes.SQUINT: return "Squint";
            case Eyes.SURPRISED: return "Surprised";
            case Eyes.WINK: return "Wink";
            case Eyes.WINK_WACKY: return "WinkWacky";
        }
    },

    encoded(element: Eyes): number {
        switch (element) {
            case Eyes.CLOSE: return 1;
            case Eyes.CRY: return 2;
            case Eyes.DEFAULT: return 3;
            case Eyes.DIZZY: return 4;
            case Eyes.EYE_ROLL: return 5;
            case Eyes.HAPPY: return 6;
            case Eyes.HEARTS: return 7;
            case Eyes.SIDE: return 8;
            case Eyes.SQUINT: return 9;
            case Eyes.SURPRISED: return 10;
            case Eyes.WINK: return 11;
            case Eyes.WINK_WACKY: return 12;
        }
    },

    fromSerialName(name: string): Eyes | null {
        switch (name) {
            case "Close": return Eyes.CLOSE;
            case "Cry": return Eyes.CRY;
            case "Default": return Eyes.DEFAULT;
            case "Dizzy": return Eyes.DIZZY;
            case "EyeRoll": return Eyes.EYE_ROLL;
            case "Happy": return Eyes.HAPPY;
            case "Hearts": return Eyes.HEARTS;
            case "Side": return Eyes.SIDE;
            case "Squint": return Eyes.SQUINT;
            case "Surprised": return Eyes.SURPRISED;
            case "Wink": return Eyes.WINK;
            case "WinkWacky": return Eyes.WINK_WACKY;
            default: return null;
        }
    },

    fromEncoded(encoded: number): Eyes | null {
        switch (encoded) {
            case 1: return Eyes.CLOSE;
            case 2: return Eyes.CRY;
            case 3: return Eyes.DEFAULT;
            case 4: return Eyes.DIZZY;
            case 5: return Eyes.EYE_ROLL;
            case 6: return Eyes.HAPPY;
            case 7: return Eyes.HEARTS;
            case 8: return Eyes.SIDE;
            case 9: return Eyes.SQUINT;
            case 10: return Eyes.SURPRISED;
            case 11: return Eyes.WINK;
            case 12: return Eyes.WINK_WACKY;
            default: return null;
        }
    },
};

export enum Eyebrows {
    ANGRY,
    ANGRY_NATURAL,
    DEFAULT,
    DEFAULT_NATURAL,
    FLAT_NATURAL,
    FROWN_NATURAL,
    RAISED_EXCITED,
    RAISED_EXCITED_NATURAL,
    SAD_CONCERNED,
    SAD_CONCERNED_NATURAL,
    UNIBROW_NATURAL,
    UP_DOWN,
    UP_DOWN_NATURAL,
}

export const EyebrowsCompanion = {
    name(element: Eyebrows): string {
        switch (element) {
            case Eyebrows.ANGRY: return "ANGRY";
            case Eyebrows.ANGRY_NATURAL: return "ANGRY_NATURAL";
            case Eyebrows.DEFAULT: return "DEFAULT";
            case Eyebrows.DEFAULT_NATURAL: return "DEFAULT_NATURAL";
            case Eyebrows.FLAT_NATURAL: return "FLAT_NATURAL";
            case Eyebrows.FROWN_NATURAL: return "FROWN_NATURAL";
            case Eyebrows.RAISED_EXCITED: return "RAISED_EXCITED";
            case Eyebrows.RAISED_EXCITED_NATURAL: return "RAISED_EXCITED_NATURAL";
            case Eyebrows.SAD_CONCERNED: return "SAD_CONCERNED";
            case Eyebrows.SAD_CONCERNED_NATURAL: return "SAD_CONCERNED_NATURAL";
            case Eyebrows.UNIBROW_NATURAL: return "UNIBROW_NATURAL";
            case Eyebrows.UP_DOWN: return "UP_DOWN";
            case Eyebrows.UP_DOWN_NATURAL: return "UP_DOWN_NATURAL";
        }
    },

    serialName(element: Eyebrows): string {
        switch (element) {
            case Eyebrows.ANGRY: return "Angry";
            case Eyebrows.ANGRY_NATURAL: return "AngryNatural";
            case Eyebrows.DEFAULT: return "Default";
            case Eyebrows.DEFAULT_NATURAL: return "DefaultNatural";
            case Eyebrows.FLAT_NATURAL: return "FlatNatural";
            case Eyebrows.FROWN_NATURAL: return "FrownNatural";
            case Eyebrows.RAISED_EXCITED: return "RaisedExcited";
            case Eyebrows.RAISED_EXCITED_NATURAL: return "RaisedExcitedNatural";
            case Eyebrows.SAD_CONCERNED: return "SadConcerned";
            case Eyebrows.SAD_CONCERNED_NATURAL: return "SadConcernedNatural";
            case Eyebrows.UNIBROW_NATURAL: return "UnibrowNatural";
            case Eyebrows.UP_DOWN: return "UpDown";
            case Eyebrows.UP_DOWN_NATURAL: return "UpDownNatural";
        }
    },

    encoded(element: Eyebrows): number {
        switch (element) {
            case Eyebrows.ANGRY: return 1;
            case Eyebrows.ANGRY_NATURAL: return 2;
            case Eyebrows.DEFAULT: return 3;
            case Eyebrows.DEFAULT_NATURAL: return 4;
            case Eyebrows.FLAT_NATURAL: return 5;
            case Eyebrows.FROWN_NATURAL: return 6;
            case Eyebrows.RAISED_EXCITED: return 7;
            case Eyebrows.RAISED_EXCITED_NATURAL: return 8;
            case Eyebrows.SAD_CONCERNED: return 9;
            case Eyebrows.SAD_CONCERNED_NATURAL: return 10;
            case Eyebrows.UNIBROW_NATURAL: return 11;
            case Eyebrows.UP_DOWN: return 12;
            case Eyebrows.UP_DOWN_NATURAL: return 13;
        }
    },

    fromSerialName(name: string): Eyebrows | null {
        switch (name) {
            case "Angry": return Eyebrows.ANGRY;
            case "AngryNatural": return Eyebrows.ANGRY_NATURAL;
            case "Default": return Eyebrows.DEFAULT;
            case "DefaultNatural": return Eyebrows.DEFAULT_NATURAL;
            case "FlatNatural": return Eyebrows.FLAT_NATURAL;
            case "FrownNatural": return Eyebrows.FROWN_NATURAL;
            case "RaisedExcited": return Eyebrows.RAISED_EXCITED;
            case "RaisedExcitedNatural": return Eyebrows.RAISED_EXCITED_NATURAL;
            case "SadConcerned": return Eyebrows.SAD_CONCERNED;
            case "SadConcernedNatural": return Eyebrows.SAD_CONCERNED_NATURAL;
            case "UnibrowNatural": return Eyebrows.UNIBROW_NATURAL;
            case "UpDown": return Eyebrows.UP_DOWN;
            case "UpDownNatural": return Eyebrows.UP_DOWN_NATURAL;
            default: return null;
        }
    },

    fromEncoded(encoded: number): Eyebrows | null {
        switch (encoded) {
            case 1: return Eyebrows.ANGRY;
            case 2: return Eyebrows.ANGRY_NATURAL;
            case 3: return Eyebrows.DEFAULT;
            case 4: return Eyebrows.DEFAULT_NATURAL;
            case 5: return Eyebrows.FLAT_NATURAL;
            case 6: return Eyebrows.FROWN_NATURAL;
            case 7: return Eyebrows.RAISED_EXCITED;
            case 8: return Eyebrows.RAISED_EXCITED_NATURAL;
            case 9: return Eyebrows.SAD_CONCERNED;
            case 10: return Eyebrows.SAD_CONCERNED_NATURAL;
            case 11: return Eyebrows.UNIBROW_NATURAL;
            case 12: return Eyebrows.UP_DOWN;
            case 13: return Eyebrows.UP_DOWN_NATURAL;
            default: return null;
        }
    },
};

export enum MouthTypes {
    CONCERNED,
    DEFAULT,
    DISBELIEF,
    EATING,
    GRIMACE,
    SAD,
    SCREAM_OPEN,
    SERIOUS,
    SMILE,
    TONGUE,
    TWINKLE,
    VOMIT,
}

export const MouthTypesCompanion = {
    name(element: MouthTypes): string {
        switch (element) {
            case MouthTypes.CONCERNED: return "CONCERNED";
            case MouthTypes.DEFAULT: return "DEFAULT";
            case MouthTypes.DISBELIEF: return "DISBELIEF";
            case MouthTypes.EATING: return "EATING";
            case MouthTypes.GRIMACE: return "GRIMACE";
            case MouthTypes.SAD: return "SAD";
            case MouthTypes.SCREAM_OPEN: return "SCREAM_OPEN";
            case MouthTypes.SERIOUS: return "SERIOUS";
            case MouthTypes.SMILE: return "SMILE";
            case MouthTypes.TONGUE: return "TONGUE";
            case MouthTypes.TWINKLE: return "TWINKLE";
            case MouthTypes.VOMIT: return "VOMIT";
        }
    },

    serialName(element: MouthTypes): string {
        switch (element) {
            case MouthTypes.CONCERNED: return "Concerned";
            case MouthTypes.DEFAULT: return "Default";
            case MouthTypes.DISBELIEF: return "Disbelief";
            case MouthTypes.EATING: return "Eating";
            case MouthTypes.GRIMACE: return "Grimace";
            case MouthTypes.SAD: return "Sad";
            case MouthTypes.SCREAM_OPEN: return "ScreamOpen";
            case MouthTypes.SERIOUS: return "Serious";
            case MouthTypes.SMILE: return "Smile";
            case MouthTypes.TONGUE: return "Tongue";
            case MouthTypes.TWINKLE: return "Twinkle";
            case MouthTypes.VOMIT: return "Vomit";
        }
    },

    encoded(element: MouthTypes): number {
        switch (element) {
            case MouthTypes.CONCERNED: return 1;
            case MouthTypes.DEFAULT: return 2;
            case MouthTypes.DISBELIEF: return 3;
            case MouthTypes.EATING: return 4;
            case MouthTypes.GRIMACE: return 5;
            case MouthTypes.SAD: return 6;
            case MouthTypes.SCREAM_OPEN: return 7;
            case MouthTypes.SERIOUS: return 8;
            case MouthTypes.SMILE: return 9;
            case MouthTypes.TONGUE: return 10;
            case MouthTypes.TWINKLE: return 11;
            case MouthTypes.VOMIT: return 12;
        }
    },

    fromSerialName(name: string): MouthTypes | null {
        switch (name) {
            case "Concerned": return MouthTypes.CONCERNED;
            case "Default": return MouthTypes.DEFAULT;
            case "Disbelief": return MouthTypes.DISBELIEF;
            case "Eating": return MouthTypes.EATING;
            case "Grimace": return MouthTypes.GRIMACE;
            case "Sad": return MouthTypes.SAD;
            case "ScreamOpen": return MouthTypes.SCREAM_OPEN;
            case "Serious": return MouthTypes.SERIOUS;
            case "Smile": return MouthTypes.SMILE;
            case "Tongue": return MouthTypes.TONGUE;
            case "Twinkle": return MouthTypes.TWINKLE;
            case "Vomit": return MouthTypes.VOMIT;
            default: return null;
        }
    },

    fromEncoded(encoded: number): MouthTypes | null {
        switch (encoded) {
            case 1: return MouthTypes.CONCERNED;
            case 2: return MouthTypes.DEFAULT;
            case 3: return MouthTypes.DISBELIEF;
            case 4: return MouthTypes.EATING;
            case 5: return MouthTypes.GRIMACE;
            case 6: return MouthTypes.SAD;
            case 7: return MouthTypes.SCREAM_OPEN;
            case 8: return MouthTypes.SERIOUS;
            case 9: return MouthTypes.SMILE;
            case 10: return MouthTypes.TONGUE;
            case 11: return MouthTypes.TWINKLE;
            case 12: return MouthTypes.VOMIT;
            default: return null;
        }
    },
};

export enum SkinColors {
    TANNED,
    YELLOW,
    PALE,
    LIGHT,
    BROWN,
    DARK_BROWN,
    BLACK,
}

export const SkinColorsCompanion = {
    name(element: SkinColors): string {
        switch (element) {
            case SkinColors.TANNED: return "TANNED";
            case SkinColors.YELLOW: return "YELLOW";
            case SkinColors.PALE: return "PALE";
            case SkinColors.LIGHT: return "LIGHT";
            case SkinColors.BROWN: return "BROWN";
            case SkinColors.DARK_BROWN: return "DARK_BROWN";
            case SkinColors.BLACK: return "BLACK";
        }
    },

    serialName(element: SkinColors): string {
        switch (element) {
            case SkinColors.TANNED: return "Tanned";
            case SkinColors.YELLOW: return "Yellow";
            case SkinColors.PALE: return "Pale";
            case SkinColors.LIGHT: return "Light";
            case SkinColors.BROWN: return "Brown";
            case SkinColors.DARK_BROWN: return "DarkBrown";
            case SkinColors.BLACK: return "Black";
        }
    },

    encoded(element: SkinColors): number {
        switch (element) {
            case SkinColors.TANNED: return 1;
            case SkinColors.YELLOW: return 2;
            case SkinColors.PALE: return 3;
            case SkinColors.LIGHT: return 4;
            case SkinColors.BROWN: return 5;
            case SkinColors.DARK_BROWN: return 6;
            case SkinColors.BLACK: return 7;
        }
    },

    fromSerialName(name: string): SkinColors | null {
        switch (name) {
            case "Tanned": return SkinColors.TANNED;
            case "Yellow": return SkinColors.YELLOW;
            case "Pale": return SkinColors.PALE;
            case "Light": return SkinColors.LIGHT;
            case "Brown": return SkinColors.BROWN;
            case "DarkBrown": return SkinColors.DARK_BROWN;
            case "Black": return SkinColors.BLACK;
            default: return null;
        }
    },

    fromEncoded(encoded: number): SkinColors | null {
        switch (encoded) {
            case 1: return SkinColors.TANNED;
            case 2: return SkinColors.YELLOW;
            case 3: return SkinColors.PALE;
            case 4: return SkinColors.LIGHT;
            case 5: return SkinColors.BROWN;
            case 6: return SkinColors.DARK_BROWN;
            case 7: return SkinColors.BLACK;
            default: return null;
        }
    },
};

export enum ClothesGraphic {
    BAT,
    CUMBIA,
    DEER,
    DIAMOND,
    HOLA,
    PIZZA,
    RESIST,
    SELENA,
    BEAR,
    SKULL_OUTLINE,
    SKULL,
    ESPIE,
    ESCIENCELOGO,
    TEETH,
}

export const ClothesGraphicCompanion = {
    name(element: ClothesGraphic): string {
        switch (element) {
            case ClothesGraphic.BAT: return "BAT";
            case ClothesGraphic.CUMBIA: return "CUMBIA";
            case ClothesGraphic.DEER: return "DEER";
            case ClothesGraphic.DIAMOND: return "DIAMOND";
            case ClothesGraphic.HOLA: return "HOLA";
            case ClothesGraphic.PIZZA: return "PIZZA";
            case ClothesGraphic.RESIST: return "RESIST";
            case ClothesGraphic.SELENA: return "SELENA";
            case ClothesGraphic.BEAR: return "BEAR";
            case ClothesGraphic.SKULL_OUTLINE: return "SKULL_OUTLINE";
            case ClothesGraphic.SKULL: return "SKULL";
            case ClothesGraphic.ESPIE: return "ESPIE";
            case ClothesGraphic.ESCIENCELOGO: return "ESCIENCELOGO";
            case ClothesGraphic.TEETH: return "TEETH";
        }
    },

    serialName(element: ClothesGraphic): string {
        switch (element) {
            case ClothesGraphic.BAT: return "Bat";
            case ClothesGraphic.CUMBIA: return "Cumbia";
            case ClothesGraphic.DEER: return "Deer";
            case ClothesGraphic.DIAMOND: return "Diamond";
            case ClothesGraphic.HOLA: return "Hola";
            case ClothesGraphic.PIZZA: return "Pizza";
            case ClothesGraphic.RESIST: return "Resist";
            case ClothesGraphic.SELENA: return "Selena";
            case ClothesGraphic.BEAR: return "Bear";
            case ClothesGraphic.SKULL_OUTLINE: return "SkullOutline";
            case ClothesGraphic.SKULL: return "Skull";
            case ClothesGraphic.ESPIE: return "Espie";
            case ClothesGraphic.ESCIENCELOGO: return "EScienceLogo";
            case ClothesGraphic.TEETH: return "Teeth";
        }
    },

    encoded(element: ClothesGraphic): number {
        switch (element) {
            case ClothesGraphic.BAT: return 1;
            case ClothesGraphic.CUMBIA: return 2;
            case ClothesGraphic.DEER: return 3;
            case ClothesGraphic.DIAMOND: return 4;
            case ClothesGraphic.HOLA: return 5;
            case ClothesGraphic.PIZZA: return 6;
            case ClothesGraphic.RESIST: return 7;
            case ClothesGraphic.SELENA: return 8;
            case ClothesGraphic.BEAR: return 9;
            case ClothesGraphic.SKULL_OUTLINE: return 10;
            case ClothesGraphic.SKULL: return 11;
            case ClothesGraphic.ESPIE: return 12;
            case ClothesGraphic.ESCIENCELOGO: return 13;
            case ClothesGraphic.TEETH: return 14;
        }
    },

    fromSerialName(name: string): ClothesGraphic | null {
        switch (name) {
            case "Bat": return ClothesGraphic.BAT;
            case "Cumbia": return ClothesGraphic.CUMBIA;
            case "Deer": return ClothesGraphic.DEER;
            case "Diamond": return ClothesGraphic.DIAMOND;
            case "Hola": return ClothesGraphic.HOLA;
            case "Pizza": return ClothesGraphic.PIZZA;
            case "Resist": return ClothesGraphic.RESIST;
            case "Selena": return ClothesGraphic.SELENA;
            case "Bear": return ClothesGraphic.BEAR;
            case "SkullOutline": return ClothesGraphic.SKULL_OUTLINE;
            case "Skull": return ClothesGraphic.SKULL;
            case "Espie": return ClothesGraphic.ESPIE;
            case "EScienceLogo": return ClothesGraphic.ESCIENCELOGO;
            case "Teeth": return ClothesGraphic.TEETH;
            default: return null;
        }
    },

    fromEncoded(encoded: number): ClothesGraphic | null {
        switch (encoded) {
            case 1: return ClothesGraphic.BAT;
            case 2: return ClothesGraphic.CUMBIA;
            case 3: return ClothesGraphic.DEER;
            case 4: return ClothesGraphic.DIAMOND;
            case 5: return ClothesGraphic.HOLA;
            case 6: return ClothesGraphic.PIZZA;
            case 7: return ClothesGraphic.RESIST;
            case 8: return ClothesGraphic.SELENA;
            case 9: return ClothesGraphic.BEAR;
            case 10: return ClothesGraphic.SKULL_OUTLINE;
            case 11: return ClothesGraphic.SKULL;
            case 12: return ClothesGraphic.ESPIE;
            case 13: return ClothesGraphic.ESCIENCELOGO;
            case 14: return ClothesGraphic.TEETH;
            default: return null;
        }
    },
};

export class Avatar implements UBinaryType {
    buffer: BufferAndOffset;
    constructor(buffer: BufferAndOffset) {
        this.buffer = buffer;
    }

    get top(): Top {
        return TopCompanion.fromEncoded(this.buffer.buf.getInt16(0 + this.buffer.offset))!;
    }
    set top(value: Top) {
        this.buffer.buf.setInt16(0 + this.buffer.offset!, TopCompanion.encoded(value));
    }


    get topAccessory(): TopAccessory {
        return TopAccessoryCompanion.fromEncoded(this.buffer.buf.getInt16(2 + this.buffer.offset))!;
    }
    set topAccessory(value: TopAccessory) {
        this.buffer.buf.setInt16(2 + this.buffer.offset!, TopAccessoryCompanion.encoded(value));
    }


    get hairColor(): HairColor {
        return HairColorCompanion.fromEncoded(this.buffer.buf.getInt16(4 + this.buffer.offset))!;
    }
    set hairColor(value: HairColor) {
        this.buffer.buf.setInt16(4 + this.buffer.offset!, HairColorCompanion.encoded(value));
    }


    get facialHair(): FacialHair {
        return FacialHairCompanion.fromEncoded(this.buffer.buf.getInt16(6 + this.buffer.offset))!;
    }
    set facialHair(value: FacialHair) {
        this.buffer.buf.setInt16(6 + this.buffer.offset!, FacialHairCompanion.encoded(value));
    }


    get facialHairColor(): FacialHairColor {
        return FacialHairColorCompanion.fromEncoded(this.buffer.buf.getInt16(8 + this.buffer.offset))!;
    }
    set facialHairColor(value: FacialHairColor) {
        this.buffer.buf.setInt16(8 + this.buffer.offset!, FacialHairColorCompanion.encoded(value));
    }


    get clothes(): Clothes {
        return ClothesCompanion.fromEncoded(this.buffer.buf.getInt16(10 + this.buffer.offset))!;
    }
    set clothes(value: Clothes) {
        this.buffer.buf.setInt16(10 + this.buffer.offset!, ClothesCompanion.encoded(value));
    }


    get colorFabric(): ColorFabric {
        return ColorFabricCompanion.fromEncoded(this.buffer.buf.getInt16(12 + this.buffer.offset))!;
    }
    set colorFabric(value: ColorFabric) {
        this.buffer.buf.setInt16(12 + this.buffer.offset!, ColorFabricCompanion.encoded(value));
    }


    get eyes(): Eyes {
        return EyesCompanion.fromEncoded(this.buffer.buf.getInt16(14 + this.buffer.offset))!;
    }
    set eyes(value: Eyes) {
        this.buffer.buf.setInt16(14 + this.buffer.offset!, EyesCompanion.encoded(value));
    }


    get eyebrows(): Eyebrows {
        return EyebrowsCompanion.fromEncoded(this.buffer.buf.getInt16(16 + this.buffer.offset))!;
    }
    set eyebrows(value: Eyebrows) {
        this.buffer.buf.setInt16(16 + this.buffer.offset!, EyebrowsCompanion.encoded(value));
    }


    get mouthTypes(): MouthTypes {
        return MouthTypesCompanion.fromEncoded(this.buffer.buf.getInt16(18 + this.buffer.offset))!;
    }
    set mouthTypes(value: MouthTypes) {
        this.buffer.buf.setInt16(18 + this.buffer.offset!, MouthTypesCompanion.encoded(value));
    }


    get skinColors(): SkinColors {
        return SkinColorsCompanion.fromEncoded(this.buffer.buf.getInt16(20 + this.buffer.offset))!;
    }
    set skinColors(value: SkinColors) {
        this.buffer.buf.setInt16(20 + this.buffer.offset!, SkinColorsCompanion.encoded(value));
    }


    get clothesGraphic(): ClothesGraphic {
        return ClothesGraphicCompanion.fromEncoded(this.buffer.buf.getInt16(22 + this.buffer.offset))!;
    }
    set clothesGraphic(value: ClothesGraphic) {
        this.buffer.buf.setInt16(22 + this.buffer.offset!, ClothesGraphicCompanion.encoded(value));
    }


    get hatColor(): HatColor {
        return HatColorCompanion.fromEncoded(this.buffer.buf.getInt16(24 + this.buffer.offset))!;
    }
    set hatColor(value: HatColor) {
        this.buffer.buf.setInt16(24 + this.buffer.offset!, HatColorCompanion.encoded(value));
    }


    encodeToJson() {
        return {
            top: this.top != null ? TopCompanion.encoded(this.top) : null,
            topAccessory: this.topAccessory != null ? TopAccessoryCompanion.encoded(this.topAccessory) : null,
            hairColor: this.hairColor != null ? HairColorCompanion.encoded(this.hairColor) : null,
            facialHair: this.facialHair != null ? FacialHairCompanion.encoded(this.facialHair) : null,
            facialHairColor: this.facialHairColor != null ? FacialHairColorCompanion.encoded(this.facialHairColor) : null,
            clothes: this.clothes != null ? ClothesCompanion.encoded(this.clothes) : null,
            colorFabric: this.colorFabric != null ? ColorFabricCompanion.encoded(this.colorFabric) : null,
            eyes: this.eyes != null ? EyesCompanion.encoded(this.eyes) : null,
            eyebrows: this.eyebrows != null ? EyebrowsCompanion.encoded(this.eyebrows) : null,
            mouthTypes: this.mouthTypes != null ? MouthTypesCompanion.encoded(this.mouthTypes) : null,
            skinColors: this.skinColors != null ? SkinColorsCompanion.encoded(this.skinColors) : null,
            clothesGraphic: this.clothesGraphic != null ? ClothesGraphicCompanion.encoded(this.clothesGraphic) : null,
            hatColor: this.hatColor != null ? HatColorCompanion.encoded(this.hatColor) : null,
        };
    }

    static create(
        allocator: BinaryAllocator,
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
        const result = allocator.allocate(AvatarCompanion);
        result.top = top;
        result.topAccessory = topAccessory;
        result.hairColor = hairColor;
        result.facialHair = facialHair;
        result.facialHairColor = facialHairColor;
        result.clothes = clothes;
        result.colorFabric = colorFabric;
        result.eyes = eyes;
        result.eyebrows = eyebrows;
        result.mouthTypes = mouthTypes;
        result.skinColors = skinColors;
        result.clothesGraphic = clothesGraphic;
        result.hatColor = hatColor;
        return result;
    }
}
export const AvatarCompanion: BinaryTypeCompanion<Avatar> = {
    size: 26,
    decodeFromJson: (allocator, element) => {
        if (typeof element !== "object" || element === null) {
            throw "Expected an object but found an: " + element;
        }
        let top: Top | null = null;
        {
            const value = element['top'];
            if (typeof value !== 'string') throw "Expected 'top' to be a string";
            top = TopCompanion.fromSerialName(value);
            if (top === null) throw "Did not expect 'top' to be null!";
        }
        let topAccessory: TopAccessory | null = null;
        {
            const value = element['topAccessory'];
            if (typeof value !== 'string') throw "Expected 'topAccessory' to be a string";
            topAccessory = TopAccessoryCompanion.fromSerialName(value);
            if (topAccessory === null) throw "Did not expect 'topAccessory' to be null!";
        }
        let hairColor: HairColor | null = null;
        {
            const value = element['hairColor'];
            if (typeof value !== 'string') throw "Expected 'hairColor' to be a string";
            hairColor = HairColorCompanion.fromSerialName(value);
            if (hairColor === null) throw "Did not expect 'hairColor' to be null!";
        }
        let facialHair: FacialHair | null = null;
        {
            const value = element['facialHair'];
            if (typeof value !== 'string') throw "Expected 'facialHair' to be a string";
            facialHair = FacialHairCompanion.fromSerialName(value);
            if (facialHair === null) throw "Did not expect 'facialHair' to be null!";
        }
        let facialHairColor: FacialHairColor | null = null;
        {
            const value = element['facialHairColor'];
            if (typeof value !== 'string') throw "Expected 'facialHairColor' to be a string";
            facialHairColor = FacialHairColorCompanion.fromSerialName(value);
            if (facialHairColor === null) throw "Did not expect 'facialHairColor' to be null!";
        }
        let clothes: Clothes | null = null;
        {
            const value = element['clothes'];
            if (typeof value !== 'string') throw "Expected 'clothes' to be a string";
            clothes = ClothesCompanion.fromSerialName(value);
            if (clothes === null) throw "Did not expect 'clothes' to be null!";
        }
        let colorFabric: ColorFabric | null = null;
        {
            const value = element['colorFabric'];
            if (typeof value !== 'string') throw "Expected 'colorFabric' to be a string";
            colorFabric = ColorFabricCompanion.fromSerialName(value);
            if (colorFabric === null) throw "Did not expect 'colorFabric' to be null!";
        }
        let eyes: Eyes | null = null;
        {
            const value = element['eyes'];
            if (typeof value !== 'string') throw "Expected 'eyes' to be a string";
            eyes = EyesCompanion.fromSerialName(value);
            if (eyes === null) throw "Did not expect 'eyes' to be null!";
        }
        let eyebrows: Eyebrows | null = null;
        {
            const value = element['eyebrows'];
            if (typeof value !== 'string') throw "Expected 'eyebrows' to be a string";
            eyebrows = EyebrowsCompanion.fromSerialName(value);
            if (eyebrows === null) throw "Did not expect 'eyebrows' to be null!";
        }
        let mouthTypes: MouthTypes | null = null;
        {
            const value = element['mouthTypes'];
            if (typeof value !== 'string') throw "Expected 'mouthTypes' to be a string";
            mouthTypes = MouthTypesCompanion.fromSerialName(value);
            if (mouthTypes === null) throw "Did not expect 'mouthTypes' to be null!";
        }
        let skinColors: SkinColors | null = null;
        {
            const value = element['skinColors'];
            if (typeof value !== 'string') throw "Expected 'skinColors' to be a string";
            skinColors = SkinColorsCompanion.fromSerialName(value);
            if (skinColors === null) throw "Did not expect 'skinColors' to be null!";
        }
        let clothesGraphic: ClothesGraphic | null = null;
        {
            const value = element['clothesGraphic'];
            if (typeof value !== 'string') throw "Expected 'clothesGraphic' to be a string";
            clothesGraphic = ClothesGraphicCompanion.fromSerialName(value);
            if (clothesGraphic === null) throw "Did not expect 'clothesGraphic' to be null!";
        }
        let hatColor: HatColor | null = null;
        {
            const value = element['hatColor'];
            if (typeof value !== 'string') throw "Expected 'hatColor' to be a string";
            hatColor = HatColorCompanion.fromSerialName(value);
            if (hatColor === null) throw "Did not expect 'hatColor' to be null!";
        }
        return Avatar.create(
            allocator,
            top,
            topAccessory,
            hairColor,
            facialHair,
            facialHairColor,
            clothes,
            colorFabric,
            eyes,
            eyebrows,
            mouthTypes,
            skinColors,
            clothesGraphic,
            hatColor,
        );
    },
    create: (buf) => new Avatar(buf),
};

export class FindBulkRequest implements UBinaryType {
    buffer: BufferAndOffset;
    constructor(buffer: BufferAndOffset) {
        this.buffer = buffer;
    }

    get usernames(): BinaryTypeList<UText> {
        let result: BinaryTypeList<UText> | null = null;
        const ptr = this.buffer.buf.getInt32(0 + this.buffer.offset);
        if (ptr === 0) result = null;
        else {
            result = new BinaryTypeList<UText>(UTextCompanion, this.buffer.copyWithOffset(ptr));
        }
        return result!;
    }

    set usernames(value) {
        if (value === null) this.buffer.buf.setInt32(0 + this.buffer.offset, 0);
        else this.buffer.buf.setInt32(0 + this.buffer.offset, value.buffer.offset);
    }

    encodeToJson() {
        return {
            usernames: this.usernames?.encodeToJson() ?? null,
        };
    }

    static create(
        allocator: BinaryAllocator,
        usernames: BinaryTypeList<UText>,
    ): FindBulkRequest {
        const result = allocator.allocate(FindBulkRequestCompanion);
        result.usernames = usernames;
        return result;
    }
}
export const FindBulkRequestCompanion: BinaryTypeCompanion<FindBulkRequest> = {
    size: 4,
    decodeFromJson: (allocator, element) => {
        if (typeof element !== "object" || element === null) {
            throw "Expected an object but found an: " + element;
        }
        let usernames: BinaryTypeList<UText> | null = null;
        {
            const value = element['usernames'];
            if (!Array.isArray(value)) throw "Expected 'usernames' to be an array";
            usernames = BinaryTypeList.create(
                UTextCompanion,
                allocator,
                value.map(it => UTextCompanion.decodeFromJson(allocator, it))
            );
            if (usernames === null) throw "Did not expect 'usernames' to be null!";
        }
        return FindBulkRequest.create(
            allocator,
            usernames,
        );
    },
    create: (buf) => new FindBulkRequest(buf),
};

export class FindBulkResponse implements UBinaryType {
    buffer: BufferAndOffset;
    constructor(buffer: BufferAndOffset) {
        this.buffer = buffer;
    }

    get avatars(): BinaryTypeDictionary<Avatar> {
        let result: BinaryTypeDictionary<Avatar> | null = null;
        const ptr = this.buffer.buf.getInt32(0 + this.buffer.offset);
        if (ptr === 0) result = null;
        else {
            result = new BinaryTypeDictionary<Avatar>(AvatarCompanion, this.buffer.copyWithOffset(ptr));
        }
        return result!;
    }

    set avatars(value) {
        if (value === null) this.buffer.buf.setInt32(0 + this.buffer.offset, 0);
        else this.buffer.buf.setInt32(0 + this.buffer.offset, value.buffer.offset);
    }

    encodeToJson() {
        return {
            avatars: this.avatars?.encodeToJson() ?? null,
        };
    }

    static create(
        allocator: BinaryAllocator,
        avatars: BinaryTypeDictionary<Avatar>,
    ): FindBulkResponse {
        const result = allocator.allocate(FindBulkResponseCompanion);
        result.avatars = avatars;
        return result;
    }
}
export const FindBulkResponseCompanion: BinaryTypeCompanion<FindBulkResponse> = {
    size: 4,
    decodeFromJson: (allocator, element) => {
        if (typeof element !== "object" || element === null) {
            throw "Expected an object but found an: " + element;
        }
        let avatars: BinaryTypeDictionary<Avatar> | null = null;
        {
            const value = element['avatars'];
            if (typeof value !== 'object') throw "Expected 'avatars' to be an object";
            let builder: Record<string, Avatar> = {};
            for (const key of Object.keys(value)) {
                builder[key] = AvatarCompanion.decodeFromJson(allocator, value[key]);
            }
            avatars = BinaryTypeDictionary.create(AvatarCompanion, allocator, builder);
            if (avatars === null) throw "Did not expect 'avatars' to be null!";
        }
        return FindBulkResponse.create(
            allocator,
            avatars,
        );
    },
    create: (buf) => new FindBulkResponse(buf),
};

export class Simple implements UBinaryType {
    buffer: BufferAndOffset;
    constructor(buffer: BufferAndOffset) {
        this.buffer = buffer;
    }

    get fie(): number {
        return this.buffer.buf.getInt32(0 + this.buffer.offset)
    }

    set fie(value: number) {
        this.buffer.buf.setInt32(0 + this.buffer.offset, value)
    }

    get _hund(): UText {
        let result: UText | null = null;
        const ptr = this.buffer.buf.getInt32(4 + this.buffer.offset);
        if (ptr === 0) result = null;
        else {
            result = new UText(this.buffer.copyWithOffset(ptr));
        }
        return result!;
    }
    set _hund(value: UText) {
        if (value === null) this.buffer.buf.setInt32(4 + this.buffer.offset, 0);
        else {
            this.buffer.buf.setInt32(4 + this.buffer.offset, value.buffer.offset);
        }
    }
    get hund(): string {
        return this._hund?.decode() ?? null;
    }

    get enumeration(): Top {
        return TopCompanion.fromEncoded(this.buffer.buf.getInt16(8 + this.buffer.offset))!;
    }
    set enumeration(value: Top) {
        this.buffer.buf.setInt16(8 + this.buffer.offset!, TopCompanion.encoded(value));
    }


    encodeToJson() {
        return {
            fie: this.fie,
            hund: this.hund,
            enumeration: this.enumeration != null ? TopCompanion.encoded(this.enumeration) : null,
        };
    }

    static create(
        allocator: BinaryAllocator,
        fie: number,
        hund: string,
        enumeration: Top,
    ): Simple {
        const result = allocator.allocate(SimpleCompanion);
        result.fie = fie;
        result._hund = allocator.allocateText(hund);
        result.enumeration = enumeration;
        return result;
    }
}
export const SimpleCompanion: BinaryTypeCompanion<Simple> = {
    size: 10,
    decodeFromJson: (allocator, element) => {
        if (typeof element !== "object" || element === null) {
            throw "Expected an object but found an: " + element;
        }
        let fie: number | null = null;
        {
            const value = element['fie'];
            if (typeof value !== 'number') throw "Expected 'fie' to be a number";
            fie = value;
            if (fie === null) throw "Did not expect 'fie' to be null!";
        }
        let hund: string | null = null;
        {
            const value = element['hund'];
            if (typeof value !== 'string') throw "Expected 'hund' to be a string";
            hund = value;
            if (hund === null) throw "Did not expect 'hund' to be null!";
        }
        let enumeration: Top | null = null;
        {
            const value = element['enumeration'];
            if (typeof value !== 'string') throw "Expected 'enumeration' to be a string";
            enumeration = TopCompanion.fromSerialName(value);
            if (enumeration === null) throw "Did not expect 'enumeration' to be null!";
        }
        return Simple.create(
            allocator,
            fie,
            hund,
            enumeration,
        );
    },
    create: (buf) => new Simple(buf),
};
