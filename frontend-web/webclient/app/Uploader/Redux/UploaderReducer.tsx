export const SET_UPLOADER_CALLBACK = "SET_UPLOADER_CALLBACK";
export const SET_UPLOADER_UPLOADS = "SET_UPLOADER_UPLOADS";
export const SET_UPLOADER_VISIBLE = "SET_UPLOADER_VISIBLE";
import { UploaderActions } from "./UploaderActions";
import { UploaderReduxObject, initUploads } from "DefaultObjects";
/* type UploaderActions = SetUploaderVisibleProps | SetUploadsProps | SetUploaderCallbackProps */


const uploader = (state: UploaderReduxObject = initUploads(), action: UploaderActions): UploaderReduxObject => {
    switch (action.type) {
        case SET_UPLOADER_UPLOADS:
        case SET_UPLOADER_VISIBLE:
        case SET_UPLOADER_CALLBACK: {
            return { ...state, ...action.payload }
        }
        // REQUIRED BY REDUX, WILL OVERWRITE OTHERWISE
        default: {
            return state;
        }
    }
}

export default uploader;