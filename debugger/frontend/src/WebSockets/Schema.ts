export function readInt8(buffer: DataView, offset: number): number {
    return buffer.getInt8(offset);
}

export function readInt16(buffer: DataView, offset: number): number {
    return buffer.getInt16(offset, false);
}

export function readInt32(buffer: DataView, offset: number): number {
    return buffer.getInt32(offset, false);
}

export function readInt64(buffer: DataView, offset: number): bigint {
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
    DATABASE_TRANSACTION = 5,
    DATABASE_QUERY = 6,
    DATABASE_RESPONSE = 7,
    LOG = 8,
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
        return Number(readInt64(this.buffer, this.offset + 1)); // 1
    }

    get ctxParent(): number {
        return readInt32(this.buffer, this.offset + 9); // 1 + 8 = 9
    }

    get ctxId(): number {
        return readInt32(this.buffer, this.offset + 13); // 1 + 8 + 4 = 13
    }

    get timestamp(): number {
        return Number(readInt64(this.buffer, this.offset + 17)); // 1 + 8 + 4 + 4 = 17
    }

    get importance(): MessageImportance {
        return readInt8(this.buffer, this.offset + 25) as MessageImportance; // 1 + 8 + 4 + 4 + 8 = 25
    }

    get importanceString(): string {
        return MessageImportance[this.importance];
    }

    get id(): number {
        return readInt32(this.buffer, this.offset + 26); // 1 + 8 + 4 + 4 + 8 + 4 = 29
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
        return readInt16(this.buffer, this.offset + HDRL);
    }

    get responseTime(): number {
        return readInt32(this.buffer, this.offset + HDRL + 2);
    }

    get call(): LargeText {
        return readText(this.buffer, this.offset + HDRL + 2 + 4, 64);
    }

    get response(): LargeText {
        return readText(this.buffer, this.offset + HDRL + 2 + 4 + 64, 64);
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
        return readInt16(this.buffer, this.offset + HDRL);
    }

    get responseTime(): number {
        return readInt32(this.buffer, this.offset + HDRL + 2);
    }

    get call(): LargeText {
        return readText(this.buffer, this.offset + HDRL + 2 + 4, 64);
    }

    get response(): LargeText {
        return readText(this.buffer, this.offset + HDRL + 2 + 4 + 64, 64);
    }
}

// DatabaseTransaction.type === 5
export class DatabaseTransaction extends BaseBinaryDebugMessage {
    get type(): BinaryDebugMessageType {
        return BinaryDebugMessageType.DATABASE_TRANSACTION
    }

    get event(): DBTransactionEvent {
        return readInt8(this.buffer, this.offset + HDRL)
    }

    get eventString(): string {
        return DBTransactionEvent[this.event];
    }

}

// DatabaseQuery.type === 6
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

// DatabaseResponse.type === 7
export class DatabaseResponse extends BaseBinaryDebugMessage {
    get type(): BinaryDebugMessageType {
        return BinaryDebugMessageType.DATABASE_RESPONSE
    }

    get responseTime(): number {
        return readInt32(this.buffer, this.offset + HDRL);
    }
}
// Log.type === 8
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
    ClientRequest | ClientResponse | ServerRequest | ServerResponse | DatabaseTransaction | DatabaseQuery | DatabaseResponse | Log;

// Contexts
export enum DebugContextType {
    CLIENT_REQUEST = 0,
    SERVER_REQUEST = 1,
    DATABASE_TRANSACTION = 2,
    BACKGROUND_TASK = 3,
    OTHER = 4,
}

export class DebugContext {
    protected buffer: DataView;
    protected offset: number;

    constructor(buffer: DataView, offset: number) {
        this.buffer = buffer;
        this.offset = offset;
    }

    get parent(): number {
        return readInt32(this.buffer, this.offset + 0);
    }

    get id(): number {
        return readInt32(this.buffer, this.offset + 4);
    }

    get importance(): MessageImportance {
        return readInt8(this.buffer, this.offset + 8) as MessageImportance;
    }

    get importanceString(): string {
        return MessageImportance[this.importance];
    }

    get type(): DebugContextType {
        return readInt8(this.buffer, this.offset + 9) as DebugContextType;
    }

    get typeString(): string {
        return DebugContextType[this.type];
    }

    get timestamp(): number {
        return Number(readInt64(this.buffer, this.offset + 10));
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

export function getServiceName(buffer: DataView, offset: number) {
    return readNameFromBuffer(buffer, offset, 256 - 16);
}

export function getGenerationName(buffer: DataView, offset: number) {
    return readNameFromBuffer(buffer, 256 - 16 + offset, 16);
}
