import { History } from "history";
import { Creator, Subject, RelatedIdentifier } from "./api";

export { CreateUpdate } from "./CreateUpdate";
export { Search } from "./Search";
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
    contributors: Creator[]
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

export interface CreateUpdateProps {
    match: {
        params: string[]
    }
    history: History
}