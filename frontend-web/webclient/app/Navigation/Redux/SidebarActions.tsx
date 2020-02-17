import {PayloadAction} from "Types";
import * as Types from "./SidebarReducer";

export type SidebarActions = KCSuccess;

type KCSuccess = PayloadAction<typeof Types.KC_SUCCESS, {pp: boolean}>;
export const KCSuccess = (): KCSuccess => ({
    type: Types.KC_SUCCESS,
    payload: {pp: true}
});
