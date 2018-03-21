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

export const closeUppy = (uppy) => {
    // TODO Gigantic hack to remove all (non-uppy) callbacks when closed
    uppy.emitter._fns["upload-success"] = uppy.emitter._fns["upload-success"].slice(0, 1);
    return {
        type: CLOSE_UPPY
    };
};