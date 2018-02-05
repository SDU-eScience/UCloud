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
import {Router, Route, Link, IndexRoute, useRouterHistory} from 'react-router';
import {createHistory} from 'history'

import "uppy/src/scss/uppy.scss";
import "./SiteComponents/Datatable.scss";
import "./SiteComponents/Common/Common";
import "./components/Colors/Colors"
import "./SiteComponents/Common/Utils.scss";
import "./components/Bootstrap/Bootstrap";
import Core from './SiteComponents/Core';
import Dashboard from "./SiteComponents/Dashboard";
import Notifications from "./SiteComponents/Activity/Notifications.jsx";
import Applications from "./SiteComponents/Applications/Applications";
import RunApp from "./SiteComponents/Applications/RunApp";
import Workflows from "./SiteComponents/Applications/Workflows";
import Analyses from "./SiteComponents/Applications/Analyses";
import Status from "./SiteComponents/StatusPage";
import Files from './SiteComponents/Files';
import FileInfo from "./SiteComponents/FileInfo";
import UserAuditing from "./SiteComponents/Admin/UserAuditing";
import { Cloud } from "../authentication/SDUCloudObject";

$(() => {
    Cloud.receiveAccessTokenOrRefreshIt();
    // prevent page reload when using dummy anchors
    $(document).on('click', '[href=""],[href="#"]', () => {
        return false;
    });

    // Support for float labels on inputs
    $(document).on('change', '.mda-form-control > input', function () {
        $(this)[this.value.length ? 'addClass' : 'removeClass']('has-value');
    });

});

const browserHistory = useRouterHistory(createHistory)({
    basename: "/app"
});

const NotFound = () => (<div className="container-fluid"><h1>Page not found</h1></div>);

// Declare routes
ReactDOM.render(
    <Router history={browserHistory}>
        <Route path="/" component={Core}>

            {/* Default route*/}
            <IndexRoute component={Dashboard}/>

            <Route path="dashboard" component={Dashboard} />
            <Route path="files(/**)" component={Files} />
            <Route path="fileInfo/(**)" component={FileInfo}/>
            <Route path="status" component={Status} />
            <Route path="applications" component={Applications}/>
            <Route path="applications/:appName/:appVersion" component={RunApp}/>
            <Route path="workflows" component={Workflows}/>
            <Route path="analyses" component={Analyses}/>

            <Route path="audit">
                <Route path="user/:id" component={UserAuditing}/>
            </Route>

            <Route path="notifications" component={Notifications}/>

            <Route path="*" component={NotFound}/>

        </Route>

    </Router>,
    document.getElementById('app')
);

// Auto close sidebar on route changes
browserHistory.listen(function (ev) {
    $('.sidebar-visible').removeClass('sidebar-visible');
});
