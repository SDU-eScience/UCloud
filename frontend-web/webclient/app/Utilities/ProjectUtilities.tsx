import SDUCloud from "Authentication/lib";

export const projectViewPage = (filePath: string): string => {
    return `/projects/view?filePath=${encodeURIComponent(filePath)}`;
}

export const projectEditPage = (filePath: string): string => {
    return `/projects/edit?filePath=${encodeURIComponent(filePath)}`;
}


/* Add members to a project
`POST /api/projects/members` */

export const addProjectMember = "/projects/members";

/* Remove members from a project
`DELETE /api/projects/members` */


export const deleteProjectMember = (projectId: string, username: string, cloud: SDUCloud, callback: () => void) =>
    cloud.delete(deleteProjectMemberQuery, { projectId, username })
         .then()
         .catch()


export const deleteProjectMemberQuery = "/projects/members";

/* Change the role of members in a project
`POST /api/projects/members/change-role` */

export const changeProjectMemberrole = "/projects/members/change-role";

/* View the members of a project (grouped by their role)
`GET /api/projects?id=$PROJECTID` */

export const viewProjectMembers = (id: string | number) => `/projects?id=${id}`;