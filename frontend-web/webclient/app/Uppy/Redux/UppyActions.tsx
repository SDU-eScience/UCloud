import { OPEN_UPPY } from "./UppyReducers";

/**
 * Used to open Uppy modal
 */
export const openUppy = (open: boolean) => ({
    type: OPEN_UPPY,
    open
});

/**
 * Closes the uppy window, and cleans up settings that would interfere with next upload
 * @param {Uppy} uppy an instance of Uppy
 */
export const closeUppy = (uppy: any) => {
    // TODO Gigantic hack to remove all (non-uppy) callbacks when closed
    if (uppy.emitter._fns["upload-success"])
        uppy.emitter._fns["upload-success"] = uppy.emitter._fns["upload-success"].slice(0, 1);
    return openUppy(false);
};