export interface File {
    type: string
    path: string
    createdAt: number
    modifiedAt: number
    ownerName: string
    size: number
    acl: Array<Acl>
    favorited: boolean
    sensitivityLevel: string
    isChecked?: boolean
    link: boolean
}

export interface Acl {
    entity: Entity
    right: string
}

export interface Entity {
    type: string
    name: string
    displayName: string
    zone: string
}

export interface Analysis {
    name: string
    status: string
    state: string
    jobId: string
    appName: string
    appVersion: string
    createdAt: number
    modifiedAt: number
    owner: string
}

export interface Application {
    tool: {
        name:string
        version:string
    }
    info: {
        name:string
        version:string
    }
    prettyName:string
    authors:string[]
    createdAt:number
    modifiedAt:number
    description:string
}

export interface Status { 
    title: string 
    level: string
    body: string
}

export interface SidebarOption {
    name: string
    icon: string
    href: string
    children?: SidebarOption
}

export interface Publication {
    id: number
    name: string
    zenodoAction: string
    createdAt: number
    modifiedAt: number   
}

export interface Notification {
    type: string
    jobId?:string
    ts: number
    status?: string
    id: string
    isRead: boolean
}

export const emptyPage: Page<any> = { items: [], itemsPerPage: 10, itemsInTotal: 0, pageNumber: 0 };

export interface Page<T> {
    itemsInTotal: number,
    itemsPerPage: number,

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