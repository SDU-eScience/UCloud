import * as React from "react";
import {default as JobApi, Job} from "@/UCloud/JobsApi";
import {BaseResourceBrowseProps, ResourceBrowse} from "@/Resource/Browse";
import {ResourceRouter} from "@/Resource/Router";
import Create from "@/Applications/Jobs/Create";
import {useNavigate} from "react-router";
import {BrowseType} from "@/Resource/BrowseType";

export const JobBrowse: React.FunctionComponent<BaseResourceBrowseProps<Job> & {additionalFilters?: Record<string, string>}> = props => {
    const navigate = useNavigate();
    const viewPropertiesInline = React.useCallback(() => props.browseType === BrowseType.Embedded, [props.browseType]);
    return <ResourceBrowse api={JobApi} viewPropertiesInline={viewPropertiesInline} additionalFilters={props.additionalFilters} {...props} browseType={props.browseType ?? BrowseType.MainContent}
        extraCallbacks={{
            startCreation() {navigate("/applications/overview")}
        }}
    />;
}

const JobRouter: React.FunctionComponent = () => {
    return <ResourceRouter api={JobApi} Browser={JobBrowse} Create={Create} />;
}

export default JobRouter;
