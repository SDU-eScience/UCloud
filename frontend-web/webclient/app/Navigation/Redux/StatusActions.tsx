import { UPDATE_PAGE_TITLE, UPDATE_STATUS, SET_ACTIVE_PAGE } from "./StatusReducer";
import { Status } from "..";
import { Action } from "redux";
import { PayloadAction } from "Types";
import { SidebarPages } from "ui-components/Sidebar";
export type StatusActions = UpdatePageTitleAction | UpdateStatusAction | SetActivePage; 


export interface UpdatePageTitleAction extends Action<typeof UPDATE_PAGE_TITLE> { payload: { title: string } }
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

type SetActivePage = PayloadAction<typeof SET_ACTIVE_PAGE, { page: SidebarPages }>
export const setActivePage = (page: SidebarPages): SetActivePage => ({
    type: SET_ACTIVE_PAGE,
    payload: { page }
});