export enum Opcode {
    SysHello = 0x01,
    UiEvent = 0x11,
    UiMount = 0x12,
    ModelPatch = 0x13,
    ModelInput = 0x14,
    RpcRequest = 0x20,
    RpcResponse = 0x21,
}

export enum RpcStatus {
    Ok = 0,
    BadRequest = 1,
    NotFound = 2,
    Internal = 3,
    Canceled = 4,
}

export enum ValueKind {
    Null = 0,
    Bool = 1,
    S64 = 2,
    F64 = 3,
    String = 4,
    List = 5,
    Object = 6,
}

export type Value =
    | { kind: ValueKind.Null }
    | { kind: ValueKind.Bool; bool: boolean }
    | { kind: ValueKind.S64; s64: number }
    | { kind: ValueKind.F64; f64: number }
    | { kind: ValueKind.String; string: string }
    | { kind: ValueKind.List; list: Value[] }
    | { kind: ValueKind.Object; object: Record<string, Value> };

export type PlainValue = null | boolean | number | string | PlainValue[] | { [key: string]: PlainValue };

export interface UiNode {
    id: string;
    component: string;
    props: Record<string, Value>;
    bindPath: string;
    optimistic: boolean;
    children: UiNode[];
}

export interface UiMount {
    // interfaceId identifies the mounted UCX interface instance.
    interfaceId: string;
    root: UiNode;
    model: Record<string, Value>;
}

export interface ModelPatch {
    changes: Record<string, Value>;
}

export interface ModelInput {
    eventId: number;
    nodeId: string;
    path: string;
    value: Value;
}

export interface UiEvent {
    nodeId: string;
    event: string;
    value: Value;
}

export interface Frame {
    opcode: Opcode;
    seq: number;
    replyToSeq: number;

    sysHello?: string;
    uiEvent?: UiEvent;
    uiMount?: UiMount;
    modelPatch?: ModelPatch;
    modelInput?: ModelInput;
    rpcRequestName?: string;
    rpcPayload?: Record<string, Value>;
    rpcStatus?: number;
}

class BinaryWriter {
    private bytes: number[] = [];
    private encoder = new TextEncoder();

    writeU8(v: number) { this.bytes.push(v & 0xff); }

    writeU32(v: number) {
        const buf = new ArrayBuffer(4);
        const dv = new DataView(buf);
        dv.setUint32(0, v >>> 0, false);
        this.push(buf);
    }

    writeS64(v: number) {
        const buf = new ArrayBuffer(8);
        const dv = new DataView(buf);
        dv.setBigInt64(0, BigInt(Math.trunc(v)), false);
        this.push(buf);
    }

    writeF64(v: number) {
        const buf = new ArrayBuffer(8);
        const dv = new DataView(buf);
        dv.setFloat64(0, v, false);
        this.push(buf);
    }

    writeString(v: string) {
        const encoded = this.encoder.encode(v);
        this.writeU32(encoded.length);
        for (const b of encoded) this.bytes.push(b);
    }

    stringByteLength(v: string): number {
        return this.encoder.encode(v).length;
    }

    toBytes(): Uint8Array { return new Uint8Array(this.bytes); }

    private push(buf: ArrayBuffer) {
        const arr = new Uint8Array(buf);
        for (const b of arr) this.bytes.push(b);
    }
}

class BinaryReader {
    private offset = 0;
    private decoder = new TextDecoder();

    constructor(private readonly bytes: Uint8Array) {}

    readU8(): number {
        this.ensure(1);
        return this.bytes[this.offset++];
    }

    readU32(): number {
        this.ensure(4);
        const dv = new DataView(this.bytes.buffer, this.bytes.byteOffset + this.offset, 4);
        const val = dv.getUint32(0, false);
        this.offset += 4;
        return val;
    }

    readS64(): number {
        this.ensure(8);
        const dv = new DataView(this.bytes.buffer, this.bytes.byteOffset + this.offset, 8);
        const val = Number(dv.getBigInt64(0, false));
        this.offset += 8;
        return val;
    }

    readF64(): number {
        this.ensure(8);
        const dv = new DataView(this.bytes.buffer, this.bytes.byteOffset + this.offset, 8);
        const val = dv.getFloat64(0, false);
        this.offset += 8;
        return val;
    }

    readString(): string {
        const len = this.readU32();
        this.ensure(len);
        const out = this.decoder.decode(this.bytes.subarray(this.offset, this.offset + len));
        this.offset += len;
        return out;
    }

    isAtEnd(): boolean { return this.offset >= this.bytes.length; }

    private ensure(length: number) {
        if (this.offset + length > this.bytes.length) {
            throw new Error("binary frame out-of-bounds read");
        }
    }
}

export function encodeFrame(frame: Frame): Uint8Array {
    const w = new BinaryWriter();
    w.writeU8(frame.opcode);
    w.writeS64(frame.seq);
    w.writeS64(frame.replyToSeq ?? 0);

    switch (frame.opcode) {
        case Opcode.SysHello: {
            const payload = frame.sysHello ?? "";
            const payloadBytes = w.stringByteLength(payload);
            if (payloadBytes >= 64 * 1024) {
                throw new Error(`sys hello payload too large (${payloadBytes} bytes)`);
            }
            w.writeString(payload);
            break;
        }
        case Opcode.UiEvent:
            encodeUiEvent(w, frame.uiEvent!);
            break;
        case Opcode.UiMount:
            encodeUiMount(w, frame.uiMount!);
            break;
        case Opcode.ModelPatch:
            encodeModelPatch(w, frame.modelPatch!);
            break;
        case Opcode.ModelInput:
            encodeModelInput(w, frame.modelInput!);
            break;
        case Opcode.RpcRequest:
            w.writeString(frame.rpcRequestName ?? "");
            writeValueMap(w, frame.rpcPayload ?? {});
            break;
        case Opcode.RpcResponse:
            w.writeU8(frame.rpcStatus ?? 0);
            writeValueMap(w, frame.rpcPayload ?? {});
            break;
    }

    return w.toBytes();
}

export function decodeFrame(input: Uint8Array): Frame {
    const r = new BinaryReader(input);
    const opcode = r.readU8() as Opcode;
    const seq = r.readS64();
    const replyToSeq = r.readS64();

    const frame: Frame = {opcode, seq, replyToSeq};
    switch (opcode) {
        case Opcode.SysHello: {
            const payload = r.readString();
            const payloadBytes = new TextEncoder().encode(payload).length;
            if (payloadBytes >= 64 * 1024) {
                throw new Error(`sys hello payload too large (${payloadBytes} bytes)`);
            }
            frame.sysHello = payload;
            break;
        }
        case Opcode.UiEvent:
            frame.uiEvent = decodeUiEvent(r);
            break;
        case Opcode.UiMount:
            frame.uiMount = decodeUiMount(r);
            break;
        case Opcode.ModelPatch:
            frame.modelPatch = decodeModelPatch(r);
            break;
        case Opcode.ModelInput:
            frame.modelInput = decodeModelInput(r);
            break;
        case Opcode.RpcRequest:
            frame.rpcRequestName = r.readString();
            frame.rpcPayload = readValueMap(r);
            break;
        case Opcode.RpcResponse:
            frame.rpcStatus = r.readU8();
            frame.rpcPayload = readValueMap(r);
            break;
    }

    if (!r.isAtEnd()) throw new Error("trailing bytes in frame");
    return frame;
}

function encodeUiMount(w: BinaryWriter, mount: UiMount) {
    w.writeString(mount.interfaceId);
    encodeUiNode(w, mount.root);
    writeValueMap(w, mount.model);
}

function decodeUiMount(r: BinaryReader): UiMount {
    return {
        interfaceId: r.readString(),
        root: decodeUiNode(r),
        model: readValueMap(r),
    };
}

function encodeUiNode(w: BinaryWriter, node: UiNode) {
    w.writeString(node.id);
    w.writeString(node.component);
    writeValueMap(w, node.props);
    w.writeString(node.bindPath ?? "");
    w.writeU8(node.optimistic ? 1 : 0);
    w.writeU32(node.children.length);
    for (const child of node.children) encodeUiNode(w, child);
}

function decodeUiNode(r: BinaryReader): UiNode {
    const id = r.readString();
    const component = r.readString();
    const props = readValueMap(r);
    const bindPath = r.readString();
    const optimistic = r.readU8() !== 0;
    const childCount = r.readU32();
    const children: UiNode[] = [];
    for (let i = 0; i < childCount; i++) children.push(decodeUiNode(r));
    return {id, component, props, bindPath, optimistic, children};
}

function encodeModelPatch(w: BinaryWriter, patch: ModelPatch) {
    writeValueMap(w, patch.changes);
}

function decodeModelPatch(r: BinaryReader): ModelPatch {
    return {changes: readValueMap(r)};
}

function encodeModelInput(w: BinaryWriter, input: ModelInput) {
    w.writeS64(input.eventId);
    w.writeString(input.nodeId);
    w.writeString(input.path);
    encodeValue(w, input.value);
}

function decodeModelInput(r: BinaryReader): ModelInput {
    return {
        eventId: r.readS64(),
        nodeId: r.readString(),
        path: r.readString(),
        value: decodeValue(r),
    };
}

function encodeUiEvent(w: BinaryWriter, ev: UiEvent) {
    w.writeString(ev.nodeId);
    w.writeString(ev.event);
    encodeValue(w, ev.value);
}

function decodeUiEvent(r: BinaryReader): UiEvent {
    return {nodeId: r.readString(), event: r.readString(), value: decodeValue(r)};
}

function encodeValue(w: BinaryWriter, value: Value) {
    w.writeU8(value.kind);
    switch (value.kind) {
        case ValueKind.Null:
            break;
        case ValueKind.Bool:
            w.writeU8(value.bool ? 1 : 0);
            break;
        case ValueKind.S64:
            w.writeS64(value.s64);
            break;
        case ValueKind.F64:
            w.writeF64(value.f64);
            break;
        case ValueKind.String:
            w.writeString(value.string);
            break;
        case ValueKind.List:
            w.writeU32(value.list.length);
            for (const item of value.list) encodeValue(w, item);
            break;
        case ValueKind.Object:
            writeValueMap(w, value.object);
            break;
    }
}

function decodeValue(r: BinaryReader): Value {
    const kind = r.readU8() as ValueKind;
    switch (kind) {
        case ValueKind.Null:
            return {kind};
        case ValueKind.Bool:
            return {kind, bool: r.readU8() !== 0};
        case ValueKind.S64:
            return {kind, s64: r.readS64()};
        case ValueKind.F64:
            return {kind, f64: r.readF64()};
        case ValueKind.String:
            return {kind, string: r.readString()};
        case ValueKind.List: {
            const count = r.readU32();
            const list: Value[] = [];
            for (let i = 0; i < count; i++) list.push(decodeValue(r));
            return {kind, list};
        }
        case ValueKind.Object:
            return {kind, object: readValueMap(r)};
        default:
            return {kind: ValueKind.Null};
    }
}

function writeValueMap(w: BinaryWriter, map: Record<string, Value>) {
    const keys = Object.keys(map).sort();
    w.writeU32(keys.length);
    for (const key of keys) {
        w.writeString(key);
        encodeValue(w, map[key]);
    }
}

function readValueMap(r: BinaryReader): Record<string, Value> {
    const count = r.readU32();
    const result: Record<string, Value> = {};
    for (let i = 0; i < count; i++) result[r.readString()] = decodeValue(r);
    return result;
}

export function toBase64(data: Uint8Array): string {
    let binary = "";
    for (const byte of data) binary += String.fromCharCode(byte);
    return btoa(binary);
}

export function fromBase64(base64: string): Uint8Array {
    const binary = atob(base64);
    const out = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) out[i] = binary.charCodeAt(i);
    return out;
}

export function valueToPlain(value: Value): PlainValue {
    switch (value.kind) {
        case ValueKind.Null:
            return null;
        case ValueKind.Bool:
            return value.bool;
        case ValueKind.S64:
            return value.s64;
        case ValueKind.F64:
            return value.f64;
        case ValueKind.String:
            return value.string;
        case ValueKind.List:
            return value.list.map(valueToPlain);
        case ValueKind.Object: {
            const result: {[key: string]: PlainValue} = {};
            for (const [key, item] of Object.entries(value.object)) {
                result[key] = valueToPlain(item);
            }
            return result;
        }
    }
}

export function plainToValue(value: PlainValue): Value {
    if (value === null || value === undefined) {
        return {kind: ValueKind.Null};
    }

    if (typeof value === "boolean") {
        return {kind: ValueKind.Bool, bool: value};
    }

    if (typeof value === "number") {
        if (Number.isFinite(value) && Number.isInteger(value)) {
            return {kind: ValueKind.S64, s64: value};
        }
        return {kind: ValueKind.F64, f64: value};
    }

    if (typeof value === "string") {
        return {kind: ValueKind.String, string: value};
    }

    if (Array.isArray(value)) {
        return {kind: ValueKind.List, list: value.map(plainToValue)};
    }

    const object: Record<string, Value> = {};
    for (const [key, item] of Object.entries(value)) {
        object[key] = plainToValue(item as PlainValue);
    }
    return {kind: ValueKind.Object, object};
}

export function valueMapToPlain(input: Record<string, Value>): Record<string, PlainValue> {
    const result: Record<string, PlainValue> = {};
    for (const [key, value] of Object.entries(input)) {
        result[key] = valueToPlain(value);
    }
    return result;
}

export function plainMapToValue(input: Record<string, PlainValue>): Record<string, Value> {
    const result: Record<string, Value> = {};
    for (const [key, value] of Object.entries(input)) {
        result[key] = plainToValue(value);
    }
    return result;
}
