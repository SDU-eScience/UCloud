import { SnackbarAction } from "./SnackbarsActions";
import { init, Object } from ".";
import { Snack } from "Snackbar/Snackbars";

export const ADD_SNACK = "ADD_SNACK";
export const REMOVE_SNACK = "REMOVE_SNACK";

const snackbars = (state = init().snackbar, action: SnackbarAction): Object => {
    switch (action.type) {
        case ADD_SNACK:
            const snack: Snack = { 
                ...action.payload.snack,
                id: state.nextId,
                lifetime: action.payload.snack.lifetime || 3000
            };
            return { ...state, nextId: state.nextId + 1, snackbar: state.snackbar.concat([snack]) }
        case REMOVE_SNACK: {
            const { snackbar } = state;
            return { ...state, snackbar: snackbar.filter(it => it.id !== action.payload.index) }
        }
        default:
            return state;
    }
}

export default snackbars;