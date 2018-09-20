export const OPEN_UPPY = "OPEN_UPPY";


const uppyReducers = (state = [], action) => {
    switch (action.type) {
        case OPEN_UPPY: {
            return { ...state, uppyOpen: action.open }
        }
        default: {
            return state;
        }
    }
}

export default uppyReducers;