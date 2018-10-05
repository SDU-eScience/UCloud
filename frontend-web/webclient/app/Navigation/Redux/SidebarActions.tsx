import * as Types from "./SidebarReducer";
import { Action } from "redux";

export type SidebarActions = SidebarState;

interface SidebarState extends Action<typeof Types.SET_SIDEBAR_STATE> { payload: { open: boolean } }
/**
 * Sets the sidebar state. Only relevant for mobile/tablet
 */
export const setSidebarState = (open: boolean): SidebarState => ({
    type: Types.SET_SIDEBAR_STATE,
    payload: { open }
})