import * as Accounting from "Accounting";
import Activity from "Activity/Page";
import UserCreation from "Admin/UserCreation";
import {dispatchUserAction, onLogin} from "App";
import ApplicationsBrowse from "Applications/Browse";
import DetailedResult from "Applications/DetailedResult";
import * as ApplicationsInstalled from "Applications/Installed";
import JobResults from "Applications/JobResults";
import ApplicationsOverview from "Applications/Overview";
import Run from "Applications/Run";
import ApplicationView from "Applications/View";
import {Cloud} from "Authentication/SDUCloudObject";
import Dashboard from "Dashboard/Dashboard";
import Dialog from "Dialog/Dialog";
import {ErrorBoundary} from "ErrorBoundary/ErrorBoundary";
import Favorites from "Favorites/Favorites";
import FileInfo from "Files/FileInfo";
import FilePreview from "Files/FilePreview";
import Files from "Files/Files";
import {History} from "history";
import {LoginPage} from "Login/Login";
import Wayf from "Login/Wayf";
import {MainContainer} from "MainContainer/MainContainer";
import {USER_LOGIN} from "Navigation/Redux/HeaderReducer";
import Status from "Navigation/StatusPage";
import NoVNCClient from "NoVNC/NoVNCClient";
import ProjectCreate from "Project/Create";
import ProjectList from "Project/List";
import ProjectView from "Project/View";
import * as React from "react";
import {Route, Switch} from "react-router-dom";
import Search from "Search/Search";
import * as Share from "Shares";
import Snackbars from "Snackbar/Snackbars";
import Sidebar from "ui-components/Sidebar";
import Uploader from "Uploader/Uploader";
import AvataaarModification from "UserSettings/Avataaar";
import UserSettings from "UserSettings/UserSettings";

const NotFound = () => (<MainContainer main={<div><h1>Not found.</h1></div>}/>);

const Core = () => {
    return <>
        <Dialog/>
        <Snackbars/>
        <Uploader/>
        <Sidebar/>
        <ErrorBoundary>
            <Switch>
                <Route exact path="/login" component={LoginPage}/>
                <Route exact path="/loginSuccess" component={LoginSuccess}/>
                <Route exact path="/login/wayf" component={Wayf}/>
                <Route exact path="/" component={requireAuth(Dashboard)}/>
                <Route exact path="/dashboard" component={requireAuth(Dashboard)}/>

                <Route exact path="/files/info" component={requireAuth(FileInfo)}/>
                <Route exact path="/files/preview" component={requireAuth(FilePreview)}/>
                <Route exact path="/files" component={requireAuth(Files)}/>

                <Route exact path="/favorites" component={requireAuth(Favorites)}/>
                <Route exact path="/activity" component={requireAuth(Activity)}/>
                <Route exact path="/status" component={requireAuth(Status)}/>
                <Route exact path="/accounting/:resource/:subResource"
                       component={requireAuth(Accounting.DetailedPage)}/>

                <Route exact path="/novnc" component={requireAuth(NoVNCClient)}/>

                <Route exact path="/applications" component={requireAuth(ApplicationsBrowse)}/>
                <Route exact path="/applications/overview" component={requireAuth(ApplicationsOverview)}/>
                <Route exact path="/applications/installed" component={requireAuth(ApplicationsInstalled.default)}/>
                <Route exact path="/applications/details/:appName/:appVersion"
                       component={requireAuth(ApplicationView)}/>
                <Route exact path="/applications/results" component={requireAuth(JobResults)}/>
                <Route exact path="/applications/results/:jobId" component={requireAuth(DetailedResult)}/>
                <Route exact path="/applications/:appName/:appVersion" component={requireAuth(Run)}/>

                <Route exact path="/shares" component={requireAuth(Share.List)}/>

                <Route exact path="/admin/usercreation" component={requireAuth(UserCreation)}/>

                <Route exact path="/users/settings" component={requireAuth(UserSettings)}/>
                <Route exact path="/users/avatar" component={requireAuth(AvataaarModification)}/>

                <Route exact path="/search/:priority" component={requireAuth(Search)}/>

                <Route exact path="/projects" component={requireAuth(ProjectList)}/>
                <Route exact path="/projects/create" component={requireAuth(ProjectCreate)}/>
                <Route exact path="/projects/view/:id" component={requireAuth(ProjectView)}/>

                <Route component={NotFound}/>
            </Switch>
        </ErrorBoundary>
    </>;
};

const requireAuth = Delegate => props => {
    if (!Cloud.isLoggedIn) {
        props.history.push("/login");
        return null;
    }
    return <Delegate {...props} />;
};

const LoginSuccess = (props: { history: History }) => {
    dispatchUserAction(USER_LOGIN);
    onLogin();
    props.history.push("/");
    return null;
};

export default Core;
