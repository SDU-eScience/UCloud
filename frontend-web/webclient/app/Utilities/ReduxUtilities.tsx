import { createStore, combineReducers, Store, AnyAction } from "redux";
import { ReduxObject, Reducers } from "DefaultObjects";

export function configureStore(initialObject, reducers): Store<ReduxObject, AnyAction> {
    const store = createStore<ReduxObject, AnyAction, {}, {}>(combineReducers(reducers), initialObject);
    store.dispatch = addPromiseSupportToDispatch(store);
    return store;
};

const addPromiseSupportToDispatch = (store) => {
    const rawDispatch = store.dispatch;
    return (action) => {
        if (typeof action.then === "function") {
            return action.then(rawDispatch);
        }
        return rawDispatch(action);
    };
};