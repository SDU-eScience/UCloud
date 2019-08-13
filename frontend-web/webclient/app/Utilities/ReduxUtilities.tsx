import {initObject, ReduxObject} from "DefaultObjects";
import {CONTEXT_SWITCH, USER_LOGIN, USER_LOGOUT} from "Navigation/Redux/HeaderReducer";
import {Action, AnyAction, combineReducers, createStore, Store} from "redux";
import {composeWithDevTools} from "redux-devtools-extension";
import {createResponsiveStateReducer} from "redux-responsive";
import {responsiveBP} from "ui-components/theme";

export function configureStore(
    initialObject: Partial<ReduxObject>,
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