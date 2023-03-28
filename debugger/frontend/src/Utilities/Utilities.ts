import {DebugContext, DebugMessage} from "../WebSockets/Schema";
import {DebugContextAndChildren, historyWatcherService} from "../WebSockets/Socket";

export function toNumberOrUndefined(str?: string): number | undefined {
    if (!str) return undefined;
    try {
        return parseInt(str, 10);
    } catch {
        return undefined;
    }
}

export function pushStateToHistory(service?: string, generation?: string, ctx?: Pick<DebugContext, "id" | "timestamp">) {
    function computeUrl(service?: string, generation?: string, ctx?: Pick<DebugContext, "id" | "timestamp">): string {
        if (!service && !generation) return "/";
        let url = "";

        if (generation) {
            url += `/${generation}`;
            if (ctx) url += `/${ctx.id}/${ctx.timestamp}`;
        } else {
            url += "/";
        }

        url += `#${service}`;
        return url;
    }

    const newUrl = computeUrl(service, generation, ctx);
    if (window.location.href.substring(window.location.origin.length) === newUrl) return;

    window.history.pushState(
        "",
        "",
        computeUrl(service, generation, ctx)
    );

    historyWatcherService.onUrlChanged();
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

