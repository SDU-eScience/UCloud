import * as React from "react";
import { Switch, Route } from "react-router-dom";
import Files from "Files/Files";
import FileInfo from "Files/FileInfo";
import Dashboard from "Dashboard/Dashboard";
import Status from "Navigation/StatusPage";
import Applications from "Applications/Applications";
import DetailedApplication from "Applications/DetailedApplication";
import RunApp from "Applications/RunApp";
import Analyses from "Applications/Analyses";
import DetailedResult from "Applications/DetailedResult";
import Header from "Navigation/Header";
import Sidebar from "Navigation/Sidebar";
import ZenodoPublish from "Zenodo/Publish";
import ZenodoHome from "Zenodo/Zenodo";
import ZenodoInfo from "Zenodo/Info";
import UppyWrapper from "Uppy/UppyWrapper";
import UserCreation from "Admin/UserCreation";
import UserSettings from "UserSettings/UserSettings";
import DetailedFileSearch from "Files/DetailedFileSearch";
import SimpleSearch from "SimpleSearch/SimpleSearch";
import Projects from "Projects/Projects";
import FilePreview from "Files/FilePreview";
import * as Share from "Shares";
import * as Metadata from "Metadata";
import Uploader from "Uploader/Uploader";
import Activity from "Activity/Activity";

const NotFound = () => (<div><h1>Not found.</h1></div>);

const Core = () => (
    <>
        <Header />
        <Uploader />
        <Sidebar>
            <Switch>
                <Route path="/files/*" component={Files} />
                <Route exact path="/dashboard" component={Dashboard} />
                <Route exact path="/" component={Dashboard} />
                <Route exact path="/fileInfo/*" component={FileInfo} />
                <Route exact path="/filepreview/*" component={FilePreview} />
                <Route exact path="/activity/*" component={Activity} />
                <Route exact path="/status" component={Status} />
                <Route exact path="/fileSearch" component={DetailedFileSearch} />
                <Route exact path="/applications" component={Applications} />
                <Route exact path="/applications/:appName/:appVersion" component={RunApp} />
                <Route exact path="/appDetails/:appName/:appVersion" component={DetailedApplication} />
                <Route exact path="/analyses" component={Analyses} />
                <Route exact path="/analyses/:jobId" component={DetailedResult} />
                <Route exact path="/zenodo/" component={ZenodoHome} />
                <Route exact path="/zenodo/info/:jobID" component={ZenodoInfo} />
                <Route exact path="/zenodo/publish/" component={ZenodoPublish} />
                <Route exact path="/shares" component={Share.List} />
                <Route exact path="/metadata/edit/*" component={Metadata.CreateUpdate} />
                <Route exact path="/metadata/search/:query?" component={Metadata.Search} />
                <Route exact path="/metadata/*" component={Metadata.ManagedView} />
                <Route exact path="/admin/usercreation" component={UserCreation} />
                <Route exact path="/usersettings/settings" component={UserSettings} />
                <Route exact path="/simpleSearch/:priority/*" component={SimpleSearch} />
                <Route exact path="/projects" component={Projects} />
                <Route component={NotFound} />
            </Switch>
        </Sidebar>
        <UppyWrapper />
    </ >
);

export default Core;
