import { match } from "react-router-dom";
import PromiseKeeper from "PromiseKeeper";
import { Page } from "Types";
import { Application, DetailedApplicationSearchReduxState } from "Applications";
import { File, DetailedFileSearchReduxState, AdvancedSearchRequest } from "Files";
import { ProjectMetadata } from "Project/api";
import { History } from "history";
import { Dispatch } from "redux";
import { HeaderSearchType } from "DefaultObjects";
import { RouterLocationProps } from "Utilities/URIUtilities";

export interface SearchProps extends SimpleSearchOperations, SimpleSearchStateProps, RouterLocationProps {
    match: match<{ priority: string }>
    history: History
}

export interface SimpleSearchStateProps {
    files: Page<File>
    filesLoading: boolean
    applications: Page<Application>
    applicationsLoading: boolean
    projects: Page<ProjectMetadata>
    projectsLoading: boolean
    errors: string[]
    search: string
    fileSearch: DetailedFileSearchReduxState
    applicationSearch: DetailedApplicationSearchReduxState
}

export interface SimpleSearchOperations {
    setFilesLoading: (loading: boolean) => void
    setApplicationsLoading: (loading: boolean) => void
    setProjectsLoading: (loading: boolean) => void
    setError: (error?: string) => void
    searchFiles: (body: AdvancedSearchRequest) => void
    searchApplications: (query: string, page: number, itemsPerPage: number) => void
    searchProjects: (query: string, page: number, itemsPerPage: number) => void
    setFilesPage: (page: Page<File>) => void
    setApplicationsPage: (page: Page<Application>) => void
    setProjectsPage: (page: Page<ProjectMetadata>) => void
    setSearch: (search: string) => void
    setPrioritizedSearch: (st: HeaderSearchType) => void
    toggleAdvancedSearch: () => void
}