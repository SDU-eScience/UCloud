import { SnackbarAction } from "./SnackbarsActions";

export const ADD_SNACK = "ADD_SNACK";

const snackbars = (state = () => ({ snacks: [] }), action: SnackbarAction) => {
    switch (action.type) {
        case ADD_SNACK:
            return { ...state, ...action.payload}
        default:
            return state;
    }
}

export default snackbars;