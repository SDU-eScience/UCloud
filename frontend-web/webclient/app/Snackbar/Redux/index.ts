import snackbar from "./SnackbarsReducer";
import { Snack } from "Snackbar/Snackbars";

export const init = (): SnackbarReduxObject => ({
    snacks: []
});

export const reducers = (): { snackbar: any } => ({
    snackbar
})

export interface SnackbarReduxObject {
    snacks: Snack[]   
}