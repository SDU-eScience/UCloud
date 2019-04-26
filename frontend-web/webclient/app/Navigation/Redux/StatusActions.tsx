import { UPDATE_PAGE_TITLE, UPDATE_STATUS, SET_ACTIVE_PAGE, SET_STATUS_LOADING } from "./StatusReducer";
import { Status } from "..";
import { Action } from "redux";
import { PayloadAction, SetLoadingAction } from "Types";
import { SidebarPages } from "ui-components/Sidebar";
export type StatusActions = UpdatePageTitleAction | UpdateStatusAction | SetActivePage | SetLoading;


export interface UpdatePageTitleAction extends Action<typeof UPDATE_PAGE_TITLE> { payload: { title: string } }
/**
 * Sets the title of the window. Stores in the redux store as well
 * @param {string} title the title to be set
 */
export const updatePageTitle = (title: string): UpdatePageTitleAction => ({
    type: UPDATE_PAGE_TITLE,
    payload: { title }
});

type UpdateStatusAction = PayloadAction<typeof UPDATE_STATUS, { status: Status }>
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

type SetLoading = SetLoadingAction<typeof SET_STATUS_LOADING>
export const setLoading = (loading: boolean): SetLoading => ({
    type: SET_STATUS_LOADING,
    payload: { loading }
});

export interface SetStatusLoading {
    setLoading: (loading: boolean) => void
}