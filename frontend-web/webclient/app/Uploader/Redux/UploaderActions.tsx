import { SET_UPLOADER_VISIBLE, SET_UPLOADER_UPLOADS } from "./UploaderReducer";
import { Upload } from "Uploader";
import { Action } from "redux";

interface SetUploaderVisibleProps extends Action { visible: boolean }
interface SetUploadsProps extends Action { uploads: Upload[] }


export const setUploaderVisible = (visible: boolean): SetUploaderVisibleProps => ({
    type: SET_UPLOADER_VISIBLE,
    visible
})

export const setUploads = (uploads: Upload[]): SetUploadsProps => ({
    type: SET_UPLOADER_UPLOADS,
    uploads
});  