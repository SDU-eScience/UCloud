import { Cloud } from "Authentication/SDUCloudObject";
import {
    RECEIVE_APPLICATIONS,
    SET_APPLICATIONS_LOADING,
    APPLICATIONS_ERROR
} from "./ApplicationsReducer";
import {
    SetLoadingAction,
    Page,
    Error
} from "Types";
import { Application } from ".."
import { Action } from "redux";
import { hpcApplicationsQuery } from "Utilities/ApplicationUtilities";

interface ReceiveApplicationsAction extends Action<typeof RECEIVE_APPLICATIONS> { page: Page<Application> }
export const receiveApplications = (page: Page<Application>): ReceiveApplicationsAction => ({
    type: RECEIVE_APPLICATIONS,
    page
});

export const setErrorMessage = (error?: string): Error<typeof APPLICATIONS_ERROR> => ({
    type: APPLICATIONS_ERROR,
    error
});

export const fetchApplications = (page: number, itemsPerPage: number): Promise<ReceiveApplicationsAction | Error<typeof APPLICATIONS_ERROR>> =>
    Cloud.get(hpcApplicationsQuery(page, itemsPerPage)).then(({ response }: { response: Page<Application> }) =>
        receiveApplications(response)
    ).catch(() => setErrorMessage("An error occurred while retrieving applications."));

/**
 * Sets the loading state for the applications component
 * @param {boolean} loading loading state for the applications component
 */
export const setLoading = (loading: boolean): SetLoadingAction<typeof SET_APPLICATIONS_LOADING> => ({
    type: SET_APPLICATIONS_LOADING,
    loading
});