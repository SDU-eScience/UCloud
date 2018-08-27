export const SET_SIDEBAR_LOADING = "SET_SIDEBAR_LOADING";
export const RECEIVE_SIDEBAR_OPTIONS = "RECEIVE_SIDEBAR_OPTIONS";
export const SET_SIDEBAR_STATE = "SET_SIDEBAR_OPEN";

const sidebar = (state: any = {}, action) => {
    switch (action.type) {
        case SET_SIDEBAR_STATE: {
            return { ...state, open: action.open }
        }
        default: {
            return state;
        }
    }
}

export default sidebar;