import {useDispatch, useSelector} from "react-redux";
import {useCallback} from "react";
import {ProjectCache} from "@/Project";
import * as AppStore from "@/Applications/AppStoreApi";
import {PayloadAction} from "@reduxjs/toolkit";

export interface HookStore {
    uploaderVisible?: boolean;
    uploadPath?: string;

    projectCache?: ProjectCache;
    catalogLandingPage?: AppStore.LandingPage;
    catalogDiscovery?: AppStore.CatalogDiscovery;
}

type Action = GenericSetAction | GenericMergeAction;

type GenericSetAction = PayloadAction<{
    property: string;
    newValue?: ValueOrSetter<any>;
    defaultValue: any;
}, "GENERIC_SET">

export type GenericMergeAction = PayloadAction<{
    property: string;
    newValue?: any;
}, "GENERIC_MERGE">

export type ValueOrSetter<T> = T | ((oldValue: T) => T);

export function useGlobal<Property extends keyof HookStore>(
    property: Property,
    defaultValue: NonNullable<HookStore[Property]>,
    equalityFn?: (left: NonNullable<HookStore[Property]>, right: NonNullable<HookStore[Property]>) => boolean
): [NonNullable<HookStore[Property]>, (newValue: ValueOrSetter<HookStore[Property]>) => void, (newValue: Partial<HookStore[Property]>) => void] {
    /* FIXME: this hook causes memory leaks */
    const value = useSelector<ReduxObject, HookStore[Property]>(it => {
        if (it.hookStore === undefined) return undefined;
        return it.hookStore[property];
    }, equalityFn);
    /* FIXME END */
    const dispatch = useDispatch();
    const setter = useCallback((newValue: HookStore[Property]) => {
        dispatch<GenericSetAction>({type: "GENERIC_SET", payload: {property, newValue, defaultValue}});
    }, [dispatch]);

    const merger = useCallback((newValue: HookStore[Property]) => {
        dispatch<Action>({type: "GENERIC_MERGE", payload: {property, newValue}});
    }, [dispatch]);

    return [
        ((value === undefined || value === null) ? defaultValue : value) as NonNullable<HookStore[Property]>,
        setter,
        merger
    ];
}

function reducer(state: HookStore = {}, action: Action): HookStore {
    switch (action.type) {
        case "GENERIC_SET": {
            const newState = {};
            for (const kv of Object.entries(state)) {
                const [key, val] = kv;
                newState[key] = val;
            }
            if (typeof action.payload.newValue === "function") {
                newState[action.payload.property] = action.payload.newValue(newState[action.payload.property] ?? action.payload.defaultValue);
            } else {
                newState[action.payload.property] = action.payload.newValue;
            }
            return newState;
        }

        case "GENERIC_MERGE": {
            const stateCopy = {};
            for (const kv of Object.entries(state)) {
                const [key, val] = kv;
                stateCopy[key] = val;
            }
            stateCopy[action.payload.property] = {...stateCopy[action.payload.property], ...action.payload.newValue};
            return stateCopy;
        }

        default: {
            return state;
        }
    }
}

export default reducer;
