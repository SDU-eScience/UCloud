import { initStatus, StatusReduxObject } from "DefaultObjects";
import { StatusActions } from "./StatusActions";

export const UPDATE_PAGE_TITLE = "UPDATE_PAGE_TITLE";
export const UPDATE_STATUS = "UPDATE_STATUS";

const status = (state: StatusReduxObject = initStatus(), action: StatusActions): StatusReduxObject => {
    switch (action.type) {
        case UPDATE_PAGE_TITLE: {
            document.title = `SDUCloud | ${action.payload.title}`;
            return { ...state, ...action.payload };
        }
        case UPDATE_STATUS: {
            return { ...state, ...action.payload };
        }
        default: {
            return state;
        }
    }
};

export default status;