import { ApplicationReduxObject, initApplications } from "DefaultObjects";
import { ApplicationActions } from "./ApplicationsActions";

export const RECEIVE_APPLICATIONS = "RECEIVE_APPLICATIONS";
export const SET_APPLICATIONS_LOADING = "SET_APPLICATIONS_LOADING";
export const UPDATE_APPLICATIONS = "UPDATE_APPLICATIONS";
export const APPLICATIONS_ERROR = "APPLICATIONS_ERROR";
export const RECEIVE_FAVORITE_APPLICATIONS = "RECEIVE_FAVORITE_APPLICATIONS";
export const SET_FAVORITE_APPLICATIONS_LOADING = "SET_FAVORITE_APPLICATIONS_LOADING"

const applications = (state: ApplicationReduxObject = initApplications(), action: ApplicationActions): ApplicationReduxObject => {
    switch (action.type) {
        case RECEIVE_APPLICATIONS:
        case APPLICATIONS_ERROR: {
            return { ...state, ...action.payload, loading: false };
        }
        case SET_FAVORITE_APPLICATIONS_LOADING:
        case SET_APPLICATIONS_LOADING: {
            return { ...state, ...action.payload };
        }
        case RECEIVE_FAVORITE_APPLICATIONS: {
            return { ...state, ...action.payload, favoritesLoading: false };
        }
        default: {
            return state;
        }
    }
};

export default applications;