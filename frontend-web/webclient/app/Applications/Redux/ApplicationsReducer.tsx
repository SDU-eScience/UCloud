import { ApplicationReduxObject, initApplications } from "DefaultObjects";
import { ApplicationActions } from "./ApplicationsActions";

export const RECEIVE_APPLICATIONS = "RECEIVE_APPLICATIONS";
export const SET_APPLICATIONS_LOADING = "SET_APPLICATIONS_LOADING";
export const UPDATE_APPLICATIONS = "UPDATE_APPLICATIONS";
export const APPLICATIONS_ERROR = "APPLICATIONS_ERROR";

const applications = (state: ApplicationReduxObject = initApplications(), action: ApplicationActions): ApplicationReduxObject => {
    switch (action.type) {
        case RECEIVE_APPLICATIONS:
        case APPLICATIONS_ERROR: {
            return { ...state, ...action.payload, loading: false };
        }
        case SET_APPLICATIONS_LOADING: {
            return { ...state, ...action.payload };
        }
        default: {
            return state;
        }
    }
};

export default applications;