import { HeaderSearchType } from "DefaultObjects";
import { SET_PRIORITIZED_SEARCH } from "./HeaderReducer";
import { Action } from "redux";

export type HeaderActions = SetPrioritizedSearchAction;

interface SetPrioritizedSearchAction extends Action<typeof SET_PRIORITIZED_SEARCH> { payload: { priority: HeaderSearchType } }
export const setPrioritizedSearch = (priority: HeaderSearchType): SetPrioritizedSearchAction => ({
    type: SET_PRIORITIZED_SEARCH,
    payload: { priority }
});