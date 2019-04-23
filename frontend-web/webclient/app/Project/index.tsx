import { Contributor, Subject, RelatedIdentifier } from "./api";
import { RouterLocationProps } from "Utilities/URIUtilities";
import { AddSnackOperation } from "Snackbar/Snackbars";
export { default as CreateUpdate } from "./CreateUpdate";
export { ManagedView, View } from "./View";
export { default as Manage } from "./Management"

export interface CreateUpdateState {
    id?: string
    path: string
    title: string
    description: string
    license?: { title: string, link: string, identifier: string }
    keywords: string[]
    notes: string
    dataManagementPlan: string
    contributors: Contributor[]
    references: string[]
    grants: string[]
    subjects: Subject[]
    relatedIdentifiers: RelatedIdentifier[]
    errors: {
        description?: boolean
        title?: boolean
        contributors: {}
        subjects: {}
        relatedIdentifiers: {}
    }
}

export type CreateUpdateProps = RouterLocationProps & AddSnackOperation;

export interface DetailedProjectSearchReduxState {
    error?: string
    loading: boolean
    hidden: boolean
    projectName: string
}

export interface DetailedProjectSearchOperations {
    setProjectName: (name: string) => void
    fetchProjectsFromName: (name: string, itemsPerPage: number, page: number, callback: () => void) => void
    setError: (error?: string) => void
}