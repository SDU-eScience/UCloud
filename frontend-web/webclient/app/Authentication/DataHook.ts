import {Client} from "Authentication/HttpClientInstance";
import {useCallback, useEffect, useMemo, useReducer, useState} from "react";
import {defaultErrorHandler} from "UtilityFunctions";
import {useGlobal, ValueOrSetter} from "Utilities/ReduxHooks";
import {HookStore} from "DefaultObjects";
import {usePromiseKeeper} from "PromiseKeeper";

function dataFetchReducer(state, action) {
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
    export interface APICallParameters<Parameters = any, Payload = any> {
        method?: "GET" | "POST" | "DELETE" | "PUT" | "PATCH" | "OPTIONS" | "HEAD";
        path?: string;
        payload?: Payload;
        context?: string;
        maxRetries?: number;
        parameters?: Parameters;
        reloadId?: number; // Can be used to force an ID by setting this to a random value
        noop?: boolean; // Used to indicate that this should not be run in a useCloudAPI hook.
        withCredentials?: boolean;
        projectOverride?: string;
    }
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

export async function callAPI<T>(parameters: APICallParameters): Promise<T> {
    const method = parameters.method !== undefined ? parameters.method : "GET";
    if (parameters.path === undefined) throw Error("Missing path");
    return (await Client.call({
        method,
        path: parameters.path,
        body: parameters.payload,
        context: parameters.context,
        maxRetries: parameters.maxRetries,
        withCredentials: parameters.withCredentials,
        projectOverride: parameters.projectOverride
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

export function useCloudAPI<T, Parameters = any>(
    callParametersInitial: APICallParameters<Parameters>,
    dataInitial: T
): [APICallState<T>, (params: APICallParameters<Parameters>) => void, APICallParameters<Parameters>] {
    const [params, setParams] = useState(callParametersInitial);

    const [state, dispatch] = useReducer(dataFetchReducer, {
        loading: false,
        error: undefined,
        data: dataInitial
    });

    useEffect(() => {
        let didCancel = false;
        if (params.noop !== true) {
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
                            let why = "Internal Server Error";
                            if (!!e.response && e.response.why) {
                                why = e.response.why;
                            }

                            dispatch({type: "FETCH_FAILURE", data: dataInitial, error: {why, statusCode}});
                        }
                    }
                }
            }

            fetchData();
        }

        return () => {
            didCancel = true;
        };
    }, [params]);

    function doFetch(params: APICallParameters): void {
        setParams(params);
    }

    const returnedState: APICallState<T> = {...state};
    return [returnedState, doFetch, params];
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
                    let why = "Internal Server Error";
                    if (!!e.response && e.response.why) {
                        why = e.response.why;
                    }

                    if (promises.canceledKeeper) return;
                    setState(old => ({...old, call: {...old.call, loading: false, error: {why, statusCode}}}));
                }
            }
        }
    }, [promises, setState]);

    return [state.call, doFetch, state.parameters];
}

export function useAsyncCommand(): [boolean, <T = any>(call: APICallParameters) => Promise<T | null>] {
    const [isLoading, setIsLoading] = useState(false);
    let didCancel = false;
    const sendCommand = useCallback(<T>(call: APICallParameters): Promise<T | null> => {
        // eslint-disable-next-line no-async-promise-executor
        return new Promise<T | null>(async (resolve, reject) => {
            if (didCancel) return;

            setIsLoading(true);
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

            setIsLoading(false);
        });
    }, [setIsLoading]);

    useEffect(() => {
        return () => {
            didCancel = true;
        };
    }, []);

    return [isLoading, sendCommand];
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
            if (!!e.request) {
                let why = "Internal Server Error";
                if (!!e.response && e.response.why) {
                    why = e.response.why;
                } else {
                    why = e.request.statusText;
                }
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
