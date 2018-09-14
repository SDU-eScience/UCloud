export const RECEIVE_APPLICATIONS = "RECEIVE_APPLICATIONS";
export const SET_APPLICATIONS_LOADING = "SET_APPLICATIONS_LOADING";
export const UPDATE_APPLICATIONS = "UPDATE_APPLICATIONS";
export const APPLICATIONS_ERROR = "APPLICATIONS_ERROR";

const applications = (state = [], action) => {
    switch (action.type) {
        case RECEIVE_APPLICATIONS: {
            return { ...state, page: action.page, loading: false };
        }
        case SET_APPLICATIONS_LOADING: {
            return { ...state, loading: action.loading };
        }
        case UPDATE_APPLICATIONS: {
            return { ...state, page: action.page };
        }
        case APPLICATIONS_ERROR: {
            return { ...state, error: action.error, loading: false };
        }
        default: {
            return state;
        }
    }
};

export default applications;