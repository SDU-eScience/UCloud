import { ReactNode } from "react";
import { SET_DIALOG_VISIBLE, SET_DIALOG_NODE } from "./DialogReducer";
import { PayloadAction } from "Types";

export type DialogActions = SetVisible | SetNode;

type SetVisible = PayloadAction<typeof SET_DIALOG_VISIBLE, { visible: boolean }>
export const setVisible = (visible: boolean): SetVisible => ({
    type: SET_DIALOG_VISIBLE,
    payload: { visible }
});

type SetNode = PayloadAction<typeof SET_DIALOG_NODE, { node?: ReactNode }>
export const setNode = (node?: ReactNode): SetNode => ({
    type: SET_DIALOG_NODE,
    payload: { node }
})