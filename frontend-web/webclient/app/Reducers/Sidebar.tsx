export const SET_SIDEBAR_LOADING = "SET_SIDEBAR_LOADING";
export const RECEIVE_SIDEBAR_OPTIONS = "RECEIVE_SIDEBAR_OPTIONS";
export const SET_SIDEBAR_OPEN = "SET_SIDEBAR_OPEN";
export const SET_SIDEBAR_CLOSED = "SET_SIDEBAR_CLOSED";

const sidebar = (state: any = {}, action) => {
    switch (action.type) {
        case SET_SIDEBAR_LOADING:
            return { ...state, loading: action.loading }
        case RECEIVE_SIDEBAR_OPTIONS:
            return { ...state, loading: false, options: action.options }
        case SET_SIDEBAR_OPEN: {
            return { ...state, open: !state.open }
        }
        case SET_SIDEBAR_CLOSED: {
            return { ...state, open: false }
        }
        default: {
            return state;
        }
    }
}

export default sidebar;