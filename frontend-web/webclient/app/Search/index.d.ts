import {
    DetailedApplicationSearchReduxState,
    FullAppInfo
} from "Applications";
import { HeaderSearchType } from "DefaultObjects";
import { AdvancedSearchRequest, DetailedFileSearchReduxState, File } from "Files";
import { History } from "history";
import { match } from "react-router-dom";
import { Page } from "Types";
import { RouterLocationProps } from "Utilities/URIUtilities";

export interface SearchProps extends SimpleSearchOperations, SimpleSearchStateProps, RouterLocationProps {
    match: match<{ priority: string }>;
    history: History;
}

export interface SimpleSearchStateProps {
    files: Page<File>;
    filesLoading: boolean;
    applications: Page<FullAppInfo>;
    applicationsLoading: boolean;
    errors: string[];
    search: string;
    fileSearch: DetailedFileSearchReduxState;
    applicationSearch: DetailedApplicationSearchReduxState;
}

export interface SimpleSearchOperations {
    clear: () => void;
    setFilesLoading: (loading: boolean) => void;
    setApplicationsLoading: (loading: boolean) => void;
    searchFiles: (body: AdvancedSearchRequest) => void;
    searchApplications: (query: string, page: number, itemsPerPage: number) => void;
    setFilesPage: (page: Page<File>) => void;
    setApplicationsPage: (page: Page<FullAppInfo>) => void;
    setSearch: (search: string) => void;
    setPrioritizedSearch: (st: HeaderSearchType) => void;
    toggleAdvancedSearch: () => void;
    setActivePage: () => void;
    setRefresh: (refresh?: () => void) => void;
}
