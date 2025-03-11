import {PayloadAction} from "@reduxjs/toolkit";
import {Dispatch} from "redux";

export interface State {
    project?: string;
}

export const initialState = {project: getStoredProject() ?? undefined};

type SetProjectAction = PayloadAction<{project?: string}, "SET_PROJECT">

export function dispatchSetProjectAction(dispatch: Dispatch, project?: string): void {
    dispatch<ProjectAction>({payload: {project}, type: "SET_PROJECT"});
}

type ProjectAction = SetProjectAction;

export const reducer = (state: State = initialState, action: ProjectAction): State => {
    switch (action.type) {
        case "SET_PROJECT": {
            setStoredProject(action.payload.project ?? null);
            return {...state, project: action.payload.project};
        }

        default:
            return state;
    }
};

export function getStoredProject(): string | null {
    return window.localStorage.getItem("project") ?? null;
}

const projectListeners: Record<string, (project: string | null) => void> = {};

export function setStoredProject(value: string | null): void {
    if (value === null) {
        window.localStorage.removeItem("project");
    } else {
        window.localStorage.setItem("project", value);
    }
    emitProjects(value);
}

let lastProject: string | null = getStoredProject();

export function emitProjects(project: string | null) {
    if (project === lastProject) return;
    lastProject = project;
    for (const listener of Object.values(projectListeners)) {
        listener(project);
    }
}

export function removeProjectListener(key: string) {
    delete projectListeners[key];
}

export function addProjectListener(key: string, op: (project: string | null) => void) {
    projectListeners[key] = op;
}

