import {ProjectName, ProjectRole} from "@/Project";
import {ProjectStatus} from "@/Project/cache";

/**
 * Extracts title and projectId from project status.
 * Intended usage:
 *  ```
 *  const project = useProjectStatus();
 *  const projectNames = getProjectNames(project);
 *  ```
 */
export function getProjectNames(project: ProjectStatus): ProjectName[] {
    return project.fetch().membership.map(it => ({title: it.title, projectId: it.projectId}));
}

export function isAdminOrPI(role: ProjectRole): boolean {
    return [ProjectRole.ADMIN, ProjectRole.PI].includes(role);
}
