import {DetailedApplicationSearchReduxState, FullAppInfo} from "Applications";
import {HeaderSearchType} from "DefaultObjects";
import {AdvancedSearchRequest, DetailedFileSearchReduxState, File} from "Files";
import {AdvancedSearchRequest as AppSearchRequest} from "Applications";
import {History} from "history";
import {match} from "react-router-dom";

export interface SearchProps extends SimpleSearchOperations, SimpleSearchStateProps {
    match: match<{priority: string}>;
    /* If extends interface with these, Jest complains */
    history: History;
    location: {
        // TODO There is more here
        search: string
    };
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
    searchApplications: (body: AppSearchRequest) => void;
    setFilesPage: (page: Page<File>) => void;
    setApplicationsPage: (page: Page<FullAppInfo>) => void;
    setSearch: (search: string) => void;
    setPrioritizedSearch: (st: HeaderSearchType) => void;
    toggleAdvancedSearch: () => void;
    setActivePage: () => void;
    setRefresh: (refresh?: () => void) => void;
}