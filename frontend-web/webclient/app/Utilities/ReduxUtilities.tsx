import { createStore, combineReducers, Store, AnyAction } from "redux";
import { composeWithDevTools } from 'redux-devtools-extension';
import { ReduxObject } from "DefaultObjects";
import { responsiveBP } from "ui-components/theme";
import { createResponsiveStateReducer } from "redux-responsive";


export function configureStore(initialObject, reducers, enhancers?): Store<ReduxObject, AnyAction> {
    const store = createStore<ReduxObject, AnyAction, {}, {}>(combineReducers(reducers), initialObject, composeWithDevTools(enhancers));
    return store;
};

export const responsive = createResponsiveStateReducer(
    responsiveBP,
    { infinity: "xxl" }
);
