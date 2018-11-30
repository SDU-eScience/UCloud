import { FileInfoReduxObject, initFileInfo } from "DefaultObjects";
import { FileInfoActions } from "./FileInfoActions";

export const RECEIVE_FILE_STAT = "RECEIVE_FILE_STAT";
export const FILE_INFO_ERROR = "FILE_INFO_ERROR";
export const SET_FILE_INFO_LOADING = "SET_FILE_INFO_LOADING";
export const RECEIVE_FILE_ACTIVITY = "RECEIVE_FILE_ACTIVITY";

const fileInfo = (state: FileInfoReduxObject = initFileInfo(), action: FileInfoActions) => {
    switch (action.type) {
        case RECEIVE_FILE_STAT:
        case FILE_INFO_ERROR:
        case SET_FILE_INFO_LOADING:
        case RECEIVE_FILE_ACTIVITY:
            return { ...state, ...action.payload };
        default:
            return state;
    }
};

export default fileInfo;