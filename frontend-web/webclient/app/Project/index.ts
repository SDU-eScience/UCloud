import {apiRetrieve, apiUpdate} from "@/Authentication/DataHook";
import {RequestSettings} from "@/Grants";

export interface ProjectCache {
    expiresAt: number;
    project: Project;
}

export enum OldProjectRole {
    PI = "PI",
    ADMIN = "ADMIN",
    DATAMANAGER = "DATA_MANAGER",
    USER = "USER",
}

export function isAdminOrPI(role?: ProjectRole | null): boolean {
    if (!role) return false;
    return [OldProjectRole.PI, OldProjectRole.ADMIN].includes(role);
}

export function isDataSteward(role?: ProjectRole | null): boolean {
    if (!role) return false;
    return OldProjectRole.DATAMANAGER == role;
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
    isHidden?: boolean | null;
    members?: ProjectMember[] | null;
    groups?: ProjectGroup[] | null;
    settings?: ProjectSettings | null;
    myRole?: ProjectRole | null;
    path?: string | null;
    needsVerification: boolean;
    personalProviderProjectFor?: string | null;
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

export interface PolicyProperty {
    name: string;
    type: string;
    title: string;
    description: string;
    options: string[];
}

export interface PolicySchema {
    name: string;
    configuration: PolicyProperty[];
    title: string;
    description: string;
}

export enum PolicyPropertyType {
    ENUM = 'Enum',
    TEXT = 'Text',
    SUBNET = 'Subnet',
    INTEGER = 'Integer',
    FLOAT = 'Float',
    PROVIDERS = 'Providers',
    BOOLEAN = 'Bool',
    TEXTLIST = 'TextList',
    ENUMSET = 'EnumSet',
}

export interface PolicesForProject {
    projectId: string;
    PolicesByName: Map<string, PolicySpecification>;
}

export interface PolicySpecification {
    schema: string;
    project: string;
    properties: PolicyPropertyValue[]
}

export interface PolicyPropertyValue {
    name: string;
    text: string | null;
    providers: string[] | null;
    int: number | null;
    float: number | null;
    bool: boolean | null;
    textElements: string[] | null;
}

export interface Policy {
    schema: PolicySchema;
    specification: PolicySpecification;
}

const baseContext = "/api/projects/v2/policies";

export interface PoliciesUpdateRequest {
    updatedPolicies: Map<string, PolicySpecification>
}

export function updatePolicyRequest(
    request: PoliciesUpdateRequest,
): APICallParameters<unknown, {}> {
    return apiUpdate(request, baseContext, "");
}

export function retrieveRequestPolicies(): APICallParameters<unknown, Map<string, Policy>> {
    return apiRetrieve({}, baseContext, "");
}
