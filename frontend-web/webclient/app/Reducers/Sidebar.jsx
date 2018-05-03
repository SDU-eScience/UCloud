export const SET_SIDEBAR_LOADING = "SET_SIDEBAR_LOADING";
export const RECEIVE_SIDEBAR_OPTIONS = "RECEIVE_SIDEBAR_OPTIONS";
export const SET_SIDEBAR_OPEN = "SET_SIDEBAR_OPEN";

const sidebar = (state = [], action) => {
    switch (action.type) {
        case SET_SIDEBAR_LOADING:
            return { ...state, loading: action.loading }
        case RECEIVE_SIDEBAR_OPTIONS:
            return { ...state, loading: false, options: action.options }
        case SET_SIDEBAR_OPEN: {
            window.localStorage.setItem("sidebar-open", action.state);
            return { ...state, open: !state.open }
        }
        default: {
            return state;
        }
    }
}

export default sidebar;