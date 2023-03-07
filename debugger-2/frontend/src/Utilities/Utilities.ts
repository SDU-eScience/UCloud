import {DebugContext, DebugMessage} from "../WebSockets/Schema";
import {DebugContextAndChildren} from "../WebSockets/Socket";

export function newURL(service?: string, generation?: string, ctx?: Pick<DebugContext, "id" | "timestamp">): string {
    if (!service || !generation) return "";
    let url = `/${generation}`;
    if (ctx) url += `/${ctx.id}/${ctx.timestamp}`;
    url += `#${service}`;
    return url;
}

export function toNumberOrUndefined(str?: string): number | undefined {
    if (!str) return undefined;
    try {
        return parseInt(str, 10);
    } catch {
        return undefined;
    }
}

// Note(Jonas): This function currently trashes history as this function is sometimes called when going back.
// Possible solutions:
//      - Move pushStateToHistory-calls higher up, so it's more controlled when it happens.
//      - Add a `noUpdateHistory`-variable that skips the calls. Essentially the same as above.
//              - Both are error prone.
//      - Other ways?
export function pushStateToHistory(service?: string, generation?: string, ctx?: Pick<DebugContext, "id" | "timestamp">) {
    window.history.pushState({
        service,
        generation,
        context: ctx
    }, "", newURL(service, generation, ctx));
}

interface WithLength {
    length: number;
}

export function nullIfEmpty<T extends WithLength>(f: T): T | null {
    return f.length === 0 ? null : f;
}

export function debugMessageOrCtxSort(a: (DebugMessage | DebugContextAndChildren), b: (DebugMessage | DebugContextAndChildren)): number {
    const timestampA = isDebugMessage(a) ? a.timestamp : a.ctx.timestamp;
    const timestampB = isDebugMessage(b) ? b.timestamp : b.ctx.timestamp;
    return timestampA - timestampB;
}

export function contextSort(a: DebugContext, b: DebugContext): number {
    return a.timestamp - b.timestamp;
}

export function isDebugMessage(input: DebugMessage | DebugContext | DebugContextAndChildren): input is DebugMessage {
    return "ctxId" in input;
}

