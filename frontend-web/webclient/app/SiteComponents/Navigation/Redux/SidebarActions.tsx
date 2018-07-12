import { Cloud } from "../../../../authentication/SDUCloudObject";
import * as Types from "./SidebarReducer";
import { failureNotification } from "../../../UtilityFunctions";
import { SetLoadingAction, Action, SidebarOption } from "../../../Types";

/**
 * Intended to fetch sidebar options for the sidebar, currently fetched from a
 * mock API. Sets the loading variable to false in the redux store when fetched.
 */
export const fetchSidebarOptions = ():Promise<ReceiveSidebarOptionsActions> =>
    Cloud.get("/../mock-api/mock_sidebar_options.json").then(({ response }) => {
        return receiveSidebarOptions(response);
    }).catch(() => {
        failureNotification("An error occurred while trying to populate sidebar options");
        return receiveSidebarOptions([])
    });

/**
 * Sets the sidebar loading state for the sidebar.
 * @param {boolean} loading - sets whether or not the sidebar displays a loading icon
 */
export const setSidebarLoading = (loading: boolean): SetLoadingAction => ({
    type: Types.SET_SIDEBAR_LOADING,
    loading
});

interface ReceiveSidebarOptionsActions extends Action { options: SidebarOption[] }
/**
 * The action for receiving sidebar options
 * @param {SidebarOption[]} options The sidebar options to be rendered
 */
const receiveSidebarOptions = (options: SidebarOption[]): ReceiveSidebarOptionsActions => ({
    type: Types.RECEIVE_SIDEBAR_OPTIONS,
    options
});

/**
 * Sets the sidebar as open. Only relevant for mobile
 */
export const setSidebarOpen = (): Action => ({
    type: Types.SET_SIDEBAR_OPEN
});

/**
 * Sets the sidebar as closed. Only relevant for mobile
 */
export const setSidebarClosed = (): Action => ({
    type: Types.SET_SIDEBAR_CLOSED
})