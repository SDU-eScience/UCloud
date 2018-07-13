import * as React from "react";
import * as ReactDOM from "react-dom";
import { Provider } from "react-redux";
import { createStore, combineReducers } from "redux";
import { BrowserRouter } from "react-router-dom";
import Core from "./Core";
import { Cloud } from "../authentication/SDUCloudObject";
import files from "./Files/Redux/FilesReducer";
import uppyReducers from "./Uppy/Redux/UppyReducers";
import status from "./Navigation/Redux/StatusReducer";
import applications from "./Applications/Redux/ApplicationsReducer";
import dashboard from "./Dashboard/Redux/DashboardReducer";
import zenodo from "./Zenodo/Redux/ZenodoReducer";
import sidebar from "./Navigation/Redux/SidebarReducer";
import analyses from "./Applications/Redux/AnalysesReducer";
import notifications from "./Notifications/Redux/NotificationsReducer";
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
