import { match } from "react-router-dom";
import PromiseKeeper from "PromiseKeeper";
import { Page } from "Types";
import { Application, DetailedApplicationSearchReduxState, ApplicationMetadata, WithAppFavorite, WithAppMetadata } from "Applications";
import { File, DetailedFileSearchReduxState, AdvancedSearchRequest } from "Files";
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
    applications: Page<WithAppMetadata & WithAppFavorite>
    applicationsLoading: boolean
    errors: string[]
    search: string
    fileSearch: DetailedFileSearchReduxState
    applicationSearch: DetailedApplicationSearchReduxState
}

export interface SimpleSearchOperations {
    clear: () => void
    setFilesLoading: (loading: boolean) => void
    setApplicationsLoading: (loading: boolean) => void
    setError: (error?: string) => void
    searchFiles: (body: AdvancedSearchRequest) => void
    searchApplications: (query: string, page: number, itemsPerPage: number) => void
    setFilesPage: (page: Page<File>) => void
    setApplicationsPage: (page: Page<WithAppMetadata & WithAppFavorite>) => void
    setSearch: (search: string) => void
    setPrioritizedSearch: (st: HeaderSearchType) => void
    toggleAdvancedSearch: () => void
    setActivePage: () => void
    setRefresh: (refresh?: () => void) => void
}