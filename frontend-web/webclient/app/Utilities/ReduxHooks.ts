import {EqualityFn, useDispatch, useSelector} from "react-redux";
import {useCallback} from "react";
import {ProjectCache} from "@/Project";
import * as AppStore from "@/Applications/AppStoreApi";
import {createSlice, PayloadAction} from "@reduxjs/toolkit";

export interface HookStore {
    uploaderVisible?: boolean;
    uploadPath?: string;

    projectCache?: ProjectCache;
    catalogLandingPage?: AppStore.LandingPage;
    catalogDiscovery?: AppStore.CatalogDiscovery;

    sidebarWidth?: number;
    sidebarStickyWidth?: number;
}

function initialState(): HookStore {
    return {};
}

export type ValueOrSetter<T> = T | ((oldValue: T) => T);

export function useGlobal<Property extends keyof HookStore>(
    property: Property,
    defaultValue: NonNullable<HookStore[Property]>,
    equalityFn?: EqualityFn<HookStore[Property]>
): [NonNullable<HookStore[Property]>, (newValue: HookStore[Property]) => void, (newValue: HookStore[Property]) => void] {
    /* FIXME: this hook causes memory leaks */
    const value = useSelector<ReduxObject, HookStore[Property]>(it => {
        if (it.hookStore === undefined) return undefined;
        return it.hookStore[property];
    }, equalityFn);
    /* FIXME END */
    const dispatch = useDispatch();
    const setter = useCallback((newValue: HookStore[Property]) => {
        dispatch(genericSet({property, newValue, defaultValue}));
    }, [dispatch]);

    const merger = useCallback((newValue: HookStore[Property]) => {
        dispatch(genericMerge({property, newValue}));
    }, [dispatch]);

    return [
        ((value == null) ? defaultValue : value) as NonNullable<HookStore[Property]>,
        setter,
        merger
    ];
}

const hookStore = createSlice({
    name: "hookStore",
    initialState: initialState(),
    reducers: {
        genericSet(state, action: PayloadAction<{
            property: keyof HookStore;
            newValue?: ValueOrSetter<any>;
            defaultValue: any;
        }>) {
            if (typeof action.payload.newValue === "function") {
                state[action.payload.property] = action.payload.newValue(state[action.payload.property] ?? action.payload.defaultValue);
            } else {
                state[action.payload.property] = action.payload.newValue;
            }
        },

        genericMerge(state, action: PayloadAction<{
            property: string;
            newValue?: any;
        }>) {
            state[action.payload.property] = {...state[action.payload.property], ...action.payload.newValue};
        }
    }
});

export const {genericMerge, genericSet} = hookStore.actions;
export const hookStoreReducer = hookStore.reducer;
