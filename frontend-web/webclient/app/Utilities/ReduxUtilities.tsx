import dashboard from "@/Dashboard/Redux/DashboardReducer";
import {initObject} from "@/DefaultObjects";
import header, {CONTEXT_SWITCH, USER_LOGIN, USER_LOGOUT} from "@/Navigation/Redux/HeaderReducer";
import status from "@/Navigation/Redux/StatusReducer";
import * as ProjectRedux from "@/Project/Redux";
import {Action, AnyAction, combineReducers, createStore, Store} from "redux";
import {composeWithDevTools} from "redux-devtools-extension";
import {createResponsiveStateReducer, responsiveStoreEnhancer} from "redux-responsive";
import {responsiveBP} from "@/ui-components/theme";
import avatar from "@/UserSettings/Redux/AvataaarReducer";
import {terminalReducer} from "@/Terminal/State";
import hookStore from "@/Utilities/ReduxHooks";

export function configureStore(
    initialObject: ReduxObject,
    reducers,
    enhancers?
): Store<ReduxObject, AnyAction> {
    const combinedReducers = combineReducers<ReduxObject, AnyAction>(reducers);
    const rootReducer = (state: ReduxObject, action: Action): ReduxObject => {
        if ([USER_LOGIN, USER_LOGOUT, CONTEXT_SWITCH].some(it => it === action.type)) {
            state = initObject();
        }
        return combinedReducers(state, action);
    };
    return createStore<ReduxObject, AnyAction, {}, {}>(rootReducer, initialObject, composeWithDevTools(enhancers));
}

export const responsive = createResponsiveStateReducer(
    responsiveBP,
    {infinity: "xxl"}
);

export const store = configureStore(initObject(), {
    dashboard,
    header,
    status,
    hookStore,
    avatar,
    terminal: terminalReducer,
    loading,
    project: ProjectRedux.reducer,
    responsive: createResponsiveStateReducer(
        responsiveBP,
        {infinity: "xxl"}),
}, responsiveStoreEnhancer);

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
