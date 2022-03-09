import {BulkRequest, FindByStringId, PaginationRequestV2} from "@/UCloud";
import {apiBrowse, apiCreate, apiRetrieve, apiUpdate} from "@/Authentication/DataHook";
import {ProjectRole as OldProjectRole} from "@/Project";

export type ProjectRole = OldProjectRole;

export interface ProjectMember {
    username: string;
    role: ProjectRole;
}

export interface Project {
    id: string;
    createdAt: number;
    specification: ProjectSpecification;
    status: ProjectStatus;
}

export interface ProjectStatus {
    archived: boolean;
    isFavorite?: boolean | null;
    members?: ProjectMember[] | null;
    groups?: ProjectGroup[] | null;
    settings?: ProjectSettings | null;
    myRole?: ProjectRole | null;
}

export interface ProjectSpecification {
    parent?: string | null;
    title: string;
}

export interface ProjectSettings {
    subprojects?: ProjectSettingsSubProjects | null;
}

export interface ProjectSettingsSubProjects {
    allowRenaming: boolean;
}

export interface ProjectGroup {
    id: string;
    createdAt: number;
    specification: ProjectGroupSpecification;
    status: ProjectGroupStatus;
}

export interface ProjectGroupSpecification {
    project: string;
    title: string;
}

export interface ProjectGroupStatus {
    members?: string[] | null;
}

export interface ProjectInvite {
    createdAt: number;
    invitedBy: string;
    invitedTo: string;
    recipient: string;
}

class ProjectApi {
    baseContext = "/api/projects/v2";

    public retrieve(request: ProjectFlags & FindByStringId): APICallParameters {
        return apiRetrieve(request, this.baseContext);
    }

    public browse(request: ProjectFlags & PaginationRequestV2): APICallParameters {
        return apiBrowse(request, this.baseContext);
    }

    public create(request: ProjectSpecification): APICallParameters {
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

    public deleteInvite(request: BulkRequest<FindByProjectId>): APICallParameters {
        return apiUpdate(request, this.baseContext, "deleteInvite");
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

    public createGroupMember(request: BulkRequest<{group: string, username: string}>): APICallParameters {
        return apiCreate(request, this.baseContext, "groupMembers");
    }

    public deleteGroupMember(request: BulkRequest<{group: string, username: string}>): APICallParameters {
        return apiUpdate(request, this.baseContext, "deleteGroupMember");
    }
}

export interface FindByProjectId {
    project: string;
}

export interface ProjectFlags {
    includeMembers?: boolean | null;
    includeGroups?: boolean | null;
    includeFavorite?: boolean | null;
    includeArchived?: boolean | null;
    includeSettings?: boolean | null;
}

export interface ProjectInviteFlags {
    filterType?: "INGOING" | "OUTGOING" | null;
}

const api = new ProjectApi();
export {api};
export default api;
