import {HeaderSearchType} from "@/DefaultObjects";

export type SearchProps = SimpleSearchOperations & SimpleSearchStateProps;

export interface SimpleSearchStateProps {
    errors: string[];
    search: string;
}

export interface SimpleSearchOperations {
    clear: () => void;
    setSearch: (search: string) => void;
    setPrioritizedSearch: (st: HeaderSearchType) => void;
    toggleAdvancedSearch: () => void;
    setActivePage: () => void;
    setRefresh: (refresh?: () => void) => void;
}
