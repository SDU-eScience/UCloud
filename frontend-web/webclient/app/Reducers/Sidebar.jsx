export const SET_SIDEBAR_LOADING = "SET_SIDEBAR_LOADING";
export const RECEIVE_SIDEBAR_OPTIONS = "RECEIVE_SIDEBAR_OPTIONS";

const sidebar = (state = [], action) => {
    switch (action.type) {
        case SET_SIDEBAR_LOADING:
            console.log(action);
            return { ...state, loading: action.loading }
        case RECEIVE_SIDEBAR_OPTIONS:
            console.log(action);
            return { ...state, loading: false, options: action.options }
        default: {
            return state;
        }
    }
}

export default sidebar;