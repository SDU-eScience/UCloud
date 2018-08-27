import * as Types from "./SidebarReducer";
import {  Action } from "Types";

/**
 * Sets the sidebar as open. Only relevant for mobile
 */
export const setSidebarOpen = (): Action => ({
    type: Types.SET_SIDEBAR_OPEN
});

/**
 * Sets the sidebar as closed. Only relevant for mobile
 */
export const setSidebarClosed = (): Action => ({
    type: Types.SET_SIDEBAR_CLOSED
})