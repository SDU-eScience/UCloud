export function readInt1(buffer: DataView, offset: number): number {
    return buffer.getInt8(offset);
}

export function readInt2(buffer: DataView, offset: number): number {
    return buffer.getInt16(offset, false);
}

export function readInt4(buffer: DataView, offset: number): number {
    return buffer.getInt32(offset, false);
}

export function readInt8(buffer: DataView, offset: number): bigint {
    return buffer.getBigInt64(offset, false);
}

export function readBool(buffer: DataView, offset: number): boolean {
    return buffer.getInt8(offset) !== 0;
}

export function readBytes(buffer: DataView, offset: number, size: number): Uint8Array {
    return new Uint8Array(buffer.buffer, offset, size);
}

export interface LargeText {
    previewOrContent: string;
    overflowIdentifier?: string;
    blobFileId?: string;
}

export enum DBTransactionEvent {
    OPEN,
    COMMIT,
    ROLLBACK
}

const textDecoder = new TextDecoder();

const OVERFLOW_PREFIX = "#OF#";
const OVERFLOW_SEP = "#";
const TEXT_OFFSET_DUE_TO_BUG = 2;

function readText(buffer: DataView, offset: number, maxSize: number): LargeText {
    const u8a = new Uint8Array(buffer.buffer);
    let slice = u8a.slice(offset + TEXT_OFFSET_DUE_TO_BUG, offset + maxSize + TEXT_OFFSET_DUE_TO_BUG);
    const endIndex = slice.indexOf(0);
    slice = u8a.slice(offset + TEXT_OFFSET_DUE_TO_BUG, offset + endIndex + TEXT_OFFSET_DUE_TO_BUG);
    const decodedSlice = textDecoder.decode(slice);
    if (decodedSlice.startsWith(OVERFLOW_PREFIX)) {
        const withoutPrefix = decodedSlice.substring(4);
        const posIdx = withoutPrefix.indexOf(OVERFLOW_SEP);
        if (posIdx <= -1) {
            return {previewOrContent: "Invalid blob"};
        }
        const overflowIdentifier = withoutPrefix.substring(0, posIdx);

        const withFileId = withoutPrefix.substring(posIdx + 1);
        const sepIdx = withFileId.indexOf(OVERFLOW_SEP);
        if (sepIdx <= -1) {
            return {previewOrContent: "Invalid blob"};
        }
        const blobFileId = withFileId.substring(0, sepIdx);
        const previewOrContent = withFileId.substring(sepIdx + 1);
        return {previewOrContent, overflowIdentifier, blobFileId};
    }

    return {previewOrContent: decodedSlice};
}

export enum BinaryDebugMessageType {
    CLIENT_REQUEST = 1,
    CLIENT_RESPONSE = 2,
    SERVER_REQUEST = 3,
    SERVER_RESPONSE = 4,
    DATABASE_CONNECTION = 5,
    DATABASE_TRANSACTION = 6,
    DATABASE_QUERY = 7,
    DATABASE_RESPONSE = 8,
    LOG = 9,
}

export function binaryDebugMessageTypeToString(type: BinaryDebugMessageType): string {
    return BinaryDebugMessageType[type];
}

export enum MessageImportance {
    TELL_ME_EVERYTHING = 0,
    IMPLEMENTATION_DETAIL = 1,
    THIS_IS_NORMAL = 2,
    THIS_IS_ODD = 3,
    THIS_IS_WRONG = 4,
    THIS_IS_DANGEROUS = 5,
}

export function messageImportanceToString(importance: MessageImportance): string {
    return MessageImportance[importance];
}

export interface BinaryDebugMessage {
    type: BinaryDebugMessageType;
    ctxGeneration: number;
    ctxParent: number;
    ctxId: number;
    timestamp: number;
    importance: MessageImportance;
    id: number;
}

// Header length
const HDRL = 32;
abstract class BaseBinaryDebugMessage implements BinaryDebugMessage {
    protected buffer: DataView;
    protected offset: number;

    constructor(buffer: DataView, offset: number) {
        this.buffer = buffer;
        this.offset = offset;
    }

    get type(): BinaryDebugMessageType {
        return -1 as BinaryDebugMessageType;
    }

    get typeString(): string {
        return binaryDebugMessageTypeToString(this.type);
    }

    get ctxGeneration(): number {
        return Number(readInt8(this.buffer, this.offset + 1)); // 1
    }

    get ctxParent(): number {
        return readInt4(this.buffer, this.offset + 9); // 1 + 8 = 9 
    }

    get ctxId(): number {
        return readInt4(this.buffer, this.offset + 13); // 1 + 8 + 4 = 13
    }

    get timestamp(): number {
        return Number(readInt8(this.buffer, this.offset + 17)); // 1 + 8 + 4 + 4 = 17
    }

    get importance(): MessageImportance {
        return readInt1(this.buffer, this.offset + 25) as MessageImportance; // 1 + 8 + 4 + 4 + 8 = 25
    }

    get id(): number {
        return readInt4(this.buffer, this.offset + 26); // 1 + 8 + 4 + 4 + 8 + 4 = 29
    }
}

// ClientRequest.type === 1
export class ClientRequest extends BaseBinaryDebugMessage {
    get type(): BinaryDebugMessageType {
        return BinaryDebugMessageType.CLIENT_REQUEST;
    }

    get call(): LargeText {
        return readText(this.buffer, this.offset + HDRL, 64);
    }

    get payload(): LargeText {
        return readText(this.buffer, this.offset + HDRL + 64, 64);
    }
}

// ClientResponse.type === 2
export class ClientResponse extends BaseBinaryDebugMessage {
    get type(): BinaryDebugMessageType {
        return BinaryDebugMessageType.CLIENT_RESPONSE;
    }

    get responseCode(): number {
        return readInt1(this.buffer, this.offset + HDRL);
    }
    // Set as Int4, but is a long.
    get responseTime(): number {
        return readInt4(this.buffer, this.offset + HDRL + 1);
    }
    // Note(Jonas): This is very likely wrong. LargeText?
    get call(): LargeText {
        return readText(this.buffer, this.offset + HDRL + 1 + 4, 64);
    }

    // Note(Jonas): This is very likely wrong. LargeText?
    get response(): LargeText {
        return readText(this.buffer, this.offset + HDRL + 1 + 4 + 64, 64);
    }
}

// ServerRequest.type === 3
export class ServerRequest extends BaseBinaryDebugMessage {
    get type(): BinaryDebugMessageType {
        return BinaryDebugMessageType.SERVER_REQUEST;
    }

    get call(): LargeText {
        return readText(this.buffer, this.offset + HDRL, 64);
    }

    get payload(): LargeText {
        return readText(this.buffer, this.offset + HDRL + 64, 64);
    }
}

// ServerResponse.type === 4
export class ServerResponse extends BaseBinaryDebugMessage {
    get type(): BinaryDebugMessageType {
        return BinaryDebugMessageType.SERVER_RESPONSE;
    }

    get responseCode(): number {
        return readInt1(this.buffer, this.offset + HDRL);
    }

    get responseTime(): number {
        return readInt4(this.buffer, this.offset + HDRL + 1);
    }

    get call(): LargeText {
        return readText(this.buffer, this.offset + HDRL + 1 + 4, 64);
    }

    get response(): LargeText {
        return readText(this.buffer, this.offset + HDRL + 1 + 4 + 64, 64);
    }
}

// DatabaseConnection.type === 5
export class DatabaseConnection extends BaseBinaryDebugMessage {
    get type(): BinaryDebugMessageType {
        return BinaryDebugMessageType.DATABASE_CONNECTION
    }

    get isOpen(): boolean {
        return readBool(this.buffer, this.offset + HDRL);
    }
}

// DatabaseTransaction.type === 6

export class DatabaseTransaction extends BaseBinaryDebugMessage {
    get type(): BinaryDebugMessageType {
        return BinaryDebugMessageType.DATABASE_TRANSACTION
    }

    get event(): string {
        return DBTransactionEvent[readInt1(this.buffer, this.offset + HDRL)];
    }

}

// DatabaseQuery.type === 7
export class DatabaseQuery extends BaseBinaryDebugMessage {
    get type(): BinaryDebugMessageType {
        return BinaryDebugMessageType.DATABASE_QUERY
    }

    get parameters(): LargeText {
        return readText(this.buffer, this.offset + HDRL, 64);
    }

    get query(): LargeText {
        return readText(this.buffer, this.offset + HDRL + 64, 128);
    }
}

// DatabaseResponse.type === 8
export class DatabaseResponse extends BaseBinaryDebugMessage {
    get type(): BinaryDebugMessageType {
        return BinaryDebugMessageType.DATABASE_RESPONSE
    }

    get responseTime(): number {
        return readInt4(this.buffer, this.offset + HDRL);
    }
}
// Log.type === 9
export class Log extends BaseBinaryDebugMessage {
    get type(): BinaryDebugMessageType {
        return BinaryDebugMessageType.LOG;
    }

    get message(): LargeText {
        return readText(this.buffer, this.offset + HDRL, 128);
    }

    get extra(): LargeText {
        return readText(this.buffer, this.offset + HDRL + 128, 32);
    }
}

export type DebugMessage =
    ClientRequest | ClientResponse | ServerRequest | ServerResponse | DatabaseConnection |
    DatabaseTransaction | DatabaseQuery | DatabaseResponse | Log;

// Contexts
export enum DebugContextType {
    CLIENT_REQUEST = 0,
    SERVER_REQUEST = 1,
    DATABASE_TRANSACTION = 2,
    BACKGROUND_TASK = 3,
    OTHER = 4,
}

export function debugContextToString(ctx: DebugContextType): string {
    return DebugContextType[ctx];
}

export class DebugContext {
    protected buffer: DataView;
    protected offset: number;

    constructor(buffer: DataView, offset: number) {
        this.buffer = buffer;
        this.offset = offset;
    }

    get parent(): number {
        return readInt4(this.buffer, this.offset + 0);
    }

    get id(): number {
        return readInt4(this.buffer, this.offset + 4);
    }

    get importance(): MessageImportance {
        return readInt1(this.buffer, this.offset + 8) as MessageImportance;
    }

    get importanceString(): string {
        return MessageImportance[this.importance];
    }

    get type(): DebugContextType {
        return readInt1(this.buffer, this.offset + 9) as DebugContextType;
    }

    get typeString(): string {
        return debugContextToString(this.type);
    }

    get timestamp(): number {
        return Number(readInt8(this.buffer, this.offset + 10));
    }

    get name(): string {
        return readNameFromBuffer(this.buffer, this.offset + 22, 108);
    }
}

function readNameFromBuffer(buffer: DataView, offset: number, size: number) {
    const nameBytes = readBytes(buffer, offset, size);
    let end = nameBytes.indexOf(0);
    if (end === -1) end = nameBytes.length;
    const slice = nameBytes.slice(0, end);
    return textDecoder.decode(slice);
}

export function getServiceName(buffer: DataView) {
    return readNameFromBuffer(buffer, 8, 264 - 16);
}

export function getGenerationName(buffer: DataView) {
    return readNameFromBuffer(buffer, 264 - 16, 16);
}