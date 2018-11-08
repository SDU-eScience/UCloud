import { ActivityReduxObject, initActivity } from "DefaultObjects";
import { ActivityActions } from "./ActivityActions";

export const RECEIVE_ACTIVITY = "RECEIVE_ACTIVITY";
export const SET_ACTIVITY_LOADING = "SET_ACTIVITY_LOADING";
export const SET_ACTIVITY_ERROR_MESSAGE = "SET_ACTIVITY_ERROR_MESSAGE";

const activity = (state: ActivityReduxObject = initActivity(), action: ActivityActions): ActivityReduxObject => {
    switch (action.type) {
        case SET_ACTIVITY_ERROR_MESSAGE:
        case RECEIVE_ACTIVITY:
            return { ...state, ...action.payload, loading: false };
        case SET_ACTIVITY_LOADING:
            return { ...state, loading: true };
        default:
            return state;
    }
}

export default activity;