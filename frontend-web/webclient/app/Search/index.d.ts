import {HeaderSearchType} from "DefaultObjects";
import {AdvancedSearchRequest, DetailedFileSearchReduxState, File} from "Files";

export type SearchProps = SimpleSearchOperations & SimpleSearchStateProps;

export interface SimpleSearchStateProps {
    files: Page<File>;
    filesLoading: boolean;
    errors: string[];
    search: string;
    fileSearch: DetailedFileSearchReduxState;
}

export interface SimpleSearchOperations {
    clear: () => void;
    setFilesLoading: (loading: boolean) => void;
    searchFiles: (body: AdvancedSearchRequest) => void;
    setFilesPage: (page: Page<File>) => void;
    setSearch: (search: string) => void;
    setPrioritizedSearch: (st: HeaderSearchType) => void;
    toggleAdvancedSearch: () => void;
    setActivePage: () => void;
    setRefresh: (refresh?: () => void) => void;
}
