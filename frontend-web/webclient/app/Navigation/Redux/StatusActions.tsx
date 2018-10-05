import { UPDATE_PAGE_TITLE, UPDATE_STATUS } from "./StatusReducer";
import { Status } from "..";
import { Action } from "redux";
export type StatusActions = UpdatePageTitleAction | UpdateStatusAction;


interface UpdatePageTitleAction extends Action<typeof UPDATE_PAGE_TITLE> { payload: { title: string } }
/**
 * Sets the title of the window. Stores in the redux store as well
 * @param {string} title the title to be set
 */
export const updatePageTitle = (title: string): UpdatePageTitleAction => ({
    type: UPDATE_PAGE_TITLE,
    payload: { title }
});

interface UpdateStatusAction extends Action<typeof UPDATE_STATUS> { payload: { status: Status } }
/**
 * Sets the sitewide status, concerning the health of the back end.
 * @param {Status} status the status to be set
 */
export const updateStatus = (status: Status): UpdateStatusAction => ({
    type: UPDATE_STATUS,
    payload: { status }
});