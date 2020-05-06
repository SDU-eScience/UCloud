import {APICallParameters} from "Authentication/DataHook";
import {buildQueryString} from "Utilities/URIUtilities";
import {Page, PaginationRequest} from "Types";
import {Client} from "Authentication/HttpClientInstance";

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

export interface ProjectMember {
    username: string;
    role: ProjectRole;
}

export interface Project {
    id: string;
    title: string;
    members: ProjectMember[];
}

export const emptyProject = (id: string): Project => ({
    id,
    title: "",
    members: []
});

export enum ProjectRole {
    PI = "PI",
    ADMIN = "ADMIN",
    USER = "USER"
}

export interface UserInProject {
    projectId: string;
    title: string;
    whoami: ProjectMember;
    needsVerification: boolean;
}

export interface UserGroupSummary {
    projectId: string;
    group: string;
    username: string;
}

// TODO This is a service only API. We need a gateway API which is responsible for also creating a data management plan
export const createProject = (payload: {title: string; principalInvestigator: string}): APICallParameters => ({
    method: "POST",
    path: "/projects",
    payload,
    reloadId: Math.random(),
    disallowProjects: true
});

export const viewProject = (payload: {id: string}): APICallParameters => ({
    method: "GET",
    path: buildQueryString("/projects/", payload),
    reloadId: Math.random(),
    disallowProjects: true
});

export const addMemberInProject = (payload: {projectId: string; member: ProjectMember}): APICallParameters => ({
    method: "POST",
    path: "/projects/members",
    payload,
    reloadId: Math.random(),
    disallowProjects: true
});

export const deleteMemberInProject = (payload: {projectId: string; member: string}): APICallParameters => ({
    method: "DELETE",
    path: "/projects/members",
    payload,
    reloadId: Math.random(),
    disallowProjects: true
});

export const changeRoleInProject = (
    payload: {projectId: string; member: string; newRole: ProjectRole}
): APICallParameters => ({
    method: "POST",
    path: "/projects/members/change-role",
    payload,
    reloadId: Math.random(),
    disallowProjects: true
});

export interface ListProjectsRequest {
    page: number;
    itemsPerPage: number;
}

export const listProjects = (parameters: ListProjectsRequest): APICallParameters<ListProjectsRequest> => ({
    method: "GET",
    path: buildQueryString(
        "/projects/list",
        parameters
    ),
    parameters,
    reloadId: Math.random(),
    disallowProjects: true
});

export const roleInProject = (project: ProjectMember[]): ProjectRole | undefined => {
    const member = project.find(m => {
        return m.username === Client.username;
    });

    if (member === undefined) return undefined;
    return member.role;
};
