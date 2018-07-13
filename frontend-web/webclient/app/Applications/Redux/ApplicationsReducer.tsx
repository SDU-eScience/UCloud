export const RECEIVE_APPLICATIONS = "RECEIVE_APPLICATIONS";
export const SET_APPLICATIONS_LOADING = "SET_APPLICATIONS_LOADING";
export const UPDATE_APPLICATIONS = "UPDATE_APPLICATIONS";

const applications = (state = [], action) => {
    switch (action.type) {
        case RECEIVE_APPLICATIONS: {
            return { ...state, page: action.page, loading: false };
        }
        case SET_APPLICATIONS_LOADING: {
            return { ...state, loading: action.loading };
        }
        case UPDATE_APPLICATIONS: {
            return { ...state, applications: action.applications };
        }
        default: {
            return state;
        }
    }
};

export default applications;