import {createSlice, PayloadAction} from "@reduxjs/toolkit";
import {useDispatch} from "react-redux";

export interface State {
    project?: string;
}

export const initialState: State = {project: getStoredProject() ?? undefined};

export function dispatchSetProjectAction(dispatch: ReturnType<typeof useDispatch>, project?: string): void {
    dispatch(setProject(project));
}

const projectSlice = createSlice({
    name: "project",
    initialState,
    reducers: {
        setProject(state, action: PayloadAction<string | undefined>) {
            setStoredProject(action.payload ?? null);
            state.project = action.payload;
        }
    }
});

export const {setProject} = projectSlice.actions;
export const reducer = projectSlice.reducer;

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

