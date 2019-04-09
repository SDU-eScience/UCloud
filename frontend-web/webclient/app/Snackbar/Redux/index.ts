import snackbar from "./SnackbarsReducer";
import { Snack } from "Snackbar/Snackbars";

export const init = (): Object => ({
    snackbar: {
        snackbar: [] as Snack[]
    }
});

export const reducers = {
    snackbar
};

export interface Object {
    snackbar: { snackbar: Snack[] }
}