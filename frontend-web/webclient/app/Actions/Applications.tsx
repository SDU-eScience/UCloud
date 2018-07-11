import { Cloud } from "../../authentication/SDUCloudObject";
import { failureNotification } from "../UtilityFunctions";
import {
    RECEIVE_APPLICATIONS,
    SET_APPLICATIONS_LOADING,
    UPDATE_APPLICATIONS
} from "../Reducers/Applications";

import {
    Application,
    Action,
    SetLoadingAction,
    Page
} from "../Types";
import { emptyPage } from "../DefaultObjects";

interface ReceiveApplicationsAction extends Action { page: Page<Application> }
const receiveApplications = (page: Page<Application>): ReceiveApplicationsAction => ({
    type: RECEIVE_APPLICATIONS,
    page
});

export const fetchApplications = (page: number, itemsPerPage: number): Promise<ReceiveApplicationsAction> =>
    Cloud.get(`/hpc/apps?page=${page}&itemsPerPage=${itemsPerPage}`).then(({ response }: { response: Page<Application> }) => {
        return receiveApplications(response);
    }).catch(() => {
        failureNotification("An error occurred while retrieving applications.")
        return receiveApplications(emptyPage);
    });

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