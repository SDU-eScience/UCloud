import {BulkRequest, FindByStringId, PageV2, PaginationRequestV2} from "@/UCloud";
import {apiBrowse, apiCreate, apiRetrieve, apiUpdate, useCloudAPI} from "@/Authentication/DataHook";
import {useSelector} from "react-redux";
import {IconName} from "@/ui-components/Icon";
import {useLocation, useParams} from "react-router";
import {useCallback, useEffect, useState} from "react";
import {getQueryParamOrElse} from "@/Utilities/URIUtilities";
import {OldProjectRole, Project, ProjectGroupSpecification, ProjectRole, ProjectSettings, ProjectSpecification} from ".";

export interface ProjectInvite {
    createdAt: number;
    invitedBy: string;
    invitedTo: string;
    recipient: string;
    projectTitle: string;
}

export interface ProjectInviteLink {
    token: string;
    expires: number;
    groupAssignment: string[];
    roleAssignment: ProjectRole;
}

interface RetrieveInviteLinkInfoRequest {
    token: string;
}

export interface RetrieveInviteLinkInfoResponse {
    token: string;
    project: Project;
    isMember: boolean;
}

interface DeleteInviteLinkRequest {
    token: string;
}

interface UpdateInviteLinkRequest {
    token: string;
    role: string;
    groups: string[];
}

interface AcceptInviteLinkRequest {
    token: string;
}

export interface AcceptInviteLinkResponse {
    project: string;
}

interface RenameProjectRequest {
    id: string;
    newTitle: string;
}

interface VerifyProjectRequest {

}

class ProjectApi {
    baseContext = "/api/projects/v2";

    public retrieve(request: ProjectFlags & FindByStringId): APICallParameters {
        return apiRetrieve(request, this.baseContext);
    }

    public browse(request: ProjectFlags & ProjectsSortByFlags & PaginationRequestV2): APICallParameters<unknown, PageV2<Project>> {
        return apiBrowse(request, this.baseContext);
    }

    public create(request: BulkRequest<ProjectSpecification>): APICallParameters {
        return apiCreate(request, this.baseContext);
    }

    public archive(request: BulkRequest<FindByStringId>): APICallParameters {
        return apiUpdate(request, this.baseContext, "archive");
    }

    public unarchive(request: BulkRequest<FindByStringId>): APICallParameters {
        return apiUpdate(request, this.baseContext, "unarchive");
    }

    public toggleFavorite(request: BulkRequest<FindByStringId>): APICallParameters {
        return apiUpdate(request, this.baseContext, "toggleFavorite");
    }

    // TODO(Jonas): Test
    public renameProject(request: BulkRequest<RenameProjectRequest>): APICallParameters {
        return apiUpdate(request, this.baseContext, "renameProject");
    }

    // TODO(Jonas): Handle in general.
    public verifyProject(request: BulkRequest<VerifyProjectRequest>): APICallParameters {
        return apiUpdate(request, this.baseContext, "verifyProject");
    }

    public updateSettings(request: ProjectSettings): APICallParameters {
        return apiUpdate(request, this.baseContext, "updateSettings");
    }

    public verifyMembership(request: BulkRequest<FindByStringId>): APICallParameters {
        return apiUpdate(request, this.baseContext, "verifyMembership");
    }

    public browseInvites(request: ProjectInviteFlags & PaginationRequestV2): APICallParameters {
        return apiBrowse(request, this.baseContext, "invites");
    }

    public createInvite(request: BulkRequest<{recipient: string}>): APICallParameters {
        return apiCreate(request, this.baseContext, "invites");
    }

    public acceptInvite(request: BulkRequest<FindByProjectId>): APICallParameters {
        return apiUpdate(request, this.baseContext, "acceptInvite");
    }

    public deleteInvite(request: BulkRequest<{username: string, project: string}>): APICallParameters {
        return apiUpdate(request, this.baseContext, "deleteInvite");
    }

    public createInviteLink(): APICallParameters<unknown, ProjectInviteLink> {
        return apiCreate(undefined, this.baseContext, "link");
    }

    public browseInviteLinks(request: PaginationRequestV2): APICallParameters {
        return apiBrowse(request, this.baseContext, "link");
    }

    public retrieveInviteLinkInfo(request: RetrieveInviteLinkInfoRequest): APICallParameters {
        return apiRetrieve(request, this.baseContext, "link");
    }

    public deleteInviteLink(request: DeleteInviteLinkRequest): APICallParameters {
        return apiUpdate(request, this.baseContext, "deleteInviteLink");
    }

    public updateInviteLink(request: UpdateInviteLinkRequest): APICallParameters {
        return apiUpdate(request, this.baseContext, "updateInviteLink");
    }

    public acceptInviteLink(request: AcceptInviteLinkRequest): APICallParameters {
        return apiUpdate(request, this.baseContext, "acceptInviteLink")
    }

    public deleteMember(request: BulkRequest<{username: string}>): APICallParameters {
        return apiUpdate(request, this.baseContext, "deleteMember");
    }

    public changeRole(request: BulkRequest<{username: string, role: ProjectRole}>): APICallParameters {
        return apiUpdate(request, this.baseContext, "changeRole");
    }

    public createGroup(request: BulkRequest<ProjectGroupSpecification>): APICallParameters {
        return apiUpdate(request, this.baseContext, "groups");
    }

    public renameGroup(request: BulkRequest<{group: string, newTitle: string}>): APICallParameters {
        return apiUpdate(request, this.baseContext, "renameGroup");
    }

    public deleteGroup(request: BulkRequest<FindByStringId>): APICallParameters {
        return apiUpdate(request, this.baseContext, "deleteGroup");
    }

    public createGroupMember(request: BulkRequest<{group: string, username: string}>): APICallParameters {
        return apiCreate(request, this.baseContext, "groupMembers");
    }

    public deleteGroupMember(request: BulkRequest<{group: string, username: string}>): APICallParameters {
        return apiUpdate(request, this.baseContext, "deleteGroupMember");
    }
}

export function useGroupIdAndMemberId(): [groupId?: string, memberId?: string] {
    const locationParams = useParams<{group: string; member?: string}>();
    let groupId = locationParams.group ? decodeURIComponent(locationParams.group) : undefined;
    let membersPage = locationParams.member ? decodeURIComponent(locationParams.member) : undefined;
    if (groupId === '-') groupId = undefined;
    if (membersPage === '-') membersPage = undefined;

    const [localGroupId, setLocalGroupId] = useState<string | undefined>(undefined);
    const [localMemberId, setLocalMemberId] = useState<string | undefined>(undefined);

    useEffect(() => {
        setLocalGroupId(groupId);
        setLocalMemberId(membersPage);
    }, [groupId, membersPage]);

    return [localGroupId, localMemberId];
}

export interface FindByProjectId {
    project: string;
}

export interface ProjectFlags {
    includeMembers?: boolean | null;
    includeGroups?: boolean | null;
    includeFavorite?: boolean | null;
    includePath?: boolean | null;
    includeArchived?: boolean | null;
    includeSettings?: boolean | null;
}

export interface ProjectsSortByFlags {
    sortBy?: "favorite" | "title" | "parent" | null;
    sortDirection?: "ascending" | "descending" | null;
}

export interface ProjectInviteFlags {
    filterType?: "INGOING" | "OUTGOING" | null;
}

export function useProjectId(): string | undefined {
    return useSelector<ReduxObject, string | undefined>(it => it.project.project);
}

export function projectRoleToString(role: ProjectRole): string {
    switch (role) {
        case OldProjectRole.PI: return "PI";
        case OldProjectRole.ADMIN: return "Admin";
        case OldProjectRole.USER: return "User";
    }
}

export function projectStringToRole(role: string): ProjectRole {
    switch (role) {
        case "PI": return OldProjectRole.PI;
        case "ADMIN": return OldProjectRole.ADMIN;
        case "USER": return OldProjectRole.USER;
        default: {
            console.log("Unhandled role in projectStringToRole")
            console.log(role);
            return OldProjectRole.USER;
        }
    }

}

export function projectRoleToStringIcon(role: ProjectRole): IconName {
    switch (role) {
        case OldProjectRole.PI: return "userPi";
        case OldProjectRole.ADMIN: return "userAdmin";
        case OldProjectRole.USER: return "user";
        default: {
            console.log(role);
            return "bug";
        }
    }
}

export function useSubprojectFromURL(request: ProjectFlags): {project: Project; reload: () => void; projectId: string} {
    const location = useLocation();
    const subprojectFromQuery = getQueryParamOrElse(location.search, "subproject", "");

    const [project, fetchProject] = useCloudAPI<Project>(
        {noop: true},
        emptyProject()
    );

    useEffect(() => {
        if (subprojectFromQuery) {
            fetchProject(api.retrieve({
                id: subprojectFromQuery,
                ...request
            }));
        }
    }, [subprojectFromQuery])

    const reload = useCallback(() => {
        fetchProject(api.retrieve({
            id: subprojectFromQuery,
            ...request
        }));
    }, [request])

    return {project: project.data, projectId: subprojectFromQuery, reload};
}

export function emptyProject(): Project {
    return {
        id: "",
        createdAt: new Date().getTime(),
        specification: {
            title: "",
            canConsumeResources: true
        },
        status: {
            archived: false,
            needsVerification: false,
        }
    }
}

const api = new ProjectApi();
export {api};
export default api;
