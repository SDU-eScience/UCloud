export const SET_LOADING = "SET_LOADING";
export const RECEIVE_PUBLICATIONS = "RECEIVE_PUBLICATIONS";

const zenodo = (state = [], action) => {
    switch (action.type) {
        case RECEIVE_PUBLICATIONS: {
            return { ...state, publications: action.publications, connected: action.connected, loading: false };
        }
        case SET_LOADING: {
            return { ...state, loading: action.loading };
        }
        default: {
            return state;
        }
    }
}

export default zenodo;