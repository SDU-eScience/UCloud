import {HeaderSearchType} from "@/DefaultObjects";

export type SearchProps = SimpleSearchOperations & SimpleSearchStateProps;

export interface SimpleSearchStateProps {
}

export interface SimpleSearchOperations {
    clear: () => void;
    setPrioritizedSearch: (st: HeaderSearchType) => void;
    setActivePage: () => void;
    setRefresh: (refresh?: () => void) => void;
}
