import { SnackbarAction } from "./SnackbarsActions";
import { removeEntry } from "Utilities/CollectionUtilities";
import { init } from ".";
import { Snack } from "Snackbar/Snackbars";

export const ADD_SNACK = "ADD_SNACK";
export const REMOVE_SNACK = "REMOVE_SNACK";

const snackbars = (state = init().snackbar, action: SnackbarAction): { snackbar: Snack[] } => {
    switch (action.type) {
        case ADD_SNACK:
            return { ...state, snackbar: state.snackbar.concat([action.payload.snack]) }
        case REMOVE_SNACK: {
            const { snackbar } = state;
            return { ...state, snackbar: removeEntry(snackbar, action.payload.index) }
        }
        default:
            return state;
    }
}

export default snackbars;