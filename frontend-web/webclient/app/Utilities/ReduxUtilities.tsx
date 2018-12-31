import { createStore, combineReducers, Store, AnyAction } from "redux";
import { composeWithDevTools } from 'redux-devtools-extension';
import { ReduxObject } from "DefaultObjects";


export function configureStore(initialObject, reducers, enhancers?): Store<ReduxObject, AnyAction> {
    const store = createStore<ReduxObject, AnyAction, {}, {}>(combineReducers(reducers), initialObject, composeWithDevTools(enhancers));
    return store;
};