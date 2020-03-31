import {File} from "Files";
import {Action, Dispatch} from "redux";
import {Dictionary, Page} from "Types";
import {callAPI} from "Authentication/DataHook";
import {shouldVerifyMembership, ShouldVerifyMembershipResponse} from "Project/api";

export interface State {
    project?: string;
    files?: Dictionary<ProjectFilesCacheEntry>;
    shouldVerify: boolean;
}

export interface ProjectFilesCacheEntry {
    project: string;
    path: string;
    files: Page<File>;
}

export const initialState = {project: getStoredProject() ?? undefined, shouldVerify: false};

interface SetProjectAction extends Action<"SET_PROJECT"> {
    project?: string;
}

interface SetShouldVerifyAction extends Action<"SHOULD_VERIFY_PROJECT"> {
    project: string;
    shouldVerify: boolean;
}

export function dispatchSetProjectAction(dispatch: Dispatch, project?: string) {
    console.log("Dispatching");
    dispatch<ProjectAction>({project, type: "SET_PROJECT"});
    console.log("Dispatch complete");

    if (project !== undefined) {
        callAPI<ShouldVerifyMembershipResponse>(shouldVerifyMembership())
            .then(resp => {
                const shouldVerify = !resp.shouldVerify;
                dispatch<ProjectAction>({type: "SHOULD_VERIFY_PROJECT", project, shouldVerify});
            })
            .catch(resp => {
                // Do nothing
            });
    }
}

type ProjectAction = SetProjectAction | SetShouldVerifyAction;

export const reducer = (state: State = initialState, action: ProjectAction) => {
    switch (action.type) {
        case "SET_PROJECT": {
            console.log("Set project");
            setStoredProject(action.project ?? null);
            return {...state, project: action.project, shouldVerify: false};
        }

        case "SHOULD_VERIFY_PROJECT":
            console.log(state, action);
            if (state.project === action.project) {
                return {...state, shouldVerify: action.shouldVerify};
            }
            return state;

        default:
            return state;
    }
};

export function getStoredProject(): string | null {
    return window.localStorage.getItem("project") ?? null;
}

function setStoredProject(value: string | null) {
    if (value === null) {
        window.localStorage.removeItem("project");
    } else {
        window.localStorage.setItem("project", value);
    }
}

