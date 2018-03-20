import {CHANGE_UPPY_OPEN, UPDATE_UPPY} from "../Reducers/UppyReducers";

export const changeUppyOpen = (open) => 
({
    type: CHANGE_UPPY_OPEN,
    open,
});

export const updateUppy = (uppy) => ({
    type: UPDATE_UPPY,
    uppy
})