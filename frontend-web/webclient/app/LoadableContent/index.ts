import { ErrorMessage, unwrap, isError } from "Utilities/XHRUtils";

export interface LoadableContent<Content = any> {
    loading: boolean
    error?: ErrorMessage
    content?: Content
}

export function emptyLoadableContent<T = any>(): LoadableContent<T> {
    return { loading: false };
}

export enum LoadableEventTag {
    LOADING = "LOADING",
    ERROR = "ERROR",
    CONTENT = "CONTENT"
}

export type LoadableEvent<Content> = LoadableEventLoading | LoadableEventError | LoadableEventContent<Content>;

interface LoadableEventLoading {
    type: typeof LoadableEventTag.LOADING
    loading: boolean
}

interface LoadableEventError {
    type: typeof LoadableEventTag.ERROR
    error: ErrorMessage
}

interface LoadableEventContent<Content> {
    type: typeof LoadableEventTag.CONTENT
    content: Content
}

export function loadableEventToContent<T>(event: LoadableEvent<T>): LoadableContent<T> {
    switch (event.type) {
        case LoadableEventTag.LOADING:
            return { loading: event.loading };
        case LoadableEventTag.CONTENT:
            return { loading: false, content: event.content };
        case LoadableEventTag.ERROR:
            return { loading: false, error: event.error };
    }
}

export function loadingEvent(loading: boolean): LoadableEventLoading {
    return { type: LoadableEventTag.LOADING, loading };
}

export async function unwrapCall<T>(httpResponse: Promise<{ request: XMLHttpRequest, response: T }>): Promise<LoadableEvent<T>> {
    const message = await unwrap(httpResponse);
    if (isError(message)) {
        return {
            type: LoadableEventTag.ERROR,
            error: message as ErrorMessage
        };
    } else {
        return {
            type: LoadableEventTag.CONTENT,
            content: message as T
        }
    }
}