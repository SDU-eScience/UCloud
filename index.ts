import Usage from "Accounting/Usage";
import {match} from "react-router";

export {Usage};

export type AccountingProps = AccountingOperations & AccountingStateProps;

export interface AccountingStateProps {
}

export interface AccountingOperations {
    onInit: () => void;
    setRefresh: (refresh?: () => void) => void;
}

export type DetailedUsageProps = DetailedUsageStateProps & DetailedUsageOperations;

export interface DetailedUsageStateProps {
    match: match<{projectId: string}>;
}

export interface DetailedUsageOperations {
    onInit: () => void;
    setRefresh: (refresh?: () => void) => void;
}

