import { SidebarReduxObject, initSidebar } from "DefaultObjects";
import { SidebarActions } from "./SidebarActions";

export const SET_SIDEBAR_LOADING = "SET_SIDEBAR_LOADING";
export const RECEIVE_SIDEBAR_OPTIONS = "RECEIVE_SIDEBAR_OPTIONS";
export const SET_SIDEBAR_STATE = "SET_SIDEBAR_OPEN";

const sidebar = (state: SidebarReduxObject = initSidebar(), action: SidebarActions): SidebarReduxObject => {
    switch (action.type) {
        case SET_SIDEBAR_STATE: {
            return { ...state, ...action.payload }
        }
        default: {
            return state;
        }
    }
}

export default sidebar;