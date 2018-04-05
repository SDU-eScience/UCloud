import React from "react";
import "./Core.scss";
import "./LayoutVariants.scss";
import {Switch, Route} from "react-router-dom";
import PropTypes from "prop-types";
import Files from "./Files";
import FileInfo from "./FileInfo";
import Dashboard from "./Dashboard";
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
import {connect} from "react-redux";
import {changeUppyOpen} from "../Actions/UppyActions";
import UppyWrapper from "./UppyWrapper"

const NotFound = () => (<div className="container-fluid"><h1>Not found.</h1></div>);

const Core = () => (
    <div className="layout-container">
        <Header/>
        <Sidebar/>
        <div className="sidebar-layout-obfuscator"/>
        <div className="main-container">
            <Switch>
                <Route path="/files/*" component={Files}/>
                <Route exact path="/dashboard" component={Dashboard}/>
                <Route exact path="/fileInfo/*" component={FileInfo}/>
                <Route exact path="/status" component={Status}/>
                <Route exact path="/applications" component={Applications}/>
                <Route exact path="/applications/:appName/:appVersion" component={RunApp}/>
                <Route exact path="/analyses" component={Analyses}/>
                <Route exact path="/analyses/:jobId" component={DetailedResult}/>
                <Route exact path="/audit/user/:id" component={UserAuditing}/>
                <Route exact path="/notifications" component={Notifications}/>
                <Route exact path="/zenodo/" component={ZenodoHome}/>
                <Route exact path="/zenodo/info/:jobID" component={ZenodoInfo}/>
                <Route exact path="/zenodo/publish/" component={ZenodoPublish}/>
                <Route component={NotFound}/>
            </Switch>
            <footer>
                <span>{new Date().getFullYear()} - SDUCloud.</span>
            </footer>
            <UppyWrapper/>
        </div>
    </div>
);

export default Core;
