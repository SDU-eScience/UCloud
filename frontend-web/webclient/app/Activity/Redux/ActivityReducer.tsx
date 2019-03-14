import { ActivityReduxObject, initActivity } from "DefaultObjects";
import { ActivityActions } from "./ActivityActions";
import { concatScrolls } from "Scroll/Types";

export const RECEIVE_ACTIVITY = "RECEIVE_ACTIVITY";
export const SET_ACTIVITY_LOADING = "SET_ACTIVITY_LOADING";
export const SET_ACTIVITY_ERROR_MESSAGE = "SET_ACTIVITY_ERROR_MESSAGE";

const activity = (state: ActivityReduxObject = initActivity(), action: ActivityActions): ActivityReduxObject => {
    switch (action.type) {
        case SET_ACTIVITY_ERROR_MESSAGE:
            return { ...state, ...action.payload, loading: false };
        case RECEIVE_ACTIVITY:
            const incoming = action.payload.page
            return { ...state, page: concatScrolls(state.page, incoming), loading: false };
        case SET_ACTIVITY_LOADING:
            return { ...state, loading: true };
        default:
            return state;
    }
}

export default activity;