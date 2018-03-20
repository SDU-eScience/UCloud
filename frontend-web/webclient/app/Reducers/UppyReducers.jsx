export const CHANGE_UPPY_RUNAPP_OPEN = "CANGE_UPPY_RUNAPP_OPEN";
export const CHANGE_UPPY_FILES_OPEN = "CHANGE_UPPY_FILES_OPEN";
export const CLOSE_UPPY = "CLOSE_UPPY";
export const UPDATE_UPPY = "UPDATE_UPPY";


const uppyReducers = (state = [], action) => {
    switch (action.type) {
        case CHANGE_UPPY_RUNAPP_OPEN: {
            return { ...state, uppyRunAppOpen: action.open }
        }
        case CHANGE_UPPY_FILES_OPEN: {
            return { ...state, uppyFilesOpen: action.open }
        }
        case CLOSE_UPPY: {
            return { ...state, uppyFilesOpen: false, uppyRunAppOpen: false}
        }
        case UPDATE_UPPY: {
            return { ...state, uppy: action.uppy }
        }
        default: {
            return state;
        }
    }
}

export default uppyReducers;