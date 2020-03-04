import {APICallParameters} from "Authentication/DataHook";
import {buildQueryString} from "Utilities/URIUtilities";

const baseContext = "/projects/groups";

interface CreateGroupRequest {
    group: string;
}

export function createGroup(props: CreateGroupRequest): APICallParameters<{}> {
    return {
        reloadId: Math.random(),
        method: "PUT",
        path: baseContext,
        parameters: props,
    };
}
