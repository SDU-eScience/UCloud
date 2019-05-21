import { SET_DIALOG_NODE } from "./DialogReducer";
import { PayloadAction } from "Types";

export type DialogActions = SetNode;

type SetNode = PayloadAction<typeof SET_DIALOG_NODE, { node?: JSX.Element }>
export const setNode = (node?: JSX.Element): SetNode => ({
    type: SET_DIALOG_NODE,
    payload: { node }
})