import { UPDATE_PAGE_TITLE, UPDATE_STATUS } from "./StatusReducer";
import { Action } from "../../../Types";
import { Status } from "..";

interface UpdatePageTitleAction extends Action { title: string }
/**
 * Sets the title of the window. Stores in the redux store as well
 * @param {string} title the title to be set
 */
export const updatePageTitle = (title: string): UpdatePageTitleAction => ({
    type: UPDATE_PAGE_TITLE,
    title
});

interface UpdateStatusAction extends Action { status: Status }
/**
 * Sets the sitewide status, concerning the health of the back end.
 * @param {Status} status the status to be set
 */
export const updateStatus = (status: Status): UpdateStatusAction => ({
    type: UPDATE_STATUS,
    status
});