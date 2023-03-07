import {DebugContext} from "./Schema";

export function setSessionStateRequest(query: string | null, filters: string[] | null, level: string | null): string {
    return JSON.stringify({
        type: "set_session_state",
        query: query,
        filters: filters,
        level: level,
    });
}

export function fetchTextBlobRequest(generation: string, blobId: string, fileIndex: string) {
    return JSON.stringify({
        type: "fetch_text_blob",
        generation,
        id: blobId,
        fileIndex,
    });
}

export function fetchPreviousMessagesRequest(ctx: Pick<DebugContext, "timestamp" | "id">, onlyFindSelf?: boolean): string {
    return JSON.stringify({
        type: "fetch_previous_messages",
        timestamp: ctx.timestamp,
        id: ctx.id,
        onlyFindSelf
    });
}

export function replayMessagesRequest(generation: string, context: number, timestamp: number): string {
    return JSON.stringify({
        type: "replay_messages",
        generation,
        context,
        timestamp,
    });
}

type Nullable<T> = null | T;

export function activateServiceRequest(service: Nullable<string>, generation: Nullable<string>): string {
    return JSON.stringify({
        type: "activate_service",
        service,
        generation
    })
}

export function resetMessagesRequest(): string {
    return JSON.stringify({type: "clear_active_context"});
}
