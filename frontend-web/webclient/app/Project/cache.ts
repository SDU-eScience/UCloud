/**
 * Cache for project and group membership.
 */
import {useGlobal} from "@/Utilities/ReduxHooks";
import {useCallback, useEffect, useState} from "react";
import {useCloudCommand} from "@/Authentication/DataHook";
import ProjectAPI, {Project, useProjectId} from "./Api";
import {Client} from "@/Authentication/HttpClientInstance";
import {dispatchSetProjectAction, getStoredProject, setStoredProject} from "./Redux";
import {displayErrorMessageOrDefault, errorMessageOrDefault} from "@/UtilityFunctions";
import { useDispatch } from "react-redux";

// This needs to be global
let cacheIsLoading = false;

export function emptyProject(): Project {
    return {
        id: "",
        createdAt: new Date().getTime(),
        specification: {
            title: "",
            canConsumeResources: true
        },
        status: {
            archived: false,
            needsVerification: false,
        }
    }
}

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

export interface ProjectCache {
    expiresAt: number;
    project: Project;
}

const cacheMaxAge = 1000 * 60 * 3;
