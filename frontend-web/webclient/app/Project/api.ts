import {APICallParameters} from "Authentication/DataHook";

const baseContext = "/projects/groups/";

interface CreateGroupRequest {
    group: string;
}

export interface ListGroupMembersRequestProps {
    group: string;
    itemsPerPage: number;
    page: number;
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

interface AddGroupMemberProps {
    group: string;
    memberUsername: string;
}

export function addGroupMember(payload: AddGroupMemberProps): APICallParameters<AddGroupMemberProps> {
    return {
        method: "PUT",
        path: `${baseContext}`,
        payload
    };
}

interface RemoveGroupMemberProps {
    group: string;
    memberUsername: string;
}

export function removeGroupMemberRequest(payload: RemoveGroupMemberProps): APICallParameters<RemoveGroupMemberProps> {
    return {
        method: "DELETE",
        path: `${baseContext}members`,
        payload
    };
}
