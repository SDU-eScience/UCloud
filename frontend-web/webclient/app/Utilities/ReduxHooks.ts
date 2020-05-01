import {HookStore, ReduxObject} from "DefaultObjects";
import {useDispatch, useSelector} from "react-redux";
import {useCallback} from "react";

type Action = GenericSetAction | GenericMergeAction;

interface GenericSetAction {
    type: "GENERIC_SET";
    property: string;
    newValue?: any;
}

export interface GenericMergeAction {
    type: "GENERIC_MERGE",
    property: string;
    newValue?: any;
}

export function useGlobal<Property extends keyof HookStore>(
    property: Property,
    defaultValue: NonNullable<HookStore[Property]>
): [NonNullable<HookStore[Property]>, (newValue: HookStore[Property]) => void] {
    const value = useSelector<ReduxObject, HookStore[Property]>(it => {
        if (it.hookStore === undefined) return undefined;
        return it.hookStore[property];
    });
    const dispatch = useDispatch();
    const setter = useCallback((newValue: HookStore[Property]) => {
        dispatch<GenericSetAction>({type: "GENERIC_SET", property, newValue});
    }, [dispatch]);

    return [
        ((value === undefined || value === null) ? defaultValue : value) as NonNullable<HookStore[Property]>,
        setter
    ];
}

/**
 * Similar to useGlobal but merges previous value with new value. Keys in the new object take precedense over old values.
 */
export function useGlobalWithMerge<Property extends keyof HookStore>(
    property: Property,
    defaultValue: NonNullable<HookStore[Property]>
): [NonNullable<HookStore[Property]>, (newValue: HookStore[Property]) => void] {
    const value = useSelector<ReduxObject, HookStore[Property]>(it => {
        if (it.hookStore === undefined) return undefined;
        return it.hookStore[property];
    });
    const dispatch = useDispatch();
    const setter = useCallback((newValue: HookStore[Property]) => {
        dispatch<Action>({type: "GENERIC_MERGE", property, newValue});
    }, [dispatch]);

    return [
        ((value === undefined || value === null) ? defaultValue : value) as NonNullable<HookStore[Property]>,
        setter
    ];
}

const reducer = (state: HookStore = {}, action: Action): HookStore => {
    switch (action.type) {
        case "GENERIC_SET":
            const newState = {...state};
            newState[action.property] = action.newValue;
            return newState;

        case "GENERIC_MERGE":
            const stateCopy = {...state};
            stateCopy[action.property] = {...stateCopy[action.property], ...action.newValue};
            return stateCopy;

        default: {
            return state;
        }
    }
};

export default reducer;
