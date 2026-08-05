import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {PRODUCT_NAME} from "../../../site.config.json";
import {createSlice, PayloadAction} from "@reduxjs/toolkit";
import {useDispatch} from "react-redux";
import {useEffect} from "react";
import {initStatus} from "@/DefaultObjects";

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
        dispatch(setStatusLoading(loading));
    }, [loading]);
}

export const statusSlice = createSlice({
    name: "status",
    initialState: initStatus(),
    reducers: {
        updatePageTitle(state, action: PayloadAction<string>) {
            document.title = `${PRODUCT_NAME} | ${action.payload}`;
            state.title = action.payload;
        },
        setStatusLoading(state, action: PayloadAction<boolean>) {
            state.loading = action.payload;
        },
        setActivePage(state, action: PayloadAction<SidebarTabId>) {
            state.tab = action.payload;
        }
    },
})

export const {updatePageTitle, setStatusLoading, setActivePage} = statusSlice.actions;

export const statusReducer = statusSlice.reducer;
