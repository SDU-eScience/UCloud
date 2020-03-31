import {APICallParameters} from "Authentication/DataHook";
import {ProjectRole} from "Project/index";

const groupContext = "/projects/groups/";
const projectContext = "/projects/";

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
        path: groupContext,
        parameters: props,
    };
}

export function listGroupMembersRequest(
    props: ListGroupMembersRequestProps
): APICallParameters<ListGroupMembersRequestProps> {
    return {
        method: "GET",
        path: `${groupContext}members`,
        reloadId: Math.random(),
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
        path: `${groupContext}`,
        reloadId: Math.random(),
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
        path: `${groupContext}members`,
        reloadId: Math.random(),
        payload
    };
}

export interface ShouldVerifyMembershipResponse {
    shouldVerify: boolean;
}

export function shouldVerifyMembership(projectId: string): APICallParameters {
    return {
        method: "GET",
        path: `${projectContext}should-verify`,
        reloadId: Math.random(),
        projectOverride: projectId
    };
}

export function verifyMembership(projectId: string): APICallParameters {
    return {
        method: "POST",
        path: `${projectContext}verify-membership`,
        reloadId: Math.random(),
        projectOverride: projectId
    };
}

export function projectRoleToString(role: ProjectRole): string {
    switch (role) {
        case ProjectRole.PI: return "PI";
        case ProjectRole.ADMIN: return "Admin";
        case ProjectRole.USER: return "User";
    }
}

