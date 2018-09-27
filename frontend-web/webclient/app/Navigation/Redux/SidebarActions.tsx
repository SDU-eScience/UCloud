import * as Types from "./SidebarReducer";
import { Action } from "redux";

interface SidebarState extends Action<typeof Types.SET_SIDEBAR_STATE> { open: boolean }
/**
 * Sets the sidebar state. Only relevant for mobile/tablet
 */
export const setSidebarState = (open: boolean): SidebarState => ({
    type: Types.SET_SIDEBAR_STATE,
    open
})