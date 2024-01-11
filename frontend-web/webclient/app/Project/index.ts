export interface ProjectCache {
    expiresAt: number;
    project: Project;
}
export enum OldProjectRole {
    PI = "PI",
    ADMIN = "ADMIN",
    USER = "USER",
}

export function isAdminOrPI(role?: ProjectRole | null): boolean {
    if (!role) return false;
    return [OldProjectRole.PI, OldProjectRole.ADMIN].includes(role);
}

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
    path?: string | null;
    needsVerification: boolean;
}

export interface ProjectSpecification {
    parent?: string | null;
    title: string;
    canConsumeResources?: boolean;
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