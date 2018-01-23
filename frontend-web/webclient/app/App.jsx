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
import "../app/components/Tables/Datatable.scss";
import Core from './components/Core/Core';
import Bootstrap from './components/Bootstrap/Bootstrap';
import Common from './components/Common/Common';
import Colors from './components/Colors/Colors';
import FloatButton from './components/FloatButton/FloatButton';
import Translate from './components/Translate/Translate';
import Dashboard from './SiteComponents/Dashboard';
import Notifications from './SiteComponents/Activity/Notifications.jsx';
import Applications from './SiteComponents/Applications/Applications';
import RunApp from './SiteComponents/Applications/RunApp';
import Workflows from './SiteComponents/Applications/Workflows';
import Analyses from './SiteComponents/Applications/Analyses';
import Status from './SiteComponents/StatusPage'
import Files from './SiteComponents/Files'
import User from './components/User/User';
import Utils from './components/Utils/Utils';
import { Cloud } from '../authentication/SDUCloudObject'



Translate();

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

const browserHistory = useRouterHistory(createHistory)({basename: REACT_BASE_HREF});

const NotFound = () => (<div className="container-fluid"><h1>Page not found</h1></div>);

// Declare routes
ReactDOM.render(
    <Router history={browserHistory}>
        <Route path="/" component={Core}>

            {/* Default route*/}
            <IndexRoute component={Dashboard}/>

            <Route path="dashboard" component={Dashboard} />
            <Route path="files(/**)" component={Files} />
            <Route path="status" component={Status} />
            <Route path="apps">
                <Route path="applications" component={Applications}/>
                <Route path="workflows" component={Workflows}/>
                <Route path="analyses" component={Analyses}/>
            </Route>
            <Route path="runApp/:appName/:appVersion" component={RunApp} />

            <Route path="activity">
                <Route path="notifications" component={Notifications}/>
            </Route>

            <Route path="*" component={NotFound}/>

        </Route>

        {/* User Pages */}
        <Route path="/" component={User.Layout}>
            <Route path="login" component={User.Login}/>
            <Route path="signup" component={User.Signup}/>
            <Route path="recover" component={User.Recover}/>
            <Route path="lock" component={User.Lock}/>
        </Route>

    </Router>,
    document.getElementById('app')
);

// Auto close sidebar on route changes
browserHistory.listen(function (ev) {
    $('.sidebar-visible').removeClass('sidebar-visible');
});
