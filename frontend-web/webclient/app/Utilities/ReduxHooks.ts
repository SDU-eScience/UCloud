import {HookStore, ReduxObject} from "DefaultObjects";
import {useDispatch, useSelector} from "react-redux";
import {useCallback} from "react";

interface GenericSetAction {
    type: "GENERIC_SET";
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

const reducer = (state: HookStore = {}, action: GenericSetAction): HookStore => {
    switch (action.type) {
        case "GENERIC_SET":
            const newState = {...state};
            newState[action.property] = action.newValue;
            return newState;

        default: {
            return state;
        }
    }
};

export default reducer;
