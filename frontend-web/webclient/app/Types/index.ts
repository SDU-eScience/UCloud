export interface SidebarOption {
    name: string
    icon: string
    href: string
    children?: SidebarOption
}


export interface Page<T> {
    itemsInTotal: number,
    itemsPerPage: number,
    pagesInTotal: number
    pageNumber: number,
    items: T[]
}

export enum AccessRight {
    READ = "READ",
    WRITE = "WRITE",
    EXECUTE = "EXECUTE"
}

export interface DropdownOption {
    name: string
    value: string
}

export type Action = { type: String }
export interface Error extends Action { error?: string }
export interface SetLoadingAction extends Action { loading: boolean }
export interface ReceivePage<T> extends Action { page: Page<T> }
