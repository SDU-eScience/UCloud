import { Cloud } from "../../authentication/SDUCloudObject";
import { failureNotification } from "../UtilityFunctions";
import {
    RECEIVE_APPLICATIONS,
    SET_APPLICATIONS_LOADING,
    TO_APPLICATIONS_PAGE,
    UPDATE_APPLICATIONS_PER_PAGE,
    UPDATE_APPLICATIONS
} from "../Reducers/Applications";

import {
    Application,
    Action,
    SetLoadingAction,
    ToPageAction,
    SetItemsPerPage,
    Page
} from "../Types";
import { emptyPage } from "../DefaultObjects";

interface ReceiveApplicationsAction extends Action { applications: Page<Application> }
const receiveApplications = (applications: Page<Application>): ReceiveApplicationsAction => ({
    type: RECEIVE_APPLICATIONS,
    applications
});

export const fetchApplications = (): Promise<ReceiveApplicationsAction> =>
    Cloud.get("/hpc/apps").then(({ response }: { response: Page<Application> }) => {
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

export const toPage = (pageNumber): ToPageAction => ({
    type: TO_APPLICATIONS_PAGE,
    pageNumber
});

export const updateApplications = (applications: Page<Application>): ReceiveApplicationsAction => ({
    type: UPDATE_APPLICATIONS,
    applications
});

export const updateApplicationsPerPage = (itemsPerPage: number): SetItemsPerPage => ({
    type: UPDATE_APPLICATIONS_PER_PAGE,
    itemsPerPage
});