import {PRODUCT_NAME} from "../../../site.config.json";
import {SetLoadingAction} from "@/Types";
import {useDispatch} from "react-redux";
import {useEffect} from "react";
import {initStatus, StatusReduxObject} from "@/DefaultObjects";

export type Index = UpdatePageTitleAction | SetLoading;

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

export const statusReducer = (state: StatusReduxObject = initStatus(), action: Index): StatusReduxObject => {
    switch (action.type) {
        case UPDATE_PAGE_TITLE:
            document.title = `${PRODUCT_NAME} | ${action.payload.title}`;
            return {...state, ...action.payload};
        case SET_STATUS_LOADING:
            return {...state, ...action.payload};
        default: {
            return state;
        }
    }
};

