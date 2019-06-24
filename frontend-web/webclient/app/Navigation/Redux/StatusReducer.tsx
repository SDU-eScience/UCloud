import {initStatus, StatusReduxObject} from "DefaultObjects";
import {StatusActions} from "./StatusActions";

export const UPDATE_PAGE_TITLE = "UPDATE_PAGE_TITLE";
export const UPDATE_STATUS = "UPDATE_STATUS";
export const SET_ACTIVE_PAGE = "SET_ACTIVE_PAGE";
export const SET_STATUS_LOADING = "SET_STATUS_LOADING";

const status = (state: StatusReduxObject = initStatus(), action: StatusActions): StatusReduxObject => {
    switch (action.type) {
        case UPDATE_PAGE_TITLE:
            document.title = `SDUCloud | ${action.payload.title}`;
            return {...state, ...action.payload};
        case UPDATE_STATUS:
        case SET_STATUS_LOADING:
        case SET_ACTIVE_PAGE:
            return {...state, ...action.payload};
        default: {
            return state;
        }
    }
};

export default status;