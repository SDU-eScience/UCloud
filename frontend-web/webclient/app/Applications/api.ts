import {APICallParameters} from "Authentication/DataHook";
import {buildQueryString} from "Utilities/URIUtilities";

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
