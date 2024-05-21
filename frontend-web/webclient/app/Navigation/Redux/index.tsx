import {PRODUCT_NAME} from "../../../site.config.json";
import {SetLoadingAction} from "@/Types";
import {useDispatch} from "react-redux";
import {useEffect} from "react";
import {initStatus, StatusReduxObject} from "@/DefaultObjects";
import {SidebarTabId} from "@/ui-components/SidebarComponents";

export type Index = UpdatePageTitleAction | SetLoading | SetActivePage;

export type UpdatePageTitleAction = PayloadAction<typeof UPDATE_PAGE_TITLE, {title: string}>;
/**
 * Sets the title of the window. Stores in the redux store as well
 * @param {string} title the title to be set
 */
export const updatePageTitle = (title: string): UpdatePageTitleAction => ({
    type: UPDATE_PAGE_TITLE,
    payload: {title}
});

type SetLoading = SetLoadingAction<typeof SET_STATUS_LOADING>;
export function setLoading(loading: boolean): SetLoading {
    return ({
        type: SET_STATUS_LOADING,
        payload: {loading}
    });
}

type SetActivePage = PayloadAction<typeof SET_ACTIVE_PAGE, {tab: SidebarTabId}>
function setActivePage(tab: SidebarTabId): SetActivePage {
    return {
        type: SET_ACTIVE_PAGE,
        payload: {tab}
    }
}

export interface SetStatusLoading {
    setLoading: (loading: boolean) => void;
}

export function usePage(title: string, tab: SidebarTabId): void {
    const dispatch = useDispatch();
    useEffect(() => {
        dispatch(updatePageTitle(title));
        dispatch(setActivePage(tab));
        return () => {
            dispatch(updatePageTitle(""));
            dispatch(setActivePage(SidebarTabId.NONE));
        };
    }, [title]);
}

export function useLoading(loading: boolean): void {
    const dispatch = useDispatch();
    useEffect(() => {
        dispatch(setLoading(loading));
    }, [loading]);
}

export const UPDATE_PAGE_TITLE = "UPDATE_PAGE_TITLE";
export const SET_STATUS_LOADING = "SET_STATUS_LOADING";
export const SET_ACTIVE_PAGE = "SET_ACTIVE_PAGE";

export const statusReducer = (state: StatusReduxObject = initStatus(), action: Index): StatusReduxObject => {
    switch (action.type) {
        case UPDATE_PAGE_TITLE:
            document.title = `${PRODUCT_NAME} | ${action.payload.title}`;
            return {...state, ...action.payload};
        case SET_STATUS_LOADING:
        case SET_ACTIVE_PAGE:
            return {...state, ...action.payload};
        default: {
            return state;
        }
    }
};

