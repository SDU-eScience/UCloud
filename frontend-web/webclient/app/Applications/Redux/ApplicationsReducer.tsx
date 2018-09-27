import { ApplicationReduxObject, initApplications } from "DefaultObjects";
import { ApplicationActions } from "./ApplicationsActions"; 

export const RECEIVE_APPLICATIONS = "RECEIVE_APPLICATIONS";
export const SET_APPLICATIONS_LOADING = "SET_APPLICATIONS_LOADING";
export const UPDATE_APPLICATIONS = "UPDATE_APPLICATIONS";
export const APPLICATIONS_ERROR = "APPLICATIONS_ERROR";

const applications = (state: ApplicationReduxObject = initApplications(), action: ApplicationActions) => {
    switch (action.type) {
        case RECEIVE_APPLICATIONS: {
            return { ...state, page: action.payload.page, loading: false };
        }
        case SET_APPLICATIONS_LOADING: {
            return { ...state, loading: action.payload.loading };
        }
        case APPLICATIONS_ERROR: {
            return { ...state, error: action.payload.error, loading: false };
        }
        default: {
            return state;
        }
    }
};

export default applications;