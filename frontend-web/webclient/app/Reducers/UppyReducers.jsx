export const CHANGE_UPPY_OPEN = "CHANGE_UPPY_OPEN";
export const UPDATE_UPPY = "UPDATE_UPPY";


const uppyReducers = (state = [], action) => {
    switch (action.type) {
        case CHANGE_UPPY_OPEN: {
            return { ...state, uppyOpen: action.open }
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