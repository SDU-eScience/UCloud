export const SET_UPLOADER_CALLBACK = "SET_UPLOADER_CALLBACK";
export const SET_UPLOADER_UPLOADS = "SET_UPLOADER_UPLOADS";
export const SET_UPLOADER_VISIBLE = "SET_UPLOADER_VISIBLE";
export const SET_UPLOADER_ERROR = "SET_UPLOADER_ERROR";
export const SET_UPLOADER_LOADING = "SET_UPLOADER_LOADING";
import { UploaderActions } from "./UploaderActions";
import { UploaderReduxObject, initUploads } from "DefaultObjects";



const uploader = (state: UploaderReduxObject = initUploads(), action: UploaderActions): UploaderReduxObject => {
    switch (action.type) {
        case SET_UPLOADER_ERROR:
        case SET_UPLOADER_UPLOADS:
        case SET_UPLOADER_VISIBLE:
        case SET_UPLOADER_LOADING:
        case SET_UPLOADER_CALLBACK: {
            return { ...state, ...action.payload }
        }
        default: {
            return state;
        }
    }
}

export default uploader;