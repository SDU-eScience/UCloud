/**
 * Cache for project and group membership.
 */
import {useGlobal} from "@/Utilities/ReduxHooks";
import {useCallback, useEffect, useState} from "react";
import {useCloudCommand} from "@/Authentication/DataHook";
import ProjectAPI, {useProjectId, emptyProject} from "./Api";
import {dispatchSetProjectAction, getStoredProject, setStoredProject} from "./ReduxState";
import {useDispatch} from "react-redux";
import {Project} from ".";

// This needs to be global
let cacheIsLoading = false;


export function useProject(): {fetch(): Project; reload(): void; loading: boolean; error: string;} {
    const [cache, setCache] = useGlobal(
        "projectCache",
        {
            expiresAt: 0,
            project: emptyProject()
        }
    );

    const [error, setError] = useState("");

    const dispatch = useDispatch();
    const projectId = useProjectId();
    const [loading, invokeCommand] = useCloudCommand();

    const reload = useCallback(async () => {
        if (cacheIsLoading) return;
        const projectId = getStoredProject()
        if (!projectId) return;
        try {
            cacheIsLoading = true;
            const project = await invokeCommand<Project>(ProjectAPI.retrieve({
                id: projectId,
                includeGroups: true,
                includeFavorite: true,
                includeArchived: true,
                includeMembers: true,
                includePath: true,
                includeSettings: true
            }), {defaultErrorHandler: false});
            if (project !== null) {
                setCache({expiresAt: +(new Date()) + cacheMaxAge, project});
            } else {
                setStoredProject(null);
                dispatchSetProjectAction(dispatch);
            }
            setError("");
        } catch (e) {
            setStoredProject(null);
            dispatchSetProjectAction(dispatch);
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

    return {fetch, reload, loading, error};
}

const cacheMaxAge = 1000 * 60 * 3;
