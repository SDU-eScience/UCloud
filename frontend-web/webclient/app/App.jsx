/*!
 *
 * Centric - Bootstrap Admin App + ReactJS
 *
 * Version: 1.9.5
 * Author: @themicon_co
 * Website: http://themicon.co
 * License: https://wrapbootstrap.com/help/licenses
 *
*/

import React from "react";
import ReactDOM from "react-dom";
import { connect, Provider } from "react-redux";
import { createStore } from "redux";
import { BrowserRouter } from "react-router-dom";
import Core from './SiteComponents/Core';
import { Cloud } from "../authentication/SDUCloudObject";
import files from "./Reducers/Files";
import { initObject } from "./DefaultObjects"

window.onload = () => {
    Cloud.receiveAccessTokenOrRefreshIt();
};

// Middleware allowing for dispatching promises.
const addPromiseSupportToDispatch = (store) => {
    const rawDispatch = store.dispatch;
    return (action) => {
        if (typeof action.then === 'function') {
            return action.then(rawDispatch);
        }
        return rawDispatch(action);
    };
};

const configureStore = () => {
    let store = createStore(files, initObject);
    store.dispatch = addPromiseSupportToDispatch(store);
    return store;
};

let store = configureStore();

ReactDOM.render(
    <Provider store={store}>
        <BrowserRouter basename="app">
            <Core/>
        </BrowserRouter>
    </Provider>,
    document.getElementById("app")
);