import {Client} from "@/Authentication/HttpClientInstance";
import {useCallback, useEffect, useReducer, useRef, useState} from "react";
import {capitalize, defaultErrorHandler, removeTrailingSlash, timestampUnixMs} from "@/UtilityFunctions";
import {useGlobal, ValueOrSetter} from "@/Utilities/ReduxHooks";
import {HookStore} from "@/DefaultObjects";
import {usePromiseKeeper} from "@/PromiseKeeper";
import {buildQueryString} from "@/Utilities/URIUtilities";
import * as React from "react";

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
            removeTrailingSlash(baseContext) + "/browse" + (subResource ? capitalize(subResource) : ""),
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
            removeTrailingSlash(baseContext) + "/retrieve" + (subResource ? capitalize(subResource) : ""),
            request
        ),
        parameters: request
    };
}
export function apiSearch<R>(request: R, baseContext: string, subResource?: string): APICallParameters<R> {
    return {
        context: "",
        method: "POST",
        path: removeTrailingSlash(baseContext) + "/search" + (subResource ? capitalize(subResource) : ""),
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

export function mapCallState<T, T2>(state: APICallState<T>, mapper: (t: T) => T2): APICallState<T2> {
    return {
        ...state,
        data: mapper(state.data)
    };
}

export async function callAPI<T>(parameters: APICallParameters<unknown, T>): Promise<T> {
    if (window["forceApiFailure"] === true) {
        return Promise.reject({ request: { status: 500 }, response: {} });
    }

    const method = parameters.method !== undefined ? parameters.method : "GET";
    if (parameters.path === undefined) throw Error("Missing path");
    return (await Client.call({
        method,
        path: parameters.path,
        body: parameters.payload,
        context: parameters.context,
        withCredentials: parameters.withCredentials,
        projectOverride: parameters.projectOverride,
        accessTokenOverride: parameters.accessTokenOverride,
    })).response;
}

export async function callAPIWithErrorHandler<T>(
    parameters: APICallParameters
): Promise<T | null> {
    try {
        return await callAPI<T>(parameters);
    } catch (e) {
        defaultErrorHandler(e);
        return null;
    }
}

export function useGlobalCloudAPI<T, Parameters = any>(
    property: string,
    callParametersInitial: APICallParameters<Parameters>,
    dataInitial: T
): [APICallState<T>, (params: APICallParameters<Parameters>) => void, APICallParameters<Parameters>] {
    const defaultState: APICallStateWithParams<T, Parameters> = {
        call: {
            loading: false,
            error: undefined,
            data: dataInitial
        }, parameters: callParametersInitial
    };

    const promises = usePromiseKeeper();

    const [globalState, setGlobalState] =
        useGlobal(property as keyof HookStore, defaultState as unknown as NonNullable<HookStore[keyof HookStore]>);
    const state = globalState as unknown as APICallStateWithParams<T, Parameters>;
    const setState = setGlobalState as unknown as (value: ValueOrSetter<APICallStateWithParams<T, Parameters>>) => void;

    const doFetch = useCallback(async (parameters: APICallParameters) => {
        if (promises.canceledKeeper) return;
        setState(old => ({...old, parameters}));
        if (parameters.noop !== true) {
            if (parameters.path !== undefined) {
                if (promises.canceledKeeper) return;
                setState(old => ({...old, call: {...old.call, loading: true}}));

                try {
                    const result: T = await callAPI(parameters);

                    if (promises.canceledKeeper) return;
                    setState(old => ({...old, call: {...old.call, loading: false, data: result}}));
                } catch (e) {
                    const statusCode = e.request.status;
                    const why = e.response?.why ?? "An error occurred. Please reload the page.";
                    if (promises.canceledKeeper) return;
                    setState(old => ({...old, call: {...old.call, loading: false, error: {why, statusCode}}}));
                }
            }
        }
    }, [promises, setState]);

    return [state.call, doFetch, state.parameters];
}

/**
 * @deprecated
 */
export function useAsyncCommand(): [boolean, InvokeCommand, React.RefObject<boolean>] {
    return useCloudCommand();
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

export function useCloudCache(): CloudCacheHook {
    const [cache, setCache, mergeCache] = useGlobal("cloudApiCache", {});

    const cleanup = useCallback(() => {
        setCache((oldCache) => {
            if (oldCache === undefined) return {};
            const now = timestampUnixMs();

            const newCache = {...oldCache};
            for (const key of Object.keys(oldCache)) {
                if (now > oldCache[key].expiresAt) {
                    delete newCache[key];
                }
            }

            return newCache;
        });
    }, []);

    const removePrefix = useCallback((prefix: string) => {
        setCache((oldCache) => {
            if (oldCache === undefined) return {};
            const now = timestampUnixMs();

            const newCache = {...oldCache};
            for (const key of Object.keys(oldCache)) {
                if (now > oldCache[key].expiresAt) {
                    delete newCache[key];
                } else {
                    const parsedKey = JSON.parse(key) as APICallParameters;
                    if (parsedKey.path?.indexOf(prefix) === 0) {
                        delete newCache[key];
                    }
                }
            }

            return newCache;
        });
    }, []);

    return {cleanup, removePrefix};
}

export type APIFetch<Parameters> = (params: APICallParameters<Parameters>, disableCache?: boolean) => Promise<void>;

export function useCloudAPI<T, Parameters = any>(
    callParametersInitial: APICallParameters<Parameters, T>,
    dataInitial: T,
    cachingPolicy?: {cacheTtlMs: number, cacheKey: string}
): [APICallState<T>, APIFetch<Parameters>, APICallParameters<Parameters>] {
    const parameters = useRef(callParametersInitial);
    const initialCall = useRef(true);
    const lastKey = useRef("");
    const [cache, , mergeCache] = useGlobal("cloudApiCache", {}, (oldCache, newCache) => {
        let cacheKey = cachingPolicy?.cacheKey;
        if (cacheKey === undefined) return true; // Don't give us the update
        cacheKey += "/";

        if (oldCache === newCache) return true;

        for (const key of Object.keys(newCache)) {
            if (key.indexOf(cacheKey) === 0) {
                if (newCache[key] !== oldCache[key] && key != lastKey.current) {
                    return false;
                }
            }
        }

        return true;
    });

    const [state, dispatch] = useReducer(dataFetchReducer, {
        loading: false,
        error: undefined,
        data: dataInitial
    });

    const refetch = useCallback(async (params: APICallParameters<Parameters, T>) => {
        let didCancel = false;

        let key: string | null = null;
        if (cachingPolicy !== undefined) {
            const keyObj = {...params};
            delete keyObj.reloadId;
            key = cachingPolicy.cacheKey + "/" + JSON.stringify(keyObj);
            lastKey.current = key;
        }

        const now = timestampUnixMs();
        const cachedEntry = key ? cache[key] : null;

        // NOTE: We only cache successful attempts
        if (cachedEntry && now < cachedEntry.expiresAt && !params.disableCache) {
            dispatch({type: "FETCH_SUCCESS", payload: cachedEntry});
            return;
        }

        if (params.noop !== true) {
            // eslint-disable-next-line no-inner-declarations
            async function fetchData(): Promise<void> {
                if (params.path !== undefined) {
                    dispatch({type: "FETCH_INIT"});

                    try {
                        const result: T = await callAPI(params);
                        if (!didCancel) {
                            dispatch({type: "FETCH_SUCCESS", payload: result});
                            if (cachingPolicy !== undefined && cachingPolicy.cacheTtlMs > 0 && params.method === "GET") {
                                const newEntry = {};
                                newEntry[key!] = {expiresAt: now + cachingPolicy.cacheTtlMs, ...result};
                                mergeCache(newEntry);
                            }
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
    }, [cache]);

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
