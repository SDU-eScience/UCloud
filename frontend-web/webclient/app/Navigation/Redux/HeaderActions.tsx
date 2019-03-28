import { HeaderSearchType } from "DefaultObjects";
import { SET_PRIORITIZED_SEARCH, SET_REFRESH_FUNCTION, USER_LOGOUT, USER_LOGIN, CONTEXT_SWITCH } from "./HeaderReducer";
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

type UserLogOut = { type: typeof USER_LOGOUT }
export const logout = (): UserLogOut => ({
    type: USER_LOGOUT
});

type UserLogIn = { type: typeof USER_LOGIN }
export const login = (): UserLogIn => ({
    type: USER_LOGIN
});

type ContextSwitch = { type: typeof CONTEXT_SWITCH }
export const contextSwitch = (): ContextSwitch => ({
    type: CONTEXT_SWITCH
});