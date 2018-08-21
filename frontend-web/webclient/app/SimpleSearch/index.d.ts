import { match } from "react-router-dom";
import PromiseKeeper from "PromiseKeeper";
import { Page } from "Types";
import { Application } from "Applications";
import { File } from "Files";
import { ProjectMetadata } from "Metadata/api";
import { History } from "history";
import { Dispatch } from "redux";

export interface SimpleSearchProps {
    match: match<{ 0: string, priority: string }>
    dispatch: Dispatch
    history: History
}

export interface SimpleSearchState {
    promises: PromiseKeeper
    files: Page<File>
    filesLoading: boolean
    applications: Page<Application>
    applicationsLoading: boolean
    projects: Page<ProjectMetadata>
    projectsLoading: boolean
    error: string
    search: string
}