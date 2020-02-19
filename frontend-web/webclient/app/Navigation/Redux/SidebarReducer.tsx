import {initSidebar, SidebarReduxObject} from "DefaultObjects";
import {SidebarActions} from "./SidebarActions";

export const KC_SUCCESS = "KC_SUCCESS";

const sidebar = (state: SidebarReduxObject = initSidebar(), action: SidebarActions): SidebarReduxObject => {
    switch (action.type) {
        case KC_SUCCESS: {
            return {...state, ...action.payload};
        }
        default: {
            return state;
        }
    }
};

export default sidebar;
