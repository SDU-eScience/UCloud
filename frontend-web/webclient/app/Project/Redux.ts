import {Action, Dispatch} from "redux";

export interface State {
    project?: string;
}

export const initialState = {project: getStoredProject() ?? undefined};

interface SetProjectAction extends Action<"SET_PROJECT"> {
    project?: string;
}

export function dispatchSetProjectAction(dispatch: Dispatch, project?: string): void {
    dispatch<ProjectAction>({project, type: "SET_PROJECT"});
}

type ProjectAction = SetProjectAction;

export const reducer = (state: State = initialState, action: ProjectAction): State => {
    switch (action.type) {
        case "SET_PROJECT": {
            setStoredProject(action.project ?? null);
            return {...state, project: action.project};
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

export function emitProjects(project: string | null) {
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

