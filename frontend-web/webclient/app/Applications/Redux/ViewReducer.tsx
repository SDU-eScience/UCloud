import { Reducer } from "redux";
import { Type, init } from "./ViewObject";
import * as Actions from "./ViewActions";

export interface Reducer {
    applicationView: Reducer<Type>
}

const reducer: Reducer<Type> = (state: Type = init().applicationView, action: Actions.Type): Type => {
    switch (action.type) {
        case Actions.Tag.SET_ERROR:
        case Actions.Tag.RECEIVE_APP: {
            return { ...state, ...action.payload, loading: false };
        }

        case Actions.Tag.SET_PREV_ERROR:
        case Actions.Tag.RECEIVE_PREVIOUS_VERSIONS: {
            return { ...state, ...action.payload, previousLoading: false };
        }
        
        case Actions.Tag.SET_PREV_LOADING:
        case Actions.Tag.SET_LOADING: {
            return { ...state, ...action.payload };
        }

        default: {
            return state;
        }
    }
}

export default reducer;