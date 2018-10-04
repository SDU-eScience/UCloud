import { match } from "react-router-dom";
import PromiseKeeper from "PromiseKeeper";
import { Page } from "Types";
import { Application } from "Applications";
import { File } from "Files";
import { ProjectMetadata } from "Metadata/api";
import { History } from "history";
import { Dispatch } from "redux";
import { HeaderSearchType } from "DefaultObjects";

export interface SimpleSearchProps extends SimpleSearchOperations, SimpleSearchStateProps {
    match: match<{ 0: string, priority: string }>
    history: History
}

export interface SimpleSearchStateProps {
    files: Page<File>
    filesLoading: boolean
    applications: Page<Application>
    applicationsLoading: boolean
    projects: Page<ProjectMetadata>
    projectsLoading: boolean
    error?: string
    search: string
}

export interface SimpleSearchOperations {
    setFilesLoading: (loading: boolean) => void
    setApplicationsLoading: (loading: boolean) => void
    setProjectsLoading: (loading: boolean) => void
    setError: (error?: string) => void
    searchFiles: (query: string, page: number, itemsPerPage: number) => void
    searchApplications: (query: string, page: number, itemsPerPage: number) => void
    searchProjects: (query: string, page: number, itemsPerPage: number) => void
    setFilesPage: (page: Page<File>) => void
    setApplicationsPage: (page: Page<Application>) => void
    setProjectsPage: (page: Page<ProjectMetadata>) => void
    setSearch: (search: string) => void
    setPrioritizedSearch: (st: HeaderSearchType) => void
}