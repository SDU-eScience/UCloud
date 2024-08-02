import {useEffect} from "react";
import {Action, AnyAction, combineReducers} from "redux";

import {dashboardReducer} from "@/Dashboard/Redux";
import {initObject} from "@/DefaultObjects";
import {statusReducer} from "@/Navigation/Redux";
import * as ProjectRedux from "@/Project/ReduxState";
import {avatarReducer} from "@/UserSettings/Redux";
import {terminalReducer} from "@/Terminal/State";
import hookStore from "@/Utilities/ReduxHooks";
import {popInReducer} from "@/ui-components/PopIn";
import sidebar from "@/Applications/Redux/Reducer";
import {EnhancedStore, ReducersMapObject, configureStore} from "@reduxjs/toolkit";
import {noopCall} from "@/Authentication/DataHook";

export const CONTEXT_SWITCH = "CONTEXT_SWITCH";
export const USER_LOGIN = "USER_LOGIN";
export const USER_LOGOUT = "USER_LOGOUT";
export type UserActionType = typeof USER_LOGIN | typeof USER_LOGOUT | typeof CONTEXT_SWITCH


export function confStore(
    initialObject: ReduxObject,
    reducers: ReducersMapObject<ReduxObject, AnyAction>,
): EnhancedStore<ReduxObject> {
    const combinedReducers = combineReducers(reducers);
    const rootReducer = (state: ReduxObject, action: Action): ReduxObject => {
        if ([USER_LOGIN, USER_LOGOUT, CONTEXT_SWITCH].some(it => it === action.type)) {
            state = initObject();
        }
        return combinedReducers(state, action);
    };
    return configureStore<ReduxObject>({reducer: rootReducer, preloadedState: initialObject});
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

export const refreshFunctionCache = new class {
    private refresh: () => void = () => void 0;
    private subscribers: (() => void)[] = [];

    public subscribe(subscription: () => void) {
        this.subscribers = [...this.subscribers, subscription];
        return () => {
            this.subscribers = this.subscribers.filter(s => s !== subscription);
        }
    }

    public getSnapshot(): () => void {
        return this.refresh;
    }

    public emitChange(): void {
        for (const sub of this.subscribers) {
            sub();
        }
    }

    public setRefreshFunction(refreshFn: () => void): void {
        this.refresh = refreshFn;
        this.emitChange();
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

export class ExternalStoreBase {
    private subscribers: (() => void)[] = [];
    
    public subscribe(subscription: () => void) {
        this.subscribers = [...this.subscribers, subscription];
        return () => {
            this.subscribers = this.subscribers.filter(s => s !== subscription);
        }
    }

    public emitChange(): void {
        for (const sub of this.subscribers) {
            sub();
        }
    }
}