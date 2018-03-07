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

import React from 'react';
import ReactDOM from 'react-dom';
import { BrowserRouter } from "react-router-dom";
import Core from './SiteComponents/Core';
import { Cloud } from "../authentication/SDUCloudObject";

window.onload = () => {
    Cloud.receiveAccessTokenOrRefreshIt();
};

// Declare routes
ReactDOM.render(
    <BrowserRouter basename="app">
        <Core/>
    </BrowserRouter>,
    document.getElementById("app")
);