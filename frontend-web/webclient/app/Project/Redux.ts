import {Project} from "Project/index";

export interface State {
    project?: string;
}

export const initialState = {};

export const reducer = (state: State = initialState, newState: { type: string, project?: string }) => {
    if (newState.type === "SET_PROJECT") {
        return { project: newState.project };
    }

    return state;
};
