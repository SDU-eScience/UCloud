import { HeaderSearchType } from "DefaultObjects";
import { SET_PRIORITIZED_SEARCH } from "./HeaderReducer";


export const setPrioritizedSearch = (priority: HeaderSearchType) => ({
    type: SET_PRIORITIZED_SEARCH,
    priority
});