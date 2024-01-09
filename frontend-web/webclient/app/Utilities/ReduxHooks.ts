import {useDispatch, useSelector} from "react-redux";
import {useCallback} from "react";
import {Upload} from "@/Files/Upload";
import {ProjectCache} from "@/Project/cache";

export interface HookStore {
    uploaderVisible?: boolean;
    uploads?: Upload[];
    uploadPath?: string;

    projectCache?: ProjectCache;
    frameHidden?: boolean;

    mainContainerHeaderSize?: number;
}

type Action = GenericSetAction | GenericMergeAction;

interface GenericSetAction {
    type: "GENERIC_SET";
    property: string;
    newValue?: ValueOrSetter<any>;
    defaultValue: any;
}

export interface GenericMergeAction {
    type: "GENERIC_MERGE",
    property: string;
    newValue?: any;
}

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
        dispatch<GenericSetAction>({type: "GENERIC_SET", property, newValue, defaultValue});
    }, [dispatch]);

    const merger = useCallback((newValue: HookStore[Property]) => {
        dispatch<Action>({type: "GENERIC_MERGE", property, newValue});
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
            if (typeof action.newValue === "function") {
                newState[action.property] = action.newValue(newState[action.property] ?? action.defaultValue);
            } else {
                newState[action.property] = action.newValue;
            }
            return newState;
        }

        case "GENERIC_MERGE": {
            const stateCopy = {};
            for (const kv of Object.entries(state)) {
                const [key, val] = kv;
                stateCopy[key] = val;
            }
            stateCopy[action.property] = {...stateCopy[action.property], ...action.newValue};
            return stateCopy;
        }

        default: {
            return state;
        }
    }
}

export default reducer;
