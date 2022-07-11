import {apiBrowse, apiSearch, useGlobalCloudAPI} from "@/Authentication/DataHook";
import {buildQueryString} from "@/Utilities/URIUtilities";
import {Client} from "@/Authentication/HttpClientInstance";
import {IconName} from "@/ui-components/Icon";
import {useHistory, useParams} from "react-router";
import {useSelector} from "react-redux";
import {emptyPage} from "@/DefaultObjects";
import {useProjectStatus} from "./cache";
import {useGlobal} from "@/Utilities/ReduxHooks";
import {GroupWithSummary} from "./GroupList";
import {useCallback, useEffect} from "react";
import {isAdminOrPI} from "@/Utilities/ProjectUtilities";
import {usePromiseKeeper} from "@/PromiseKeeper";
import * as React from "react";
import {accounting, PaginationRequestV2} from "@/UCloud";
import {ChargeType, ProductPriceUnit, ProductType} from "@/Accounting";
import ProductCategoryId = accounting.ProductCategoryId;

const groupContext = "/projects/groups/";
const projectContext = "/projects/";

interface CreateGroupRequest {
    group: string;
}

export interface ListGroupMembersRequestProps extends PaginationRequest {
    group: string;
}

export function createGroup(props: CreateGroupRequest): APICallParameters {
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

export function membersCountRequest(): APICallParameters {
    return {
        method: "GET",
        path: `${projectContext}membership/count`,
        reloadId: Math.random(),
    };
}

export function groupsCountRequest(): APICallParameters {
    return {
        method: "GET",
        path: `${groupContext}count`,
        reloadId: Math.random(),
    };
}

export function subprojectsCountRequest(): APICallParameters {
    return {
        method: "GET",
        path: `${projectContext}sub-projects-count`,
        reloadId: Math.random(),
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

export function groupSummaryRequest(payload: PaginationRequest, projectOverride?: string): APICallParameters<PaginationRequest> {
    return {
        path: buildQueryString(`${groupContext}/summary`, payload),
        method: "GET",
        reloadId: Math.random(),
        parameters: payload,
        payload,
        projectOverride
    };
}

export type UserStatusRequest = Record<string, never>;

export interface ProjectName {
    title: string;
    projectId: string;
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
    groupId: string;
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
    memberOfAnyGroup?: boolean;
}

export interface Project {
    id: string;
    title: string;
    parent?: string;
    archived: boolean;
    fullPath?: string;
}

export interface MemberInProject {
    role?: ProjectRole;
    project: Project;
}

export interface ProjectGroup {
    id: string,
    title: string
}

export const emptyProject = (id: string): Project => ({
    id,
    title: "",
    parent: undefined,
    archived: false
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
    parent?: string;
    ancestorPath?: string;
}

export interface UserGroupSummary {
    project: string;
    group: string;
    username: string;
}

export const createProject = (payload: {title: string; parent?: string; principalInvestigator?: string}): APICallParameters => ({
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

export interface ListProjectsRequest extends PaginationRequest {
    archived?: boolean;
    noFavorites?: boolean;
    showAncestorPath?: boolean;
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

export const listSubprojects = (parameters: ListSubprojectsRequest): APICallParameters<PaginationRequestV2> => ({
    method: "GET",
    path: buildQueryString(
        "/projects/sub-projects",
        parameters
    ),
    parameters,
    reloadId: Math.random()
});

export interface ListFavoriteProjectsRequest extends PaginationRequest {
    archived: boolean;
    showAncestorPath?: boolean;
}

export function listFavoriteProjects(
    parameters: ListFavoriteProjectsRequest
): APICallParameters<ListFavoriteProjectsRequest> {
    return {
        method: "GET",
        path: buildQueryString(
            "/projects/listFavorites",
            parameters
        ),
        parameters,
        reloadId: Math.random()
    };
}

export const roleInProject = (project: ProjectMember[]): ProjectRole | undefined => {
    const member = project.find(m => {
        return m.username === Client.username;
    });

    if (member === undefined) return undefined;
    return member.role;
};


export interface ToggleProjectFavorite {
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

export type ListOutgoingInvitesRequest = PaginationRequest;
export type ListSubprojectsRequest = PaginationRequestV2;

export function listOutgoingInvites(
    request: ListOutgoingInvitesRequest
): APICallParameters<ListOutgoingInvitesRequest> {
    return {
        method: "GET",
        path: buildQueryString("/projects/invites/outgoing", request),
        parameters: request
    };
}

export interface IngoingInvite {
    project: string;
    title: string;
    invitedBy: string;
    timestamp: string;
}

export type ListIngoingInvitesRequest = PaginationRequest;

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

export interface RenameProjectRequest {
    id: string;
    newTitle: string;
}

export function renameProject(request: RenameProjectRequest): APICallParameters<RenameProjectRequest> {
    return {
        reloadId: Math.random(),
        method: "POST",
        path: "/projects/rename",
        payload: request,
        parameters: request
    };
}


export interface ProjectAffiliationSelectorProps {
    username: string;
    trigger: React.ReactNode;
    visible: boolean;
    onProjectSelect: (project: {project: string} | null) => void;
}

type LeaveProjectRequest = Record<string, never>
export function leaveProject(
    request: LeaveProjectRequest,
    projectOverride?: string
): APICallParameters<LeaveProjectRequest> {
    return {
        method: "DELETE",
        path: "/projects/leave",
        parameters: request,
        payload: request,
        projectOverride
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

export interface ArchiveProjectRequest {
    archiveStatus: boolean;
}

export function setProjectArchiveStatus(
    request: ArchiveProjectRequest,
    projectOverride?: string
): APICallParameters<ArchiveProjectRequest> {
    return {
        method: "POST",
        path: "/projects/archive",
        parameters: request,
        payload: request,
        reloadId: Math.random(),
        projectOverride
    };
}

export interface ArchiveProjectRequestBulk {
    projects: UserInProject[];
}

export function setProjectArchiveStatusBulk(
    request: ArchiveProjectRequestBulk
): APICallParameters<ArchiveProjectRequestBulk> {
    return {
        method: "POST",
        path: "/projects/archiveBulk",
        parameters: request,
        payload: request,
        reloadId: Math.random(),
    };
}

export interface ViewProjectRequest {
    id: string;
}

export interface ViewGroupRequest {
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

export function viewGroup(request: ViewGroupRequest): APICallParameters<ViewGroupRequest> {
    return {
        method: "GET",
        path: buildQueryString("/projects/groups/view", request),
        parameters: request,
        reloadId: Math.random()
    };
}

export interface FetchDataManagementPlanResponse {
    dmp?: string;
}

export function fetchDataManagementPlan(request: unknown): APICallParameters<unknown> {
    return {
        method: "GET",
        path: "/projects/dmp",
        reloadId: Math.random()
    };
}

export interface UpdateDataManagementPlanRequest {
    id: string;
    dmp?: string;
}

export function updateDataManagementPlan(
    request: UpdateDataManagementPlanRequest
): APICallParameters<UpdateDataManagementPlanRequest> {
    return {
        method: "POST",
        path: "/projects/update-dmp",
        parameters: request,
        payload: request,
        reloadId: Math.random()
    };
}

export function useProjectId(): string | undefined {
    return useSelector<ReduxObject, string | undefined>(it => it.project.project);
}

// eslint-disable-next-line
export function useProjectManagementStatus(args: {
    /**
     * isRootComponent controls if this component should pull in new information when the project changes.
     * Rule of thumb: If the component uses a MainContainer then this should be true otherwise it should probably be
     * false.
     */
    isRootComponent: boolean,
    allowPersonalProject?: true
}) {
    const {isRootComponent, allowPersonalProject} = args;
    const history = useHistory();
    const promises = usePromiseKeeper();
    const projectId = useSelector<ReduxObject, string | undefined>(it => it.project.project);
    const locationParams = useParams<{group: string; member?: string}>();
    let groupId = locationParams.group ? decodeURIComponent(locationParams.group) : undefined;
    let membersPage = locationParams.member ? decodeURIComponent(locationParams.member) : undefined;
    if (groupId === '-') groupId = undefined;
    if (membersPage === '-') membersPage = undefined;

    const [projectMembers, setProjectMemberParams, projectMemberParams] = useGlobalCloudAPI<Page<ProjectMember>>(
        "projectManagement",
        membershipSearch({itemsPerPage: 100, page: 0, query: ""}),
        emptyPage
    );

    const [projectDetails, fetchProjectDetails, projectDetailsParams] = useGlobalCloudAPI<UserInProject>(
        "projectManagementDetails",
        {noop: true},
        {
            projectId: projectId ?? "",
            favorite: false,
            needsVerification: false,
            title: "",
            whoami: {username: Client.username ?? "", role: ProjectRole.USER},
            archived: false
        }
    );

    const [groupDetails, fetchGroupDetails, groupDetailsParams] = useGlobalCloudAPI<GroupWithSummary>(
        "projectManagementGroup",
        {noop: true},
        {
            groupId: "NoId",
            groupTitle: "NoTitle",
            numberOfMembers: 0,
            members: [],
        }
    );

    const [groupMembers, fetchGroupMembers, groupMembersParams] = useGlobalCloudAPI<Page<string>>(
        "projectManagementGroupMembers",
        {noop: true},
        emptyPage
    );

    const [groupList, fetchGroupList, groupListParams] = useGlobalCloudAPI<Page<GroupWithSummary>>(
        "projectManagementGroupSummary",
        Client.hasActiveProject ? groupSummaryRequest({itemsPerPage: 10, page: 0}) : {noop: true},
        emptyPage
    );

    const [outgoingInvites, fetchOutgoingInvites, outgoingInvitesParams] = useGlobalCloudAPI<Page<OutgoingInvite>>(
        "projectManagementOutgoingInvites",
        Client.hasActiveProject ? listOutgoingInvites({itemsPerPage: 10, page: 0}) : {noop: true},
        emptyPage
    );

    const [memberSearchQuery, setMemberSearchQuery] = useGlobal("projectManagementQuery", "");
    const [subprojectSearchQuery, setSubprojectSearchQuery] = useGlobal("projectManagementQuery", "");

    if (projectId === undefined && !allowPersonalProject) {
        history.push("/");
    }

    const projects = useProjectStatus();
    const projectRole = projects.fetch().membership
        .find(it => it.projectId === projectId)?.whoami?.role ?? ProjectRole.USER;
    const allowManagement = isAdminOrPI(projectRole);
    const reloadProjectStatus = projects.reload;

    useEffect(() => {
        if (!isRootComponent) return;
        if (promises.canceledKeeper) return;
        if (groupId !== undefined) {
            fetchGroupMembers(listGroupMembersRequest({group: groupId, itemsPerPage: 25, page: 0}));
        } else if (Client.hasActiveProject) {
            fetchGroupList(groupSummaryRequest({itemsPerPage: 10, page: 0}));
        }

        if (groupId) fetchGroupDetails(viewGroup({id: groupId}));
    }, [projectId, groupId, projectRole]);

    useEffect(() => {
        if (!isRootComponent) return;
        if (promises.canceledKeeper) return;

        // noinspection JSIgnoredPromiseFromCall
        reloadProjectStatus();
        if (Client.hasActiveProject) fetchOutgoingInvites(listOutgoingInvites({itemsPerPage: 10, page: 0}));
        if (projectId) fetchProjectDetails(viewProject({id: projectId}));
    }, [projectId, projectRole]);

    const reload = useCallback(() => {
        if (promises.canceledKeeper) return;
        fetchOutgoingInvites(outgoingInvitesParams);
        setProjectMemberParams(projectMemberParams);
        fetchProjectDetails(projectDetailsParams);
        if (groupId !== undefined) {
            fetchGroupMembers(groupMembersParams);
            fetchGroupDetails(groupDetailsParams);
        }
    }, [projectMemberParams, groupMembersParams, setProjectMemberParams, groupId]);

    return {
        locationParams, projectId: projectId ?? "", groupId, groupDetails, fetchGroupDetails, groupDetailsParams,
        projectMembers, setProjectMemberParams, groupMembers, fetchGroupMembers, groupMembersParams, groupList,
        fetchGroupList, groupListParams, projectMemberParams, memberSearchQuery, setMemberSearchQuery, allowManagement,
        reloadProjectStatus, outgoingInvites, outgoingInvitesParams, fetchOutgoingInvites, membersPage, projectRole,
        projectDetails, projectDetailsParams, fetchProjectDetails, subprojectSearchQuery, setSubprojectSearchQuery,
        reload
    };
}

export interface SubAllocation {
    id: string;
    path: string;
    startDate: number;
    endDate?: number | null;

    productCategoryId: ProductCategoryId;
    productType: ProductType;
    chargeType: ChargeType;
    unit: ProductPriceUnit;

    workspaceId: string;
    workspaceTitle: string;
    workspaceIsProject: boolean;
    projectPI?: string;

    remaining: number;
    initialBalance: number;
}

export function browseSubAllocations(request: PaginationRequestV2): APICallParameters {
    return apiBrowse(request, "/api/accounting/wallets", "subAllocation");
}

export function searchSubAllocations(request: { query: string } & PaginationRequestV2): APICallParameters {
    return apiSearch(request, "/api/accounting/wallets", "subAllocation");
}
