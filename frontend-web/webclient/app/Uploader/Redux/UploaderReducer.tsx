export const SET_UPLOADER_CALLBACK = "SET_UPLOADER_CALLBACK";
export const SET_UPLOADER_UPLOADS = "SET_UPLOADER_UPLOADS";
export const SET_UPLOADER_VISIBLE = "SET_UPLOADER_VISIBLE";
import { UploaderActions} from "./UploaderActions";
import { UploaderReduxObject, initUploads } from "DefaultObjects";
/* type UploaderActions = SetUploaderVisibleProps | SetUploadsProps | SetUploaderCallbackProps */


const uploader = (state: UploaderReduxObject = initUploads(), action: UploaderActions): UploaderReduxObject => {
    switch (action.type) {
        case SET_UPLOADER_UPLOADS: {
            return { ...state, uploads: action.uploads };
        }
        case SET_UPLOADER_VISIBLE: {
            return { ...state, visible: action.visible };
        }
        case SET_UPLOADER_CALLBACK: {
            return { ...state, onFilesUploaded: action.callback }
        }
        // REQUIRED BY REDUX, WILL OVERWRITE OTHERWISE
        default: {
            return state;
        }
    }
}

export default uploader;