import * as React from "react";
import {default as JobApi, Job} from "UCloud/JobsApi";
import {BaseResourceBrowseProps, ResourceBrowse} from "Resource/Browse";
import {ResourceRouter} from "Resource/Router";
import Create from "Applications/Jobs/Create";

const JobBrowse: React.FunctionComponent<BaseResourceBrowseProps<Job>> = props => {
    return <ResourceBrowse api={JobApi} {...props} />;
}

const JobRouter: React.FunctionComponent = () => {
    return <ResourceRouter api={JobApi} Browser={JobBrowse} Create={Create}/>;
}

export default JobRouter;
