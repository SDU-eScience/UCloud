// Runtime
export interface BinaryTypeCompanion<T> {
    size: number;
    create: (offset: BufferAndOffset) => T;
    decodeFromJson: (allocator: BinaryAllocator, json: unknown) => T;
}

export interface UBinaryType {
    buffer: BufferAndOffset;
    encodeToJson: () => unknown;
}

export class BinaryAllocator {
    private buf: DataView;
    private ptr: number;

    private duplicateStringsSize: number;
    private duplicateStrings: string[] = [];
    private duplicateStringsPointers: number[] = [];
    private duplicateStringsIdx: number = 0;

    private readOnly: boolean;

    constructor(bufferSize: number, duplicateStringsSize: number, readOnly: boolean) {
        this.buf = new DataView(new ArrayBuffer(bufferSize));
        this.duplicateStringsSize = duplicateStringsSize;
        this.readOnly = readOnly;
        this.ptr = 4;

        this.buf.setInt32(0, this.ptr);
    }

    load(dataView: DataView) {
        const source = new Uint8Array(dataView.buffer);
        const destination = new Uint8Array(this.buf.buffer, 0, source.byteLength);

        destination.set(source);
    }

    allocate<T>(companion: BinaryTypeCompanion<T>): T {
        if (this.readOnly) {
            throw "This allocator is marked read only. Please copy the data to a new allocator for modification";
        }

        const buffer = new BufferAndOffset(this, this.buf, this.ptr);
        this.ptr += companion.size;
        return companion.create(buffer);
    }

    allocateText(text: string): UText {
        if (this.readOnly) {
            throw "This allocator is marked read only. Please copy the data to a new allocator for modification";
        }

        let idx = 0;
        for (const duplicate of this.duplicateStrings) {
            if (duplicate === text) {
                return new UText(new BufferAndOffset(this, this.buf, this.duplicateStringsPointers[idx]));
            }
            idx++;
        }

        const size = textEncoder.encodeInto(text, new Uint8Array(this.buf.buffer, this.ptr + 4)).written!;
        this.buf.setInt32(this.ptr, size);
        const resultPointer = this.ptr;
        this.ptr = resultPointer + 4 + size;

        if (this.duplicateStringsIdx < this.duplicateStringsSize) {
            this.duplicateStrings.push(text);
            this.duplicateStringsPointers.push(resultPointer);
            this.duplicateStringsIdx++;
        } else {
            const key = this.duplicateStringsIdx % this.duplicateStringsSize;
            this.duplicateStrings[key] = text;
            this.duplicateStringsPointers[key] = resultPointer;
            this.duplicateStringsIdx++;
        }

        return new UText(new BufferAndOffset(this, this.buf, resultPointer));
    }

    allocateDynamic(size: number): BufferAndOffset {
        if (this.readOnly) {
            throw "This allocator is marked read only. Please copy the data to a new allocator for modification";
        }

        const buffer = new BufferAndOffset(this, this.buf, this.ptr);
        this.ptr += size;
        return buffer;
    }

    reset() {
        new Uint8Array(this.buf.buffer).fill(0);
        this.ptr = 4;
        this.buf.setInt32(0, this.ptr);
        this.duplicateStrings = [];
        this.duplicateStringsPointers = [];
        this.duplicateStringsIdx = 0;
    }

    updateRoot(root: UBinaryType) {
        this.buf.setInt32(0, root.buffer.offset);
    }

    root(): BufferAndOffset {
        return new BufferAndOffset(this, this.buf, this.buf.getInt32(0));
    }

    slicedBuffer(): DataView {
        return new DataView(this.buf.buffer.slice(0, this.ptr));
    }
}

export class BufferAndOffset {
    allocator: BinaryAllocator;
    buf: DataView;
    offset: number;

    constructor(allocator: BinaryAllocator, buf: DataView, offset: number) {
        this.buf = buf;
        this.offset = offset;
        this.allocator = allocator;
    }

    copyWithOffset(newOffset: number): BufferAndOffset {
        return new BufferAndOffset(this.allocator, this.buf, newOffset);
    }
}

const textDecoder = new TextDecoder();
const textEncoder = new TextEncoder();

export class UText implements UBinaryType {
    buffer: BufferAndOffset;

    constructor(buffer: BufferAndOffset) {
        this.buffer = buffer;
    }

    get count(): number {
        return this.buffer.buf.getInt32(0 + this.buffer.offset);
    }

    set count(value: number) {
        this.buffer.buf.setInt32(0 + this.buffer.offset, value);
    }

    get data(): DataView {
        return new DataView(this.buffer.buf.buffer.slice(4 + this.buffer.offset, 4 + this.buffer.offset + this.count));
    }

    encodeToJson() {
        return this.decode();
    }

    decode(): string {
        return textDecoder.decode(this.data);
    }
}

export const UTextCompanion: BinaryTypeCompanion<UText> = {
    create(offset: BufferAndOffset): UText {
        return new UText(offset);
    },

    decodeFromJson(allocator: BinaryAllocator, json: unknown): UText {
        if (typeof json !== "string") {
            throw "Expected a string but found: " + json;
        }

        return allocator.allocateText(json);
    },

    size: 0,
};

export class BinaryTypeList<E extends UBinaryType> implements UBinaryType {
    buffer: BufferAndOffset;
    companion: BinaryTypeCompanion<E>;

    constructor(companion: BinaryTypeCompanion<E>, buffer: BufferAndOffset) {
        this.buffer = buffer;
        this.companion = companion;
    }

    get count(): number {
        return this.buffer.buf.getInt32(this.buffer.offset);
    }

    set count(value) {
        this.buffer.buf.setInt32(this.buffer.offset, value);
    }

    set(index: number, element: E | null) {
        if (index < 0 || index >= this.count) {
            throw `Array index of out bounds: wanted to set value at ${index} but size is ${this.count}`;
        }

        const ptr = element === null ? 0 : element.buffer.offset;
        this.buffer.buf.setInt32(this.buffer.offset + ((1 + index) * 4), ptr);
    }

    getOrNull(index: number): E | null {
        if (index < 0 || index >= this.count) {
            throw `Array index of out bounds: wanted to get value at ${index} but size is ${this.count}`;
        }

        const ptr = this.buffer.buf.getInt32(this.buffer.offset + ((1 + index) * 4));
        if (ptr === 0) return null
        return this.companion.create(this.buffer.copyWithOffset(ptr));
    }

    get(index: number): E {
        const result = this.getOrNull(index);
        if (result === null) {
            throw new Error(`Null pointer found in array at index ${index}. ` +
                `The full array is: ${this.encodeToJson()}`);
        }
        return result;
    }

    encodeToJson() {
        const result: unknown[] = [];
        for (let i = 0; i < this.count; i++) {
            result.push(this.getOrNull(i)?.encodeToJson() ?? null);
        }
        return result;
    }

    static create<E extends UBinaryType>(
        companion: BinaryTypeCompanion<E>,
        allocator: BinaryAllocator,
        entries: E[]
    ): BinaryTypeList<E> {
        const result = BinaryTypeList.createOfSize(companion, allocator, entries.length);
        for (let i = 0; i < entries.length; i++) {
            const entry = entries[i];
            result.set(i, entry);
        }
        return result;
    }

    static createOfSize<E extends UBinaryType>(
        companion: BinaryTypeCompanion<E>,
        allocator: BinaryAllocator,
        size: number
    ): BinaryTypeList<E> {
        const buf = allocator.allocateDynamic(4 + size * 4);
        const result = new BinaryTypeList(companion, buf);
        result.count = size;
        return result;
    }
}

export class BinaryTypeDictionary<E extends UBinaryType> implements UBinaryType {
    buffer: BufferAndOffset;
    companion: BinaryTypeCompanion<E>;
    private nextIdx: number = 0;

    constructor(companion: BinaryTypeCompanion<E>, buffer: BufferAndOffset) {
        this.buffer = buffer;
        this.companion = companion;
    }

    get count(): number {
        return this.buffer.buf.getInt32(this.buffer.offset);
    }

    set count(value) {
        this.buffer.buf.setInt32(this.buffer.offset, value);
    }

    set(key: string, value: E) {
        this.setByUText(this.buffer.allocator.allocateText(key), value);
    }

    setByUText(key: UText, value: E) {
        let index = this.nextIdx++;
        if (index < 0 || index >= this.count) {
            throw `Too many entries in dictionary! Wanted to set value at ${index} but size is ${this.count}`;
        }

        this.buffer.buf.setInt32(this.buffer.offset + ((1 + (index * 2) + 0) * 4), key.buffer.offset);
        this.buffer.buf.setInt32(this.buffer.offset + ((1 + (index * 2) + 1) * 4), value.buffer.offset);
    }

    get(key: string): E | null {
        for (let i = 0; i < this.count; i++) {
            const keyPtr = this.buffer.buf.getInt32(this.buffer.offset + ((1 + (i * 2) + 0) * 4));
            if (keyPtr === 0) continue;
            const text = new UText(this.buffer.copyWithOffset(keyPtr));
            if (text.decode() === key) {
                const dataPtr = this.buffer.buf.getInt32(this.buffer.offset + ((1 + (i * 2) + 1) * 4));
                if (dataPtr === 0) return null;
                return this.companion.create(this.buffer.copyWithOffset(dataPtr));
            }
        }
        return null;
    }

    entries(): [key: string, value: E][] {
        const entries: [key: string, value: E][] = [];
        for (let i = 0; i < this.count; i++) {
            const keyPtr = this.buffer.buf.getInt32(this.buffer.offset + ((1 + (i * 2) + 0) * 4));
            if (keyPtr === 0) continue;
            const text = new UText(this.buffer.copyWithOffset(keyPtr));
            const decoded = text.decode();

            const dataPtr = this.buffer.buf.getInt32(this.buffer.offset + ((1 + (i * 2) + 1) * 4));
            const value = this.companion.create(this.buffer.copyWithOffset(dataPtr));

            entries.push([decoded, value]);
        }
        return entries;
    }

    encodeToJson(): Record<string, unknown> {
        const result: Record<string, unknown> = {};
        for (const [key, value] of this.entries()) {
            result[key] = value.encodeToJson();
        }
        return result;
    }

    static create<E extends UBinaryType>(
        companion: BinaryTypeCompanion<E>,
        allocator: BinaryAllocator,
        entries: Record<string, E>
    ) {
        const keys = Object.keys(entries);
        const result = BinaryTypeDictionary.createWithSize(companion, allocator, keys.length);
        for (const key of keys) {
            const utext = allocator.allocateText(key)
            const value = entries[key];
            result.setByUText(utext, value);
        }
        return result;
    }

    static createWithSize<E extends UBinaryType>(
        companion: BinaryTypeCompanion<E>,
        allocator: BinaryAllocator,
        size: number
    ) {
        const buf = allocator.allocateDynamic(4 + size * 2 * 4);
        const result = new BinaryTypeDictionary(companion, buf);
        result.count = size;
        return result;
    }
}

// Scratch
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

class Simple implements UBinaryType {
    buffer: BufferAndOffset;
    constructor(buffer: BufferAndOffset) {
        this.buffer = buffer;
    }

    get fie(): number {
        return this.buffer.buf.getInt32(0 + this.buffer.offset);
    }

    set fie(value: number) {
        this.buffer.buf.setInt32(0 + this.buffer.offset, value);
    }

    get _hund(): UText {
        const offset = this.buffer.buf.getInt32(4 + this.buffer.offset);
        return new UText(this.buffer.copyWithOffset(offset));
    }

    set _hund(value: UText) {
        this.buffer.buf.setInt32(4 + this.buffer.offset, value.buffer.offset);
    }

    get hund(): string {
        return this._hund.decode();
    }

    get enumeration(): Top {
        return TopCompanion.fromEncoded(this.buffer.buf.getInt16(8 + this.buffer.offset))!
    }

    set enumeration(value: Top) {
        this.buffer.buf.setInt16(8 + this.buffer.offset!, TopCompanion.encoded(value));
    }

    encodeToJson() {
        return { fie: this.fie, hund: this.hund, enumeration: TopCompanion.serialName(this.enumeration) }
    }

    static create(
        allocator: BinaryAllocator,
        fie: number,
        hund: string,
        enumeration: Top
    ): Simple {
        const result = allocator.allocate(SimpleCompanion);
        result.fie = fie;
        result._hund = allocator.allocateText(hund);
        result.enumeration = enumeration;
        return result;
    }
}

const SimpleCompanion: BinaryTypeCompanion<Simple> = {
    size: 10,
    decodeFromJson: (allocator, element) => {
        if (typeof element !== "object" || element === null) {
            throw "Expected an object";
        }

        let fie: number;
        {
            const value = element["fie"];
            if (typeof value !== "number") {
                throw "Expected fie to be a number";
            }

            fie = value;
        }

        let hund: string;
        {
            const value = element["hund"];
            if (typeof value !== "string") {
                throw "Expected hund to be a string";
            }

            hund = value;
        }

        let enumeration: Top;
        {
            const value = element["enumeration"];
            if (typeof value !== "string") {
                throw "Expected enumeration to be a string";
            }

            enumeration = TopCompanion.fromSerialName(value)!;
        }

        return Simple.create(allocator, fie, hund, enumeration);
    },
    create: (buf) => new Simple(buf),
};

function useAllocator<R>(block: (allocator: BinaryAllocator) => R): R {
    const allocator = new BinaryAllocator(1024 * 512, 128, false)
    return block(allocator);
}

export function messageTest() {
    useAllocator(alloc => {
        const result = Simple.create(alloc, 42, "hund", Top.HAT);
        console.log(result.fie, result.hund, TopCompanion.serialName(result.enumeration));
        console.log(result.encodeToJson());

        console.log(alloc.slicedBuffer());
        console.log(alloc.slicedBuffer().byteLength);
    });

    useAllocator(alloc => {
        const decoded = SimpleCompanion.decodeFromJson(alloc, { fie: 1337, hund: "gamer", enumeration: "Eyepatch" });
        console.log(decoded.encodeToJson());
        console.log(alloc.slicedBuffer());
    })
}
