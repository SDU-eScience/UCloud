import { HeaderSearchReduxObject, initHeader } from "DefaultObjects";
import { HeaderActions } from "./HeaderActions";

export const SET_PRIORITIZED_SEARCH = "SET_PRIORITIZED_SEARCH";

const header = (state: HeaderSearchReduxObject = initHeader(), action: HeaderActions): HeaderSearchReduxObject => {
    switch (action.type) {
        case SET_PRIORITIZED_SEARCH: {
            return { ...state, prioritizedSearch: action.payload.priority };
        }
        default: {
            return state;
        }
    }
};

export default header;