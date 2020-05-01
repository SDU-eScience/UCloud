import {File} from "Files/index";
import {AccessRight} from "Types";
import {useProjectStatus} from "Project/cache";
import {useCallback} from "react";
import {Client} from "Authentication/HttpClientInstance";

export function useFilePermissions(): FilePermissions {
    const projects = useProjectStatus();
    const hasPermission = useCallback((file: File, right: AccessRight) => {
        if (file.ownerName === Client.username) {
            return true;
        }

        const acl = file.acl ?? [];

        // Check full ACL for usernames (project ACL checks require network)
        let hasProjectAcl = false;
        for (const entry of acl) {
            if (entry.rights.some(it => it === right)) {
                if (typeof entry.entity === "string") {
                    if (entry.entity === Client.username) return true;
                } else if (typeof entry.entity === "object" && "username" in entry.entity) {
                    if (entry.entity.username === entry.entity.username) return true;
                } else if (typeof entry.entity === "object" && "projectId" in entry.entity) {
                    hasProjectAcl = true;
                }
            }
        }

        if (!hasProjectAcl) return false;
        const projectStatus = projects.fetch();

        for (const entry of acl) {
            if (entry.rights.some(it => it === right)) {
                if (typeof entry.entity === "object" && "projectId" in entry.entity) {
                    const {group, projectId} = entry.entity;
                    if (projectStatus.groups.some(it => it.group === group && it.projectId === projectId)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }, [projects]);

    const allFilesHavePermission = useCallback((files: File[], right: AccessRight) => {
        for (const file of files) {
            if (!hasPermission(file, right)) return false;
        }
        return true;
    }, [hasPermission]);

    return { require: hasPermission, requireForAll: allFilesHavePermission };
}

export interface FilePermissions {
    require: (file: File, right: AccessRight) => boolean;
    requireForAll: (files: File[], right: AccessRight) => boolean;
}