import {HeaderSearchType} from "DefaultObjects";
import {PayloadAction} from "Types";
import {SET_PRIORITIZED_SEARCH, SET_REFRESH_FUNCTION, USER_LOGIN, USER_LOGOUT} from "./HeaderReducer";

export type HeaderActions = SetPrioritizedSearchAction | SetRefreshFunction;

type SetPrioritizedSearchAction = PayloadAction<typeof SET_PRIORITIZED_SEARCH, {prioritizedSearch: HeaderSearchType}>;
export const setPrioritizedSearch = (prioritizedSearch: HeaderSearchType): SetPrioritizedSearchAction => ({
    type: SET_PRIORITIZED_SEARCH,
    payload: {prioritizedSearch}
});

export type SetRefreshFunction = PayloadAction<typeof SET_REFRESH_FUNCTION, {refresh?: () => void}>;
export const setRefreshFunction = (refresh?: () => void): SetRefreshFunction => ({
    type: SET_REFRESH_FUNCTION,
    payload: {refresh}
});

interface UserLogOut {type: typeof USER_LOGOUT}
export const logout = (): UserLogOut => ({
    type: USER_LOGOUT
});

interface UserLogIn {type: typeof USER_LOGIN}
export const login = (): UserLogIn => ({
    type: USER_LOGIN
});
