import {ErrorMessage, unwrap, isError} from "Utilities/XHRUtils";
import {snackbarStore} from "Snackbar/SnackbarStore";

export interface LoadableContent<Content = any> {
    loading: boolean
    error?: ErrorMessage
    content?: Content
}

export const emptyLoadableContent = <T = any>(): LoadableContent<T> => ({
    loading: false
});

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
}

interface LoadableEventContent<Content> {
    type: typeof LoadableEventTag.CONTENT
    content: Content
}

export function loadableEventToContent<T>(event: LoadableEvent<T>): LoadableContent<T> {
    switch (event.type) {
        case LoadableEventTag.LOADING:
            return {loading: event.loading};
        case LoadableEventTag.CONTENT:
            return {loading: false, content: event.content};
        case LoadableEventTag.ERROR:
            return {loading: false};
    }
}

export const loadingEvent = (loading: boolean): LoadableEventLoading => ({
    type: LoadableEventTag.LOADING, loading
});

export async function unwrapCall<T>(httpResponse: Promise<{request: XMLHttpRequest, response: T}>): Promise<LoadableEvent<T>> {
    const message = await unwrap(httpResponse);
    if (isError(message)) {
        snackbarStore.addFailure(`${message.statusCode}, ${message.errorMessage}`);
        return {
            type: LoadableEventTag.ERROR
        };
    } else {
        return {
            type: LoadableEventTag.CONTENT,
            content: message as T
        }
    }
}