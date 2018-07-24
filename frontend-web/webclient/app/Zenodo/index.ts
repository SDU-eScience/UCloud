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

export enum ZenodoPublicationStatus {
    PENDING = "PENDING",
    UPLOADING = "UPLOADING",
    COMPLETE = "COMPLETE",
    FAILURE = "FAILURE"
}

interface Upload {
    dataObject: string
    hasBeenTransmitted: boolean
    updatedAt: number
}

export interface ZenodoInfoState {
    error?: string
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
    error?: string
    connected: boolean
    loading: boolean
    page: Page<Publication>
    onErrorDismiss: () => void
    fetchPublications: (pageNo: Number, pageSize: number) => void
    updatePageTitle: () => void
}

export interface ZenodoHomeState {
    sorting: {
        lastSorting: string
        asc: boolean
    }
}
