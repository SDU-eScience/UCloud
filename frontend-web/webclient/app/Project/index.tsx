import { History } from "history";
import { Contributor, Subject, RelatedIdentifier } from "./api";
import { RouterLocationProps } from "Utilities/URIUtilities";
export { CreateUpdate } from "./CreateUpdate";
export { ManagedView, View } from "./View";

export interface CreateUpdateState {
    id?: string
    path: string
    title: string
    description: string
    license: {}
    keywords: string[]
    notes: ""
    dataManagementPlan: ""
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

export interface CreateUpdateProps extends RouterLocationProps {
    history: History
}