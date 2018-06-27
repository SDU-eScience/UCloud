import * as React from "react";
import * as ReactDOM from "react-dom";
import { Provider } from "react-redux";
import { createStore, combineReducers } from "redux";
import { BrowserRouter } from "react-router-dom";
import Core from "./SiteComponents/Core";
import { Cloud } from "../authentication/SDUCloudObject";
import files from "./Reducers/Files";
import uppyReducers from "./Reducers/UppyReducers";
import status from "./Reducers/Status";
import applications from "./Reducers/Applications";
import dashboard from "./Reducers/Dashboard";
import zenodo from "./Reducers/Zenodo";
import sidebar from "./Reducers/Sidebar";
import analyses from "./Reducers/Analyses";
import notifications from "./Reducers/Notifications";
import { initObject } from "./DefaultObjects";
import "semantic-ui-css/semantic.min.css"

window.onload = () => {
    Cloud.receiveAccessTokenOrRefreshIt();
};

// Middleware allowing for dispatching promises.
const addPromiseSupportToDispatch = (store) => {
    const rawDispatch = store.dispatch;
    return (action) => {
        if (typeof action.then === "function") {
            return action.then(rawDispatch);
        }
        return rawDispatch(action);
    };
};

const rootReducer = combineReducers({ files, dashboard, analyses, applications, uppy: uppyReducers, status, zenodo, sidebar, notifications });

const configureStore = (initialObject) => {
    let store = createStore(rootReducer, initialObject);
    store.dispatch = addPromiseSupportToDispatch(store);
    return store;
};

let store = configureStore(initObject(Cloud));

ReactDOM.render(
    <Provider store={store}>
        <BrowserRouter basename="app">
            <Core />
        </BrowserRouter>
    </Provider>,
    document.getElementById("app")
);
