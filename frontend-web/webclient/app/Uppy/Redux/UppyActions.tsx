import { CHANGE_UPPY_RUNAPP_OPEN, CHANGE_UPPY_FILES_OPEN, CLOSE_UPPY } from "./UppyReducers";
import { Action } from "Types";

// FIXME Is this even in use?
interface ChangeUppyOpenAction extends Action { open: boolean }
/**
 * Used to set whether or not the Uppy modal is shown for files
 * @param {boolean} open sets whether or not uppy is open
 */
export const changeUppyFilesOpen = (open: boolean): ChangeUppyOpenAction => ({
    type: CHANGE_UPPY_FILES_OPEN,
    open
});

/**
 * Used to set whether or not the Uppy modal is shown for RunApp
 * @param {boolean} open sets whether or not uppy is open
 */
export const changeUppyRunAppOpen = (open: boolean): ChangeUppyOpenAction => ({
    type: CHANGE_UPPY_RUNAPP_OPEN,
    open
});

/**
 * Closes the uppy window, and cleans up settings that would interfere with next
 * upload
 * @param {Uppy} uppy an instance of Uppy
 */
export const closeUppy = (uppy: any) => {
    // TODO Gigantic hack to remove all (non-uppy) callbacks when closed
    uppy.emitter._fns["upload-success"] = uppy.emitter._fns["upload-success"].slice(0, 1);
    return {
        type: CLOSE_UPPY
    };
};