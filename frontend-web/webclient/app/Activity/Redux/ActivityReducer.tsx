export const RECEIVE_ACTIVITY = "RECEIVE_ACTIVITY";
export const SET_ACTIVITY_LOADING = "SET_ACTIVITY_LOADING";
export const SET_ACTIVITY_ERROR_MESSAGE = "SET_ACTIVITY_ERROR_MESSAGE";

const activity = (state: any = {}, action) => {
    switch (action.type) {
        case RECEIVE_ACTIVITY: {
            return { ...state, page: action.activity, loading: false };
        }
        case SET_ACTIVITY_LOADING: {
            return { ...state, loading: true };
        }
        case SET_ACTIVITY_ERROR_MESSAGE: {
            return { ...state, error: action.error };
        }
        default: {
            return state;
        }
    }
}

export default activity;