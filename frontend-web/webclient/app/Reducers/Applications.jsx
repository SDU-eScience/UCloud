export const RECEIVE_APPLICATIONS = "RECEIVE_APPLICATIONS";
export const SET_LOADING = "SET_LOADING";
export const TO_PAGE = "TO_PAGE";
export const UPDATE_APPLICATIONS_PER_PAGE = "UPDATE_APPLICATIONS_PER_PAGE";
export const UPDATE_APPLICATIONS = "UPDATE_APPLICATIONS";

const applications = (state = [], action) => {
    switch (action.type) {
        case RECEIVE_APPLICATIONS: {
            return { ...state, applications: action.applications, loading: false };
        }
        case SET_LOADING: {
            return { ...state, loading: action.loading };
        }
        case TO_PAGE: {
            return { ...state, currentApplicationsPage: action.pageNumber };
        }
        case UPDATE_APPLICATIONS_PER_PAGE: {
            return { ...state, applicationsPerPage: action.applicationsPerPage };
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