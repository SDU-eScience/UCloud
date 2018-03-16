export const CHANGE_UPPY_OPEN = "CHANGE_UPPY_OPEN";

const uppyReducers = (state = [], action) => {
    switch (action.type) {
        case CHANGE_UPPY_OPEN: {
            return { ...state, uppyOpen: action.open }
        }
        default: {
            return state;
        }
    }
}

export default uppyReducers;