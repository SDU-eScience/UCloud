import {SetLoadingAction} from "Types";
import {SidebarPages} from "ui-components/Sidebar";
import {SET_ACTIVE_PAGE, SET_STATUS_LOADING, UPDATE_PAGE_TITLE} from "./StatusReducer";
import {useDispatch} from "react-redux";
import {useEffect} from "react";

export type StatusActions = UpdatePageTitleAction | SetActivePage | SetLoading;

export type UpdatePageTitleAction = PayloadAction<typeof UPDATE_PAGE_TITLE, {title: string}>;
/**
 * Sets the title of the window. Stores in the redux store as well
 * @param {string} title the title to be set
 */
export const updatePageTitle = (title: string): UpdatePageTitleAction => ({
    type: UPDATE_PAGE_TITLE,
    payload: {title}
});

type SetActivePage = PayloadAction<typeof SET_ACTIVE_PAGE, {page: SidebarPages}>;
export const setActivePage = (page: SidebarPages): SetActivePage => ({
    type: SET_ACTIVE_PAGE,
    payload: {page}
});

type SetLoading = SetLoadingAction<typeof SET_STATUS_LOADING>;
export const setLoading = (loading: boolean): SetLoading => ({
    type: SET_STATUS_LOADING,
    payload: {loading}
});

export interface SetStatusLoading {
    setLoading: (loading: boolean) => void;
}

export function useTitle(title: string): void {
    const dispatch = useDispatch();
    useEffect(() => {
        dispatch(updatePageTitle(title));
        return () => {
            dispatch(updatePageTitle(""));
        };
    });
}

export function useLoading(loading: boolean): void {
    const dispatch = useDispatch();
    dispatch(setLoading(loading));
}
