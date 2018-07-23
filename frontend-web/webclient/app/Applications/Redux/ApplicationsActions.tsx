import { Cloud } from "Authentication/SDUCloudObject";
import {
    RECEIVE_APPLICATIONS,
    SET_APPLICATIONS_LOADING,
    UPDATE_APPLICATIONS,
    APPLICATIONS_ERROR
} from "./ApplicationsReducer";
import {
    Action,
    SetLoadingAction,
    Page,
    Error
} from "Types";
import { Application } from ".."

interface ReceiveApplicationsAction extends Action { page: Page<Application> }
const receiveApplications = (page: Page<Application>): ReceiveApplicationsAction => ({
    type: RECEIVE_APPLICATIONS,
    page
});

export const setErrorMessage = (error?: string): Error => ({
    type: APPLICATIONS_ERROR,
    error
});

export const fetchApplications = (page: number, itemsPerPage: number): Promise<ReceiveApplicationsAction | Error> =>
    Cloud.get(`/hpc/apps?page=${page}&itemsPerPage=${itemsPerPage}`).then(({ response }: { response: Page<Application> }) => 
        receiveApplications(response)
    ).catch(() => setErrorMessage("An error occurred while retrieving applications."));

/**
 * Sets the loading state for the applications component
 * @param {boolean} loading loading state for the applications component
 */
export const setLoading = (loading: boolean): SetLoadingAction => ({
    type: SET_APPLICATIONS_LOADING,
    loading
});

export const updateApplications = (page: Page<Application>): ReceiveApplicationsAction => ({
    type: UPDATE_APPLICATIONS,
    page
});