import { FilePreviewReduxState, initFilePreview } from "DefaultObjects";

export const FILE_PREVIEW_RECEIVE_FILE = "FILE_PREVIEW_RECEIVE_FILE";
export const FILE_PREVIEW_SET_ERROR = "FILE_PREVIEW_SET_ERROR";

const filePreview = (state: FilePreviewReduxState = initFilePreview(), action: any) => {
    switch (action.type) {
        case FILE_PREVIEW_RECEIVE_FILE:
        case FILE_PREVIEW_SET_ERROR:
            return { ...state, ...action.payload };
        default:
            return state;
    }
}

export default filePreview;