import { Snack } from "Snackbar/Snackbars";
import { PayloadAction } from "Types";
import { ADD_SNACK, REMOVE_SNACK } from "./SnackbarsReducer";

export type SnackbarAction = AddSnack | RemoveSnack;

type AddSnack = PayloadAction<typeof ADD_SNACK, { snack: Snack }>
export const addSnack = (snack: Snack): AddSnack => ({
    type: ADD_SNACK,
    payload: { snack }
});

type RemoveSnack = PayloadAction<typeof REMOVE_SNACK, { index: number}>
export const removeSnack = (index: number): RemoveSnack => ({
    type: REMOVE_SNACK,
    payload: { index }
})