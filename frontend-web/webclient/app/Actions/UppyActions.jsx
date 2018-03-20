import {CHANGE_UPPY_RUNAPP_OPEN, CHANGE_UPPY_FILES_OPEN, CLOSE_UPPY, UPDATE_UPPY} from "../Reducers/UppyReducers";

export const changeUppyFilesOpen = (open) => 
({
    type: CHANGE_UPPY_FILES_OPEN,
    open,
});

export const changeUppyRunAppOpen = (open) => 
({
    type: CHANGE_UPPY_RUNAPP_OPEN,
    open,
});

export const closeUppy = () => ({
    type: CLOSE_UPPY
})