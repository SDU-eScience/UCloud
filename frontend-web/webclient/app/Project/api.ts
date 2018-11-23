import { Cloud } from "Authentication/SDUCloudObject";
import { Page } from "Types";

export interface ProjectMetadataWithRights {
    metadata: ProjectMetadata
    canEdit: boolean
}

export interface ProjectMetadata {
    /**
     * The SDUCloud FSRoot this metadata belongs to (i.e. project)
     */
    sduCloudRoot: string

    /**
     * The title of this project
     */
    title: string

    /**
     * A list of creators of this project (defaults to users in project)
     */
    creators: Contributor[]

    /**
     * A description of the project.
     */
    description: string

    /**
     * The license of the project
     */
    license: string

    id: string

    embargoDate?: number
    accessConditions?: string

    keywords: string[]
    notes?: string
    contributors: Contributor[]
    references: string[]
    grants: Grant[]

    subjects?: Subject[]
    relatedIdentifiers?: RelatedIdentifier[]

    /**
    * The data management plan devised for the project
    * TODO: NOTE, Currently not being transmitted to backend or received from backend
    */
    dataManagementPlan?: string
}

export interface Grant {
    id: string
}

export interface Subject {
    term: string
    identifier: string
    scheme?: string
}

export interface RelatedIdentifier {
    relation: string
    identifier: string
}

export interface Contributor {
    name?: string
    affiliation?: string
    orcId?: string
    gnd?: string
}

export interface FileDescriptionForMetadata {
    id: string
    type: string
    path: string
}

export const simpleSearch = (
    query: string,
    page: number,
    itemsPerPage: number
): Promise<Page<ProjectMetadata>> =>
    Cloud.get(
        `/metadata/search?query=${encodeURI(query)}` +
        `&page=${page}&itemsPerPage=${itemsPerPage}`
    ).then(({ response }) => response);

export const getById = (id: string): Promise<ProjectMetadataWithRights> => {
    return Cloud.get(`/metadata/${encodeURI(id)}`).then(f => f.response); // FIXME Add error handling
};

export const getByPath = (path: string): Promise<ProjectMetadataWithRights> => {
    return Cloud.get(`/metadata/by-path?path=${encodeURI(path)}`).then(f => f.response); // FIXME Add error handling
};

export const updateById = (payload: any): Promise<any> => {
    return Cloud.post("/metadata", payload).then(f => f.response); // FIXME Add error handling
};