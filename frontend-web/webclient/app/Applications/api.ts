import {APICallParameters} from "Authentication/DataHook";
import {buildQueryString} from "Utilities/URIUtilities";
import {SortOrder} from "Files";
import {AppState, RunsSortBy} from "Applications/index";

export interface ListByNameProps {
    itemsPerPage: number
    page: number
    name: string
}

export function listByName({name, itemsPerPage, page}: ListByNameProps): APICallParameters<ListByNameProps> {
    return {
        reloadId: Math.random(),
        method: "GET",
        path: buildQueryString(`/hpc/apps/${name}`, {itemsPerPage, page}),
        parameters: {name, itemsPerPage, page}
    };
}

export interface ListJobsProps {
    itemsPerPage: number
    page: number
    order?: SortOrder
    sortBy?: RunsSortBy
    minTimestamp?: number
    maxTimestamp?: number
    filter?: AppState
    application?: string
    version?: string
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
    }
}