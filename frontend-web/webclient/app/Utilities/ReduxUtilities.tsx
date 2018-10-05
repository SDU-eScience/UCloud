import { createStore, combineReducers, Store, AnyAction } from "redux";
import { ReduxObject } from "DefaultObjects";

export function configureStore(initialObject, reducers): Store<ReduxObject, AnyAction> {
    const store = createStore<ReduxObject, AnyAction, {}, {}>(combineReducers(reducers), initialObject);
    return store;
};