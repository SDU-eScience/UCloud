import {useEffect} from "react";
import {Action, AnyAction, combineReducers, compose} from "redux";

import {dashboardReducer} from "@/Dashboard/Redux";
import {initObject} from "@/DefaultObjects";
import {statusReducer} from "@/Navigation/Redux";
import * as ProjectRedux from "@/Project/ReduxState";
import {avatarReducer} from "@/UserSettings/Redux";
import {terminalReducer} from "@/Terminal/State";
import hookStore from "@/Utilities/ReduxHooks";
import {popInReducer} from "@/ui-components/PopIn";
import sidebar from "@/Applications/Redux/Reducer";
import {EnhancedStore, configureStore} from "@reduxjs/toolkit";
import {refreshFunctionCache} from "@/ui-components/Sidebar";
import {noopCall} from "@/Authentication/DataHook";

export const CONTEXT_SWITCH = "CONTEXT_SWITCH";
export const USER_LOGIN = "USER_LOGIN";
export const USER_LOGOUT = "USER_LOGOUT";


export function confStore(
    initialObject: ReduxObject,
    reducers,
    enhancers?
): EnhancedStore<ReduxObject> {
    const combinedReducers = combineReducers<ReduxObject, AnyAction>(reducers);
    const rootReducer = (state: ReduxObject, action: Action): ReduxObject => {
        if ([USER_LOGIN, USER_LOGOUT, CONTEXT_SWITCH].some(it => it === action.type)) {
            state = initObject();
        }
        return combinedReducers(state, action);
    };
    return configureStore<ReduxObject>({reducer: rootReducer, preloadedState: initialObject, enhancers: compose(enhancers)});
}

export const store = confStore(initObject(), {
    dashboard: dashboardReducer,
    status: statusReducer,
    hookStore,
    sidebar,
    avatar: avatarReducer,
    terminal: terminalReducer,
    loading,
    project: ProjectRedux.reducer,
    popinChild: popInReducer,
});

function loading(state = false, action: {type: string}): boolean {
    switch (action.type) {
        case "LOADING_START":
            return true;
        case "LOADING_END":
            return false;
        default:
            return state;
    }
}

export function useSetRefreshFunction(refreshFn: () => void): void {
    useEffect(() => {
        refreshFunctionCache.setRefreshFunction(refreshFn);
        return () => {
            refreshFunctionCache.setRefreshFunction(noopCall);
        };
    }, [refreshFn]);
}

export function useRefresh(): () => void {
    return refreshFunctionCache.getSnapshot();
}