export const OPEN_UPPY = "OPEN_UPPY";
export const CLOSE_UPPY = "CLOSE_UPPY";


const uppyReducers = (state = [], action) => {
    switch (action.type) {
        case OPEN_UPPY: {
            return { ...state, uppyOpen: true }
        }
        case CLOSE_UPPY: {
            return { ...state, uppyOpen: false };
        }
        default: {
            return state;
        }
    }
}

export default uppyReducers;