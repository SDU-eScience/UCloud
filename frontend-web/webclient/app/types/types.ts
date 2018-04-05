export interface File {
    type: string
    path: Path
    createdAt: number
    modifiedAt: number
    size: number
    acl: Array<Acl>
    favorited: boolean
    sensitivityLevel: string
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

}

export interface Path {
    path: string
    uri: string
    name: string
}

export interface Analyses {
    jobId:string
    owner:string
    state:string
    status:string
    appName:string
    appVersion:string
    createdAt:number
    modifiedAt:number
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