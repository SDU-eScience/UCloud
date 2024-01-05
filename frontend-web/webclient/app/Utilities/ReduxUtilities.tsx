import dashboard from "@/Dashboard/Redux/DashboardReducer";
import {initObject} from "@/DefaultObjects";
import status from "@/Navigation/Redux/StatusReducer";
import * as ProjectRedux from "@/Project/Redux";
import {Action, AnyAction, combineReducers, compose} from "redux";
import avatar from "@/UserSettings/Redux/AvataaarReducer";
import {terminalReducer} from "@/Terminal/State";
import hookStore from "@/Utilities/ReduxHooks";
import {popInReducer} from "@/ui-components/PopIn";
import sidebar from "@/Applications/Redux/Reducer";
import {EnhancedStore, configureStore} from "@reduxjs/toolkit";
import {useEffect} from "react";
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
    dashboard,
    status,
    hookStore,
    sidebar,
    avatar,
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