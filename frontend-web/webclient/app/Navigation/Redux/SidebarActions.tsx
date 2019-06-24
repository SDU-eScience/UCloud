import * as Types from "./SidebarReducer";
import {PayloadAction} from "Types";

export type SidebarActions = SidebarState | KCSuccess;

type SidebarState = PayloadAction<typeof Types.SET_SIDEBAR_STATE, {open: boolean}>
/**
 * Sets the sidebar state. Only relevant for mobile/tablet
 */
export const setSidebarState = (open: boolean): SidebarState => ({
    type: Types.SET_SIDEBAR_STATE,
    payload: {open}
})

type KCSuccess = PayloadAction<typeof Types.KC_SUCCESS, {pp: boolean}>
export const KCSuccess = (): KCSuccess => ({
    type: Types.KC_SUCCESS,
    payload: {pp: true}
});