import { Error, PayloadAction } from "Types";
import {
    DETAILED_PROJECT_SEARCH_SET_ERROR,
    DETAILED_PROJECT_SET_NAME
} from "./ProjectSearchReducer";


type DetailedProjectError = Error <typeof DETAILED_PROJECT_SEARCH_SET_ERROR >
export const setError = (error?: string): DetailedProjectError => ({
    type: DETAILED_PROJECT_SEARCH_SET_ERROR,
    payload: { error }
});

type DetailedProjectSetName = PayloadAction<typeof DETAILED_PROJECT_SET_NAME, { projectName: string }>
export const setProjectName = (projectName: string): DetailedProjectSetName => ({
    type: DETAILED_PROJECT_SET_NAME,
    payload: { projectName }
});