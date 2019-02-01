import { HeaderSearchType } from "DefaultObjects";
import { SET_PRIORITIZED_SEARCH, SET_REFRESH_FUNCTION } from "./HeaderReducer";
import { PayloadAction } from "Types";

export type HeaderActions = SetPrioritizedSearchAction | SetRefreshFunction;

type SetPrioritizedSearchAction = PayloadAction<typeof SET_PRIORITIZED_SEARCH, { prioritizedSearch: HeaderSearchType }>
export const setPrioritizedSearch = (prioritizedSearch: HeaderSearchType): SetPrioritizedSearchAction => ({
    type: SET_PRIORITIZED_SEARCH,
    payload: { prioritizedSearch }
});

export type SetRefreshFunction = PayloadAction<typeof SET_REFRESH_FUNCTION, { refresh?: () => void }>
export const setRefreshFunction = (refresh?: () => void): SetRefreshFunction => ({
    type: SET_REFRESH_FUNCTION,
    payload: { refresh }
})