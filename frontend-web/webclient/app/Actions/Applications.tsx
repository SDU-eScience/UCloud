import { Cloud } from "../../authentication/SDUCloudObject";
import { failureNotification } from "../UtilityFunctions";
import { RECEIVE_APPLICATIONS, SET_APPLICATIONS_LOADING, TO_APPLICATIONS_PAGE, UPDATE_APPLICATIONS_PER_PAGE, UPDATE_APPLICATIONS } from "../Reducers/Applications";
import { Application, Action, SetLoadingAction, ToPageAction, SetItemsPerPage } from "../Types";

interface ReceiveApplicationsAction extends Action { applications: Application[] }
// FIXME Make applications a Page when backend supports it.
const receiveApplications = (applications: Application[]): ReceiveApplicationsAction => ({
    type: RECEIVE_APPLICATIONS,
    applications
});

// FIXME Make applications a Page when backend supports it.
export const fetchApplications = (): Promise<ReceiveApplicationsAction> =>
    Cloud.get("/hpc/apps").then(({ response }) => {
        response.sort((a, b) =>
            a.prettyName.localeCompare(b.prettyName)
        );
        return receiveApplications(response);
    }).catch(() => {
        failureNotification("An error occurred while retrieving applications.")
        return receiveApplications([]);
    });

/**
 * Sets the loading state for the applications component
 * @param {boolean} loading loading state for the applications component
 */
export const setLoading = (loading: boolean): SetLoadingAction => ({
    type: SET_APPLICATIONS_LOADING,
    loading
});

// FIXME Make applications a Page when backend supports it.
export const toPage = (pageNumber): ToPageAction => ({
    type: TO_APPLICATIONS_PAGE,
    pageNumber
});

// FIXME Make applications a Page when backend supports it.
export const updateApplications = (applications: Application[]): ReceiveApplicationsAction => ({
    type: UPDATE_APPLICATIONS,
    applications
});

// FIXME Make applications a Page when backend supports it.
export const updateApplicationsPerPage = (itemsPerPage: number): SetItemsPerPage => ({
    type: UPDATE_APPLICATIONS_PER_PAGE,
    itemsPerPage
});