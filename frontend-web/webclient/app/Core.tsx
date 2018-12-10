import * as React from "react";
import { Switch, Route } from "react-router-dom";
import Files from "Files/Files";
import Dashboard from "Dashboard/Dashboard";
import Applications from "Applications/Browse";
import Run from "Applications/Run";
import JobResults from "Applications/JobResults";
import Header from "Navigation/Header";
import Sidebar from "ui-components/Sidebar";
import ZenodoPublish from "Zenodo/Publish";
import * as Share from "Shares";
import * as Project from "Project";
import Activity from "Activity/Activity";
import Uploader from "Uploader/Uploader";
import { Box } from "ui-components";

// use `const COMPNAME = React.lazy(() => import("${path}"));` when react router is updated
import Search from "Search/Search";
import FileInfo from "Files/FileInfo";
import FilePreview from "Files/FilePreview";
import UserCreation from "Admin/UserCreation";
import UserSettings from "UserSettings/UserSettings";
import ZenodoHome from "Zenodo/Zenodo";
import ZenodoInfo from "Zenodo/Info";
import DetailedResult from "Applications/DetailedResult";
import ApplicationView from "Applications/View";
import * as ApplicationsInstalled from "Applications/Installed";
import Status from "Navigation/StatusPage";

const NotFound = () => (<div><h1>Not found.</h1></div>);

const Core = () => (
    <>
        <Header />
        <Uploader />
        <Sidebar />
        <Box ml="190px" pt="62px" pl="15px" pr="15px">
            <Switch>
                <Route exact path="/" component={Dashboard} />
                <Route exact path="/dashboard" component={Dashboard} />

                <Route exact path="/files/info" component={FileInfo} />
                <Route exact path="/files/preview/*" component={FilePreview} />
                <Route exact path="/files" component={Files} />

                <Route exact path="/activity" component={Activity} />
                <Route exact path="/status" component={Status} />

                <Route exact path="/applications" component={Applications} />
                <Route exact path="/applications/installed" component={ApplicationsInstalled.default} />
                <Route exact path="/applications/details/:appName/:appVersion" component={ApplicationView} />
                <Route exact path="/applications/results" component={JobResults} />
                <Route exact path="/applications/results/:jobId" component={DetailedResult} />
                <Route exact path="/applications/:appName/:appVersion" component={Run} />

                <Route exact path="/zenodo/" component={ZenodoHome} />
                <Route exact path="/zenodo/info/:jobID" component={ZenodoInfo} />
                <Route exact path="/zenodo/publish/" component={ZenodoPublish} />

                <Route exact path="/shares" component={Share.List} />

                <Route exact path="/projects/edit" component={Project.CreateUpdate} />
                <Route exact path="/projects/view" component={Project.ManagedView} />

                <Route exact path="/admin/usercreation" component={UserCreation} />

                <Route exact path="/users/settings" component={UserSettings} />

                <Route exact path="/search/:priority" component={Search} />

                <Route component={NotFound} />
            </Switch>
        </Box>
    </>
);

export default Core;
