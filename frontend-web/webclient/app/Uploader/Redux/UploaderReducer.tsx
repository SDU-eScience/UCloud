export const SET_UPLOADER_VISIBLE = "SET_UPLOADER_VISIBLE";
export const SET_UPLOADER_UPLOADS = "SET_UPLOADER_UPLOADS";

const uploader = (state: any = {}, action) => {
    switch (action.type) {
        case SET_UPLOADER_UPLOADS: {
            return { ...state, uploads: action.uploads };
        }
        case SET_UPLOADER_VISIBLE: {
            return { ...state, visible: action.visible };
        }
        default:
            return state;
    }
}

export default uploader;