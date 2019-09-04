import {JobState, RunsSortBy} from "Applications/index";
import {APICallParameters} from "Authentication/DataHook";
import {SortOrder} from "Files";
import {buildQueryString} from "Utilities/URIUtilities";

export interface ListByNameProps {
    itemsPerPage: number;
    page: number;
    name: string;
}


export function listByName({name, itemsPerPage, page}: ListByNameProps): APICallParameters<ListByNameProps> {
    return {
        reloadId: Math.random(),
        method: "GET",
        path: buildQueryString(`/hpc/apps/${name}`, {itemsPerPage, page}),
        parameters: {name, itemsPerPage, page}
    };
}

export interface ListApplicationsProps {
    page: number;
    itemsPerPage: number;
}

export function listApplications(props: ListApplicationsProps): APICallParameters<ListApplicationsProps> {
   return {
       reloadId: Math.random(),
       method: "GET",
       path: buildQueryString("/hpc/apps", props),
       parameters: props
   };
}

export type ListToolsProps = ListApplicationsProps;

export function listTools(props: ListToolsProps): APICallParameters<ListToolsProps> {
    return {
        reloadId: Math.random(),
        method: "GET",
        path: buildQueryString("/hpc/tools", props),
        parameters: props
    };
}

export interface ListJobsProps {
    itemsPerPage: number;
    page: number;
    order?: SortOrder;
    sortBy?: RunsSortBy;
    minTimestamp?: number;
    maxTimestamp?: number;
    filter?: JobState;
    application?: string;
    version?: string;
}

export function listJobs(props: ListJobsProps): APICallParameters<ListJobsProps> {
    return {
        reloadId: Math.random(),
        method: "GET",
        parameters: props,
        path: buildQueryString(
            "/hpc/jobs",
            props
        )
    };
}
