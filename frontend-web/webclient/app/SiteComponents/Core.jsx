import React from "react";
import { Switch, Route } from "react-router-dom";
import PropTypes from "prop-types";
import Files from "./Files/Files";
import FileInfo from "./Files/FileInfo";
import Dashboard from "./Dashboard/Dashboard";
import Status from "./Navigation/StatusPage";
import Applications from "./Applications/Applications";
import RunApp from "./Applications/RunApp";
import Analyses from "./Applications/Analyses";
import DetailedResult from "./Applications/DetailedResult";
import Notifications from "./Activity/Notifications";
import Header from "./Navigation/Header";
import Sidebar from "../SiteComponents/Navigation/Sidebar";
import UserAuditing from "./Admin/UserAuditing";
import ZenodoPublish from "./Zenodo/Publish";
import ZenodoHome from "./Zenodo/Zenodo";
import ZenodoInfo from "./Zenodo/Info";
import { connect } from "react-redux";
import UppyWrapper from "./UppyWrapper";
import * as Share from "./Shares";
import * as Metadata from "./Metadata";

const NotFound = () => (<div><h1>Not found.</h1></div>);

const Core = (props) => (
    <React.Fragment>
        <Header />
        <Sidebar>
            <Switch>
                <Route path="/files/*" component={Files} />
                <Route exact path="/dashboard" component={Dashboard} />
                <Route exact path="/fileInfo/*" component={FileInfo} />
                <Route exact path="/status" component={Status} />
                <Route exact path="/applications" component={Applications} />
                <Route exact path="/applications/:appName/:appVersion" component={RunApp} />
                <Route exact path="/analyses" component={Analyses} />
                <Route exact path="/analyses/:jobId" component={DetailedResult} />
                <Route exact path="/audit/user/:id" component={UserAuditing} />
                <Route exact path="/notifications" component={Notifications} />
                <Route exact path="/zenodo/" component={ZenodoHome} />
                <Route exact path="/zenodo/info/:jobID" component={ZenodoInfo} />
                <Route exact path="/zenodo/publish/" component={ZenodoPublish} />
                <Route exact path="/shares" component={Share.List} />
                <Route exact path="/metadata" component={Metadata.CreateUpdate} />
                <Route exact path="/metadata/:id" component={Metadata.ManagedView} />
                <Route exact path="/metadata/search/:query" component={Metadata.Search} />
                <Route component={NotFound} />
            </Switch>
            <div className="footer">
                {new Date().getFullYear()} - SDUCloud
            </div>
        </Sidebar>
        <UppyWrapper />
    </React.Fragment >
);

export default (Core);
