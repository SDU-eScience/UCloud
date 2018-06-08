export const SET_ZENODO_LOADING = "SET_ZENODO_LOADING";
export const RECEIVE_PUBLICATIONS = "RECEIVE_PUBLICATIONS";

const zenodo = (state = [], action) => {
    switch (action.type) {
        case RECEIVE_PUBLICATIONS: {
            return { ...state, ...action, loading: false };
        }
        case SET_ZENODO_LOADING: {
            return { ...state, loading: action.loading };
        }
        default: {
            return state;
        }
    }
}

export default zenodo;