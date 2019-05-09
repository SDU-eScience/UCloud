import { AlertsAction } from "./AlertsActions";
import { Reducer as ReduxReducer } from "redux";

export const SET_ALERT_VISIBLE = "SET_ALERT_VISIBLE";
export const SET_ALERT_NODE = "SET_ALERT_NODE";

/* export interface Reducer {
    applicationView: ReduxReducer<Type>
}

const alerts: ReduxReducer<Type> = (state, action: AlertsAction) => {
    switch (action.type) {
        case SET_ALERT_VISIBLE:
        case SET_ALERT_NODE:
            return { ...state, ...action.payload };
        default:
            return state;
    }
} */