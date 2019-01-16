import SDUCloud from "Authentication/lib";
import { ProjectRole } from "Project/Management";

export const projectViewPage = (filePath: string): string =>
    `/projects/view?filePath=${encodeURIComponent(filePath)}`;

export const projectEditPage = (filePath: string): string =>
    `/projects/edit?filePath=${encodeURIComponent(filePath)}`;

const addProjectMemberQuery = "/projects/members";
export const addProjectMember = (projectId: string, member: string, cloud: SDUCloud) =>
    cloud.post(addProjectMemberQuery, { projectId, member })

const deleteProjectMemberQuery = "/projects/members";
export const deleteProjectMember = (projectId: string, username: string, cloud: SDUCloud, callback: () => void) =>
    cloud.delete(deleteProjectMemberQuery, { projectId, username })

const changeProjectMemberRoleQuery = "/projects/members/change-role";
export const changeProjectMemberRole = (projectId: string, member: string, newRole: ProjectRole, cloud: SDUCloud) =>
    cloud.post(changeProjectMemberRoleQuery, { projectId, member, newRole })

export const viewProjectMembers = (id: string | number) => `/projects?id=${id}`;