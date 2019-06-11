import {APICallParameters} from "Authentication/DataHook";
import {buildQueryString} from "Utilities/URIUtilities";
import {Cloud} from "Authentication/SDUCloudObject";

export interface ProjectMember {
    username: string,
    role: ProjectRole
}

export interface Project {
    id: string,
    title: string,
    members: ProjectMember[]
}

export const emptyProject = (id: string): Project => ({
    id,
    title: "",
    members: []
});

export enum ProjectRole {
    PI = "PI",
    ADMIN = "ADMIN",
    DATA_STEWARD = "DATA_STEWARD",
    USER = "USER"
}

export interface UserInProject {
    id: string,
    title: string,
    whoami: ProjectMember
}

// TODO This is a service only API. We need a gateway API which is responsible for also creating a data management plan
export const createProject = (payload: { title: string, principalInvestigator: string }): APICallParameters => ({
    method: "POST",
    path: "/projects",
    payload: payload,
    reloadId: Math.random(),
    disallowProjects: true
});

export const viewProject = (payload: { id: string }): APICallParameters => ({
    method: "GET",
    path: buildQueryString("/projects", payload),
    parameters: {
        id: payload.id
    },
    reloadId: Math.random(),
    disallowProjects: true
});

export const addMemberInProject = (payload: { projectId: string, member: ProjectMember }): APICallParameters => ({
    method: "POST",
    path: "/projects/members",
    payload,
    reloadId: Math.random(),
    disallowProjects: true
});

export const deleteMemberInProject = (payload: { projectId: string, member: string }): APICallParameters => ({
    method: "DELETE",
    path: "/projects/members",
    payload,
    reloadId: Math.random(),
    disallowProjects: true
});

export const changeRoleInProject = (payload: { projectId: string, member: string, newRole: ProjectRole }): APICallParameters => ({
    method: "POST",
    path: "/projects/members/change-role",
    payload,
    reloadId: Math.random(),
    disallowProjects: true
});

export interface ListProjectsRequest {
    page: number,
    itemsPerPage: number
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

export const roleInProject = (project: Project): ProjectRole | undefined => {
    const member = project.members.find(member => {
        return member.username == Cloud.username;
    });

    if (member === undefined) return undefined;
    return member.role;
};
