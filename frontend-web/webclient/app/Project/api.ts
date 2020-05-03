import {APICallParameters} from "Authentication/DataHook";
import {ProjectMember, ProjectRole, UserGroupSummary, UserInProject} from "Project/index";
import {buildQueryString} from "Utilities/URIUtilities";
import {Page, PaginationRequest} from "Types";

const groupContext = "/projects/groups/";
const projectContext = "/projects/";

interface CreateGroupRequest {
    group: string;
}

export interface ListGroupMembersRequestProps extends PaginationRequest {
    group: string;
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
        path: buildQueryString(`${groupContext}members`, props),
        reloadId: Math.random(),
        parameters: props
    };
}

interface AddGroupMemberProps {
    group: string;
    memberUsername: string;
}

export function addGroupMember(payload: AddGroupMemberProps): APICallParameters<AddGroupMemberProps> {
    return {
        method: "PUT",
        path: `${groupContext}/members`,
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

export function groupSummaryRequest(payload: PaginationRequest): APICallParameters<PaginationRequest> {
    return {
        path: buildQueryString(`${groupContext}/summary`, payload),
        method: "GET",
        reloadId: Math.random(),
        payload
    };
}

export interface UserStatusRequest {

}

export interface UserStatusResponse {
    membership: UserInProject[];
    groups: UserGroupSummary[];
}

export function userProjectStatus(request: UserStatusRequest): APICallParameters<UserStatusRequest> {
    return {
        reloadId: Math.random(),
        path: "/projects/membership",
        method: "POST",
        parameters: request,
        payload: request
    };
}

export interface MembershipSearchRequest extends PaginationRequest {
    query: string;
    notInGroup?: string;
}

type MembershipResponse = Page<ProjectMember>;

export function membershipSearch(request: MembershipSearchRequest): APICallParameters<MembershipSearchRequest> {
    return {
        method: "GET",
        path: buildQueryString("/projects/membership/search", request),
        parameters: request
    };
}

export interface UpdateGroupNameRequest {
    oldGroupName: string;
    newGroupName: string;
}

export function updateGroupName(request: UpdateGroupNameRequest): APICallParameters<UpdateGroupNameRequest> {
    return {
        method: "POST",
        path: "/projects/groups/update-name",
        parameters: request,
        payload: request
    };
}

export interface DeleteGroupRequest {
    groups: string[];
}

export function deleteGroup(request: DeleteGroupRequest): APICallParameters<DeleteGroupRequest> {
    return {
        method: "DELETE",
        path: "/projects/groups",
        parameters: request,
        payload: request
    };
}