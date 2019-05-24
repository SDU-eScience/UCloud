import {useEffect, useReducer, useState} from "react";
import {Cloud} from "Authentication/SDUCloudObject";
import {Snack} from "Snackbar/Snackbars";
import {defaultErrorHandler} from "UtilityFunctions";
import {GLOBAL_addSnack} from "App";

function dataFetchReducer(state, action) {
    switch (action.type) {
        case 'FETCH_INIT':
            return {
                ...state,
                loading: true,
                error: undefined
            };
        case 'FETCH_SUCCESS':
            return {
                ...state,
                loading: false,
                error: undefined,
                data: action.payload,
            };
        case 'FETCH_FAILURE':
            return {
                ...state,
                loading: false,
                error: action.error,
            };
        default:
            throw new Error();
    }
}

export interface APICallParameters<Parameters = any> {
    method?: "GET" | "POST" | "DELETE" | "PUT" | "PATCH" | "OPTIONS" | "HEAD"
    path?: string
    payload?: any
    context?: string
    maxRetries?: number
    parameters?: Parameters
    reloadId?: number // Can be used to force an ID by setting this to a random value
}

export interface APIError {
    why: string
    statusCode: number
}

export interface APICallState<T> {
    loading: boolean
    error?: APIError
    data: T
}

export function mapCallState<T, T2>(state: APICallState<T>, mapper: (T) => T2): APICallState<T2> {
    return {
        ...state,
        data: mapper(state.data)
    };
}

export async function callAPI<T>(parameters: APICallParameters): Promise<T> {
    let method = parameters.method !== undefined ? parameters.method : "GET";
    if (parameters.path === undefined) throw "Missing path";
    return (await Cloud.call(method, parameters.path, parameters.payload, parameters.context,
        parameters.maxRetries)).response;
}

export async function callAPIWithErrorHandler<T>(
    parameters: APICallParameters,
    addSnack: (snack: Snack) => void = GLOBAL_addSnack
): Promise<T | null> {
    try {
        return await callAPI(parameters);
    } catch (e) {
        defaultErrorHandler(e, addSnack);
        return null;
    }
}

export function useCloudAPI<T, Parameters = any>(
    callParametersInitial: APICallParameters<Parameters>,
    dataInitial: T
): [APICallState<T>, (params: APICallParameters) => void, APICallParameters<Parameters>] {
    const [params, setParams] = useState(callParametersInitial);

    const [state, dispatch] = useReducer(dataFetchReducer, {
        loading: false,
        error: undefined,
        data: dataInitial
    });

    useEffect(() => {
        let didCancel = false;

        async function fetchData() {
            if (params.path !== undefined) {
                dispatch({type: "FETCH_INIT"});

                try {
                    const result: T = await callAPI(params);

                    if (!didCancel) {
                        dispatch({type: "FETCH_SUCCESS", payload: result});
                    }
                } catch (e) {
                    if (!didCancel) {
                        let statusCode = e.request.status;
                        let why = "Internal Server Error";
                        if (!!e.response && e.response.why) {
                            why = e.response.why;
                        }

                        dispatch({type: "FETCH_FAILURE", error: {why, statusCode}});
                    }
                }
            }
        }

        fetchData();

        return () => {
            didCancel = true;
        };
    }, [params]);

    function doFetch(params: APICallParameters) {
        setParams(params);
    }

    const returnedState: APICallState<T> = {...state};
    return [returnedState, doFetch, params];
}

export function useAsyncCommand(addSnack: (snack: Snack) => void = GLOBAL_addSnack): [boolean, (call: APICallParameters) => void] {
    const [isLoading, setIsLoading] = useState(false);
    const sendCommand = async (call: APICallParameters) => {
        setIsLoading(true);
        await callAPIWithErrorHandler(call, addSnack);
        setIsLoading(false);
    };

    return [isLoading, sendCommand];
}

