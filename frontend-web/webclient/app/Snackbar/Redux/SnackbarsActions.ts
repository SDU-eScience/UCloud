import { Snack } from "Snackbar/Snackbars";
import { PayloadAction } from "Types";
import { ADD_SNACK } from "./SnackbarsReducer";

export type SnackbarAction = AddSnack;

type AddSnack = PayloadAction<typeof ADD_SNACK, Snack>
export const addSnack = (snack: Snack): AddSnack => ({
    type: ADD_SNACK,
    payload: snack
});