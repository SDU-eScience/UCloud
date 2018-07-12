export const SET_ZENODO_LOADING = "SET_ZENODO_LOADING";
export const RECEIVE_PUBLICATIONS = "RECEIVE_PUBLICATIONS";
export const RECEIVE_ZENODO_LOGIN_STATUS = "RECEIVE_ZENODO_LOGIN_STATUS";

const zenodo = (state = [], action) => {
    switch (action.type) {
        case RECEIVE_PUBLICATIONS: {
            return { ...state, ...action, loading: false };
        }
        case SET_ZENODO_LOADING: {
            return { ...state, loading: action.loading };
        }
        case RECEIVE_ZENODO_LOGIN_STATUS: {
            return { ...state, loggedIn: action.loggedIn };
        }
        default: {
            return state;
        }
    }
}

export default zenodo;