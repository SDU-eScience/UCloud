import { createStore, combineReducers, Store, AnyAction } from "redux";
import { composeWithDevTools } from 'redux-devtools-extension';
import { ReduxObject, initObject } from "DefaultObjects";
import { responsiveBP } from "ui-components/theme";
import { createResponsiveStateReducer } from "redux-responsive";
import { Cloud } from "Authentication/SDUCloudObject";


export function configureStore(initialObject: Partial<ReduxObject>, reducers, enhancers?): Store<ReduxObject, AnyAction> {
    const combinedReducers = combineReducers<ReduxObject, AnyAction>(reducers);
    const rootReducer = (state: ReduxObject, action): ReduxObject => {
        if (["USER_LOGIN", "USER_LOGOUT"].some(it => it === action.type)) {
            state = initObject(Cloud.homeFolder);
        }
        return combinedReducers(state, action);
    }
    const store = createStore<ReduxObject, AnyAction, {}, {}>(rootReducer, initialObject, composeWithDevTools(enhancers));
    return store;
};

export const responsive = createResponsiveStateReducer(
    responsiveBP,
    { infinity: "xxl" }
);