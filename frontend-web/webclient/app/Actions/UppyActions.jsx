import {CHANGE_UPPY_OPEN} from "../Reducers/UppyReducers";

export const changeUppyOpen = (open) => {
    return {
        type: CHANGE_UPPY_OPEN,
        open,
    }
}