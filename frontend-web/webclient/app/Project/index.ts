import {APICallParameters} from "Authentication/DataHook";
import {buildQueryString} from "Utilities/URIUtilities";
import {Page, PaginationRequest} from "Types";
import {Client} from "Authentication/HttpClientInstance";
import {DEV_SITE, STAGING_SITE} from "../../site.config.json";
import {inDevEnvironment} from "UtilityFunctions";
import {IconName} from "ui-components/Icon";

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

export function projectStringToRole(role: string): ProjectRole {
    switch (role) {
        case "PI": return ProjectRole.PI;
        case "Admin": return ProjectRole.ADMIN;
        case "User": return ProjectRole.USER;
    }
    return ProjectRole.USER;
}

export function projectRoleToStringIcon(role: ProjectRole): IconName {
    switch (role) {
        case ProjectRole.PI: return "userPi";
        case ProjectRole.ADMIN: return "userAdmin";
        case ProjectRole.USER: return "user";
    }
}

export function groupSummaryRequest(payload: PaginationRequest): APICallParameters<PaginationRequest> {
    return {
        path: buildQueryString(`${groupContext}/summary`, payload),
        method: "GET",
        reloadId: Math.random(),
        parameters: payload,
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
    favorite: boolean;
    archived: boolean;
}

export interface UserGroupSummary {
    projectId: string;
    group: string;
    username: string;
}

export const createProject = (payload: {title: string}): APICallParameters => ({
    method: "POST",
    path: "/projects",
    payload,
    reloadId: Math.random()
});

export const inviteMember = (payload: {projectId: string; usernames: string[]}): APICallParameters => ({
    method: "POST",
    path: "/projects/invites",
    payload,
    reloadId: Math.random()
});

export const deleteMemberInProject = (payload: {projectId: string; member: string}): APICallParameters => ({
    method: "DELETE",
    path: "/projects/members",
    payload,
    reloadId: Math.random()
});

export const changeRoleInProject = (
    payload: {projectId: string; member: string; newRole: ProjectRole}
): APICallParameters => ({
    method: "POST",
    path: "/projects/members/change-role",
    payload,
    reloadId: Math.random()
});

export interface ListProjectsRequest extends PaginationRequest{
    archived?: boolean;
    noFavorites?: boolean;
}

export const listProjects = (parameters: ListProjectsRequest): APICallParameters<ListProjectsRequest> => ({
    method: "GET",
    path: buildQueryString(
        "/projects/list",
        parameters
    ),
    parameters,
    reloadId: Math.random()
});

export interface ListFavoriteProjectsRequest extends PaginationRequest {
    archived: boolean;
}

export const listFavoriteProjects = (parameters: ListFavoriteProjectsRequest): APICallParameters<ListFavoriteProjectsRequest> => ({
    method: "GET",
    path: buildQueryString(
        "/projects/listFavorites",
        parameters
    ),
    parameters,
    reloadId: Math.random()
});

export const roleInProject = (project: ProjectMember[]): ProjectRole | undefined => {
    const member = project.find(m => {
        return m.username === Client.username;
    });

    if (member === undefined) return undefined;
    return member.role;
};


export interface ToggleProjectFavorite{
    projectId: string;
}

export function toggleFavoriteProject(request: ToggleProjectFavorite): APICallParameters<ToggleProjectFavorite> {
    return {
        method: "POST",
        path: "/projects/favorite",
        parameters: request,
        payload: request
    };
}

export interface OutgoingInvite {
    username: string;
    invitedBy: string;
    timestamp: number;
}

export interface ListOutgoingInvitesRequest extends PaginationRequest {

}

export function listOutgoingInvites(request: ListOutgoingInvitesRequest): APICallParameters<ListOutgoingInvitesRequest> {
    return {
        method: "GET",
        path: buildQueryString("/projects/invites/outgoing", request),
        parameters: request
    };
}

export interface IngoingInvite {
    project: string;
    invitedBy: string;
    timestamp: string;
}

export interface ListIngoingInvitesRequest extends PaginationRequest {

}

export function listIngoingInvites(request: ListIngoingInvitesRequest): APICallParameters<ListIngoingInvitesRequest> {
    return {
        method: "GET",
        path: buildQueryString("/projects/invites/ingoing", request),
        parameters: request
    };
}

export interface AcceptInviteRequest {
    projectId: string;
}

export function acceptInvite(request: AcceptInviteRequest): APICallParameters<AcceptInviteRequest> {
    return {
        method: "POST",
        path: "/projects/invites/accept",
        payload: request,
        parameters: request
    };
}

export interface RejectInviteRequest {
    projectId: string;
    username?: string;
}

export function rejectInvite(request: RejectInviteRequest): APICallParameters<RejectInviteRequest> {
    return {
        method: "DELETE",
        path: "/projects/invites/reject",
        parameters: request,
        payload: request
    };
}

type LeaveProjectRequest = {};
export function leaveProject(request: LeaveProjectRequest): APICallParameters<LeaveProjectRequest> {
    return {
        method: "DELETE",
        path: "/projects/leave",
        parameters: request,
        payload: request
    };
}

export interface TransferPiRoleRequest {
    newPrincipalInvestigator: string;
}

export function transferPiRole(request: TransferPiRoleRequest): APICallParameters<TransferPiRoleRequest> {
    return {
        method: "POST",
        path: "/projects/transfer-pi",
        parameters: request,
        payload: request
    };
}

export type ListRepositoryRequest = PaginationRequest;

export function listRepositoryFiles(
    request: ListRepositoryRequest,
    projectOverride?: string
): APICallParameters<ListRepositoryRequest> {
    return {
        method: "GET",
        path: buildQueryString("/projects/repositories/list-files", request),
        parameters: request,
        reloadId: Math.random(),
        projectOverride
    };
}

export interface ArchiveProjectRequest {
    archiveStatus: boolean;
}

export function setProjectArchiveStatus(request: ArchiveProjectRequest): APICallParameters<ArchiveProjectRequest> {
    return {
        method: "POST",
        path: "/projects/archive",
        parameters: request,
        payload: request,
        reloadId: Math.random()
    };
}

export interface ViewProjectRequest {
    id: string;
}

export function viewProject(request: ViewProjectRequest): APICallParameters<ViewProjectRequest> {
    return {
        method: "GET",
        path: buildQueryString("/projects/view", request),
        parameters: request,
        reloadId: Math.random()
    };
}

export function areProjectsEnabled(): boolean {
    if ([DEV_SITE, STAGING_SITE].includes(window.location.host) || inDevEnvironment()) {
        return true;
    }
    return Client.userRole === "ADMIN";
}
