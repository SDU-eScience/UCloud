export { Buttons, EntriesPerPageSelector } from "./Pagination";
export { List } from "./List";
export { ManagedList } from "./ManagedList";
import { Page } from "Types";

export interface ManagedListProps {
    dataProvider: (page: number, itemsPerPage: number) => Promise<Page<any>>
    pageRenderer: (page: Page<any>) => React.ReactNode
}

export interface ManagedListState {
    loading: boolean
    results: Page<any>
    errorMessage?: string
    dataProvider: Function
}