/**
 * Cache for project and group membership.
 */
import {useGlobal} from "@/Utilities/ReduxHooks";
import {useCallback, useEffect} from "react";
import {useCloudCommand} from "@/Authentication/DataHook";
import ProjectAPI, {Project, useProjectId} from "./Api";
import {Client} from "@/Authentication/HttpClientInstance";
import {getStoredProject} from "./Redux";

// This needs to be global
let cacheIsLoading = false;

export function emptyProject(): Project {
    return {
        id: "",
        createdAt: new Date().getTime(),
        specification: {
            title: ""
        },
        status: {
            archived: false,
            needsVerification: false,
        }
    }
}

export function useProject(): {fetch(): Project; reload(): void; loading: boolean;} {
    const [cache, setCache] = useGlobal(
        "projectCache",
        {
            expiresAt: 0,
            project: emptyProject()
        }
    );

    const projectId = useProjectId();
    const [loading, invokeCommand] = useCloudCommand();

    const reload = useCallback(async () => {
        if (cacheIsLoading) return;
        const projectId = getStoredProject()
        if (!projectId) return;
        cacheIsLoading = true;
        const project = await invokeCommand<Project>(ProjectAPI.retrieve({
            id: getStoredProject() ?? "",
            includeGroups: true,
            includeFavorite: true,
            includeArchived: true,
            includeMembers: true,
            includePath: true,
            includeSettings: true
        }));
        if (project !== null) {
            setCache({expiresAt: +(new Date()) + cacheMaxAge, project});
        } else {
            setCache({...cache, expiresAt: +(new Date()) + (1000 * 30)});
        }

        cacheIsLoading = false;
    }, [invokeCommand, cache, setCache]);

    const fetch = useCallback(() => {
        const now = (+new Date());
        if (now > cache.expiresAt) {
            reload();
        }

        return cache.project;
    }, [cache]);

    useEffect(() => {
        if (projectId !== cache.project.id || projectId == null) {
             reload();
        }
    }, [projectId, cache.project.id])

    return {fetch, reload, loading};
}

export interface ProjectCache {
    expiresAt: number;
    project: Project;
}

const cacheMaxAge = 1000 * 60 * 3;
