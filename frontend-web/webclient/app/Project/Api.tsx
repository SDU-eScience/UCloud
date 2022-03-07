import {buildQueryString} from "@/Utilities/URIUtilities";
import {BulkRequest, PaginationRequestV2} from "@/UCloud";
import {apiBrowse, apiRetrieve, apiUpdate} from "@/Authentication/DataHook";

export type ProjectRole = "PI" | "ADMIN" | "USER";

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

export interface ProjectFlags {
    includeMembers?: boolean | null;
    includeGroups?: boolean | null;
    includeFavorite?: boolean | null;
    includeArchived?: boolean | null;
    includeSettings?: boolean | null;
}

class ProjectApi {
    baseContext = "/api/projects/v2";

    public retrieve(request: ProjectFlags & FindById): APICallParameters {
        return apiRetrieve(request, this.baseContext);
    }
}

const api = new ProjectApi();
export {api};
export default api;
