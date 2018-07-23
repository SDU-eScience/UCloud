import PromiseKeeper from "PromiseKeeper";
import { match } from "react-router-dom";
import { Page } from "Types";

export interface Publication {
    id: number
    name: string
    zenodoAction: string
    createdAt: number
    modifiedAt: number
    status: ZenodoPublicationStatus
    uploads: Upload[]
}

export enum ZenodoPublicationStatus { "PENDING", "UPLOADING", "COMPLETE", "FAILURE" }

interface Upload {
    dataObject: string
    hasBeenTransmitted: boolean
    updatedAt: number
}

export interface ZenodoInfoState {
    promises: PromiseKeeper
    loading: boolean
    publicationID: string
    publication?: Publication
    intervalId: number
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
