import snackbar from "./SnackbarsReducer";
import { Snack } from "Snackbar/Snackbars";

export const init = (): Wrapper => ({
    snackbar: {
        snackbar: [] as Snack[],
        nextId: 0
    }
});

export const reducers = {
    snackbar
};

export interface Wrapper {
    snackbar: Object
}

export interface Object {
    snackbar: Snack[]
    nextId: number
}