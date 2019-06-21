import { ActivityReduxObject, initActivity } from "DefaultObjects";
import { ActivityActions, SET_ACTIVITY_ERROR_MESSAGE, RECEIVE_ACTIVITY, SET_ACTIVITY_LOADING, RESET_ACTIVITY, UPDATE_ACTIVITY_FILTER } from "./ActivityActions";
import { concatScrolls } from "Scroll/Types";

const activity = (state: ActivityReduxObject = initActivity(), action: ActivityActions): ActivityReduxObject => {
    switch (action.type) {
        case RESET_ACTIVITY:
            return { ...state, scroll: undefined, loading: false };
        case SET_ACTIVITY_ERROR_MESSAGE:
            return { ...state, loading: false };
        case RECEIVE_ACTIVITY:
            const incoming = action.payload.page
            return { ...state, scroll: concatScrolls(incoming, state.scroll), loading: false };
        case SET_ACTIVITY_LOADING:
            return { ...state, loading: true };
        case UPDATE_ACTIVITY_FILTER:
            return { ...state, ...action.payload.filter };
        default:
            return state;
    }
}

export default activity;