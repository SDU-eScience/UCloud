import {useEffect, useReducer, useState} from "react";
import {Cloud} from "Authentication/SDUCloudObject";

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

export interface APICallParameters {
    method: "GET" | "POST" | "DELETE" | "PUT" | "PATCH" | "OPTIONS" | "HEAD"
    path: string
    payload?: any
    context?: string
    maxRetries?: number
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

export function useCloudAPI<T>(callParametersInitial: APICallParameters, dataInitial: T): [APICallState<T>, (APICallParameters) => void] {
    const [params, setParams] = useState(callParametersInitial);

    const [state, dispatch] = useReducer(dataFetchReducer, {
        loading: false,
        error: false,
        data: dataInitial
    });

    useEffect(() => {
        let didCancel = false;

        async function fetchData() {
            dispatch({type: "FETCH_INIT"});

            try {
                const result: T = await Cloud.call(params.method, params.path, params.payload, params.context,
                    params.maxRetries);

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

        fetchData();

        return () => {
            didCancel = true;
        };
    }, [params]);

    function doFetch(params: APICallParameters) {
        setParams(params);
    }

    const returnedState: APICallState<T> = {...state};
    return [returnedState, doFetch];
}