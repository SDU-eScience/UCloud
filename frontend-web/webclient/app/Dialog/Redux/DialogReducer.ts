import { DialogActions } from "./DialogActions";
import { Type, init } from "./DialogObject";
import { Reducer as ReduxReducer } from "redux";

export const SET_DIALOG_NODE = "SET_DIALOG_NODE";

export interface Reducer {
    dialog: ReduxReducer<Type>
}

const dialog: ReduxReducer<Type> = (state: Type = init().dialog, action: DialogActions): Type => {
    switch (action.type) {
        case SET_DIALOG_NODE:
            return { ...state, ...action.payload };
        default:
            return state;
    }
}

export default dialog;