import {APICallParameters} from "Authentication/DataHook";

const baseContext = "/projects/groups/";

interface CreateGroupRequest {
    group: string;
}

interface ListGroupMembersRequestProps {
    group: string;
    itemsPerPage?: number;
    page?: number;
}

export function createGroup(props: CreateGroupRequest): APICallParameters<{}> {
    return {
        reloadId: Math.random(),
        method: "PUT",
        path: baseContext,
        parameters: props,
    };
}

export function listGroupMembersRequest(
    props: ListGroupMembersRequestProps
): APICallParameters<ListGroupMembersRequestProps> {
    return {
        method: "GET",
        path: `${baseContext}members`,
        payload: props
    };
}
