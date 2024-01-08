import {Client} from "@/Authentication/HttpClientInstance";
import {useCallback, useEffect, useReducer, useRef, useState} from "react";
import {defaultErrorHandler, removeTrailingSlash} from "@/UtilityFunctions";
import * as React from "react";
import * as Messages from "@/UCloud/Messages";
import {BufferAndOffset, loadMessage} from "@/UCloud/Messages";
import {buildQueryString} from "@/Utilities/URIUtilities";

const capitalized = (str: string): string => str.charAt(0).toUpperCase() + str.slice(1);

function dataFetchReducer<T>(state: APICallState<T>, action): APICallState<T> {
    switch (action.type) {
        case "FETCH_INIT":
            return {
                ...state,
                loading: true,
                error: undefined
            };
        case "FETCH_SUCCESS":
            return {
                ...state,
                loading: false,
                error: undefined,
                data: action.payload,
            };
        case "FETCH_FAILURE":
            return {
                ...state,
                data: action.data,
                loading: false,
                error: action.error
            };
        default:
            throw new Error();
    }
}

declare global {
    export interface APICallParameters<Parameters = any, Response = any> {
        method?: "GET" | "POST" | "DELETE" | "PUT" | "PATCH" | "OPTIONS" | "HEAD";
        path?: string;
        payload?: any;
        context?: string;
        parameters?: Parameters;
        reloadId?: number; // Can be used to force an ID by setting this to a random value
        noop?: boolean; // Used to indicate that this should not be run in a useCloudAPI hook.
        withCredentials?: boolean;
        projectOverride?: string;
        disableCache?: boolean;
        accessTokenOverride?: string;
        unauthenticated?: boolean;
    }

    export interface APICallParametersBinary<Response> extends APICallParameters {
        responseConstructor: {new(b: BufferAndOffset): Response};
    }
}

export function apiCreate<R>(request: R, baseContext: string, subResource?: string): APICallParameters<R> {
    return {
        context: "",
        method: "POST",
        path: removeTrailingSlash(baseContext) + (subResource ? "/" + subResource : ""),
        parameters: request,
        payload: request
    };
}

export function apiBrowse<R extends Record<string, any>>(request: R, baseContext: string, subResource?: string): APICallParameters<R> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString(
            removeTrailingSlash(baseContext) + "/browse" + (subResource ? capitalized(subResource) : ""),
            request
        ),
        parameters: request
    };
}
export function apiRetrieve<R extends Record<string, any>>(request: R, baseContext: string, subResource?: string): APICallParameters<R> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString(
            removeTrailingSlash(baseContext) + "/retrieve" + (subResource ? capitalized(subResource) : ""),
            request
        ),
        parameters: request
    };
}
export function apiSearch<R>(request: R, baseContext: string, subResource?: string): APICallParameters<R> {
    return {
        context: "",
        method: "POST",
        path: removeTrailingSlash(baseContext) + "/search" + (subResource ? capitalized(subResource) : ""),
        parameters: request,
        payload: request
    };
}
export function apiUpdate<R>(request: R, baseContext: string, operation: string): APICallParameters<R> {
    return {
        context: "",
        method: "POST",
        path: removeTrailingSlash(baseContext) + "/" + operation,
        parameters: request,
        payload: request
    };
}
export function apiDelete<R>(request: R, baseContext: string): APICallParameters<R> {
    return {
        context: "",
        method: "DELETE",
        path: removeTrailingSlash(baseContext),
        parameters: request,
        payload: request
    };
}

export interface APIError {
    why: string;
    statusCode: number;
}

export interface APICallState<T> {
    loading: boolean;
    error?: APIError;
    data: T;
}

export interface APICallStateWithParams<T, Params = any> {
    call: APICallState<T>;
    parameters: APICallParameters<Params>;
}

// Unused
export function mapCallState<InputType, OutputType>(
    state: APICallState<InputType>, mapper: (t: InputType) => OutputType
): APICallState<OutputType> {
    return {
        ...state,
        data: mapper(state.data)
    };
}

export async function callAPI<T>(
    parameters: (APICallParameters<unknown, T> | APICallParametersBinary<T>)
): Promise<T> {
    if (window["forceApiFailure"] !== undefined) {
        return Promise.reject(window["forceApiFailure"]);
    }

    const method = parameters.method !== undefined ? parameters.method : "GET";
    if (parameters.path === undefined) throw Error("Missing path");
    let responseType: XMLHttpRequestResponseType = "text";
    let acceptType = "*/*";
    if ("responseConstructor" in parameters) {
        responseType = "arraybuffer";
        acceptType = Messages.contentType;
    }

    const res = (await Client.call({
        method,
        path: parameters.path,
        body: parameters.payload,
        context: parameters.context,
        withCredentials: parameters.withCredentials,
        projectOverride: parameters.projectOverride,
        accessTokenOverride: parameters.accessTokenOverride,
        unauthenticated: parameters.unauthenticated,
        responseType,
        acceptType,
    })).response;

    if ("responseConstructor" in parameters) {
        return loadMessage(parameters.responseConstructor, new DataView(res));
    }
    return res;
}

export async function callAPIWithErrorHandler<T>(
    parameters: APICallParameters<unknown, T>
): Promise<T | null> {
    try {
        return await callAPI<T>(parameters);
    } catch (e) {
        defaultErrorHandler(e);
        return null;
    }
}

export type InvokeCommand = <T = any>(
    call: APICallParameters,
    opts?: {defaultErrorHandler: boolean}
) => Promise<T | null>;

export function useCloudCommand(): [boolean, InvokeCommand, React.RefObject<boolean>] {
    const [isLoading, setIsLoading] = useState(false);
    const loadingRef = useRef(false);
    let didCancel = false;
    const sendCommand: InvokeCommand = useCallback(<T>(call, opts = {defaultErrorHandler: true}): Promise<T | null> => {
        // eslint-disable-next-line no-async-promise-executor
        return new Promise<T | null>(async (resolve, reject) => {
            if (didCancel) return;

            setIsLoading(true);
            loadingRef.current = true;
            if (opts.defaultErrorHandler) {
                try {
                    const result = await callAPIWithErrorHandler<T>(call);
                    if (!didCancel) {
                        resolve(result);
                    }
                } catch (e) {
                    if (!didCancel) {
                        reject(e);
                    }
                }
            } else {
                try {
                    const result = await callAPI<T>(call);
                    if (!didCancel) {
                        resolve(result);
                    }
                } catch (e) {
                    if (!didCancel) {
                        reject(e);
                    }
                }
            }

            setIsLoading(false);
            loadingRef.current = false;
        });
    }, [setIsLoading]);

    useEffect(() => {
        didCancel = false;
        return () => {
            didCancel = true;
        };
    }, []);

    return [isLoading, sendCommand, loadingRef];
}

export type AsyncWorker = [boolean, string | undefined, (fn: () => Promise<void>) => void];

// unused
export function useAsyncWork(): AsyncWorker {
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState<string | undefined>(undefined);
    let didCancel = false;
    useEffect(() => {
        return () => {
            didCancel = true;
        };
    }, []);

    const startWork = async (fn: () => Promise<void>): Promise<void> => {
        if (didCancel) return;
        setError(undefined);
        setIsLoading(true);
        try {
            await fn();
        } catch (e) {
            if (didCancel) return;
            if (e.request) {
                const why = e.response?.why ?? e.request.statusText;
                setError(why);
            } else if (typeof e === "string") {
                setError(e);
            } else if ("message" in e && typeof e.message === "string") {
                setError(e.message);
            } else {
                setError("Internal error");
            }
        }
        if (!didCancel) setIsLoading(false);
    };

    return [isLoading, error, startWork];
}

export interface CloudCacheHook {
    cleanup: () => void;

    removePrefix(pathPrefix: string): void;
}

export type APIFetch<Parameters> = (params: APICallParameters<Parameters>, disableCache?: boolean) => Promise<void>;

export function useCloudAPI<T, Parameters = any>(
    callParametersInitial: APICallParameters<Parameters, T>,
    dataInitial: T,
): [APICallState<T>, APIFetch<Parameters>, APICallParameters<Parameters>] {
    const parameters = useRef(callParametersInitial);
    const initialCall = useRef(true);
    const lastKey = useRef("");

    const [state, dispatch] = useReducer(dataFetchReducer, {
        loading: false,
        error: undefined,
        data: dataInitial
    });

    const refetch = useCallback(async (params: APICallParameters<Parameters, T>) => {
        let didCancel = false;

        if (params.noop !== true) {
            // eslint-disable-next-line no-inner-declarations
            async function fetchData(): Promise<void> {
                if (params.path !== undefined) {
                    dispatch({type: "FETCH_INIT"});

                    try {
                        const result: T = await callAPI(params);
                        if (!didCancel) {
                            dispatch({type: "FETCH_SUCCESS", payload: result});
                        }
                    } catch (e) {
                        if (!didCancel) {
                            const statusCode = e.request.status;
                            const why = e.response?.why ?? "An error occurred. Please reload the page.";
                            dispatch({type: "FETCH_FAILURE", data: dataInitial, error: {why, statusCode}});
                        }
                    }
                }
            }
            await fetchData();
        }

        return () => {
            didCancel = true;
        };
    }, []);

    const doFetch = useCallback(async (params: APICallParameters, disableCache = false): Promise<void> => {
        parameters.current = params;
        if (disableCache) {
            await refetch({...params, disableCache: true});
        } else {
            await refetch(params);
        }
    }, [refetch]);

    if (initialCall.current) {
        initialCall.current = false;
        refetch(parameters.current);
    }

    return [state as APICallState<T>, doFetch, parameters.current];
}

export function noopCall<T>(): APICallParameters<T> {
    return {noop: true};
}
