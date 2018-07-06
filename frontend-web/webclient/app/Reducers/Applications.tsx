export const RECEIVE_APPLICATIONS = "RECEIVE_APPLICATIONS";
export const SET_APPLICATIONS_LOADING = "SET_APPLICATIONS_LOADING";
export const TO_APPLICATIONS_PAGE = "TO_APPLICATIONS_PAGE";
export const UPDATE_APPLICATIONS_PER_PAGE = "UPDATE_APPLICATIONS_PER_PAGE";
export const UPDATE_APPLICATIONS = "UPDATE_APPLICATIONS";

const applications = (state = [], action) => {
    switch (action.type) {
        case RECEIVE_APPLICATIONS: {
            return { ...state, applications: action.applications, loading: false };
        }
        case SET_APPLICATIONS_LOADING: {
            return { ...state, loading: action.loading };
        }
        case TO_APPLICATIONS_PAGE: {
            return { ...state, pageNumber: action.pageNumber };
        }
        case UPDATE_APPLICATIONS_PER_PAGE: {
            return { ...state, itemsPerPage: action.itemsPerPage, pageNumber: 0 };
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