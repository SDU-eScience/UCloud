import { ReactNode } from "react";
import { SET_ALERT_VISIBLE, SET_ALERT_NODE } from "./AlertsReducer";
import { PayloadAction } from "Types";

export type AlertsAction = SetVisible | SetNode;

type SetVisible = PayloadAction<typeof SET_ALERT_VISIBLE, { visible: boolean }>
export const setVisible = (visible: boolean): SetVisible => ({
    type: SET_ALERT_VISIBLE,
    payload: { visible }
});

type SetNode = PayloadAction<typeof SET_ALERT_NODE, { node?: ReactNode }>
export const setNode = (node?: ReactNode): SetNode => ({
    type: SET_ALERT_NODE,
    payload: { node }
})