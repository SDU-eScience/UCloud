import PromiseKeeper from "../../PromiseKeeper";
import { match } from "react-router-dom";
import { Page } from "../../Types";

export interface Publication {
    id: number
    name: string
    zenodoAction: string
    createdAt: number
    modifiedAt: number
}

export interface ZenodoInfoState {
    promises: PromiseKeeper
    loading: boolean
    publicationID: string
    publication?: Publication
    intervalId: number
    uploads?: any
}

export type ZenodoInfoProps = {
    match: match<{ jobID: string }>
}


export interface ZenodoHomeProps {
    connected: boolean
    loading: boolean
    page: Page<Publication>
    fetchPublications: (pageNo: Number, pageSize: number) => void
    updatePageTitle: () => void
}

export interface ZenodoHomeState {
    sorting: {
        lastSorting: string
        asc: boolean
    }
}
