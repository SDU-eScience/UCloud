import * as Types from "Types";
import { SortBy, SortOrder, File } from "Files";
import { Page } from "Types";
import { Dispatch } from "redux";
import PromiseKeeper from "PromiseKeeper";
import { History } from "history";

export interface ApplicationsProps extends ApplicationsStateProps, ApplicationsOperations { }

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

export interface ApplicationsOperations {
    onErrorDismiss: () => void
    updatePageTitle: () => void
    setLoading: (loading: boolean) => void
    fetchApplications: (a: number, b: number) => void
    updateApplications: (applications: Page<Application>) => void
}

export interface ApplicationsStateProps {
    page: Page<Application>
    loading: boolean
    error?: string
    sortBy: SortBy
    sortOrder: SortOrder
}

export interface AnalysesProps extends AnalysesStateProps, AnalysesOperations { }

export interface AnalysesStateProps {
    page: Page<Analysis>
    loading: boolean
    error?: string
}

export interface AnalysesOperations {
    onErrorDismiss: () => void
    updatePageTitle: (title: string) => void
    setLoading: (loading: boolean) => void
    fetchAnalyses: (itemsPerPage: number, pageNumber: number) => void
}

export interface AnalysesState {
    reloadIntervalId: number
}


export interface DetailedResultProps {
    dispatch: Dispatch
    match: {
        params: {
            jobId: string
        }
    }
}

export interface Application {
    owner: string
    createdAt: number
    modifiedAt: number

    tool: {
        owner: string
        createdAt: number
        modifiedAt: number

        description: {
            name: string
            version: string
        }
    }

    description: {
        info: {
            name: string
            version: string
        }

        title: string
        authors: string[]
        description: string

        parameters: any[]
        invocation: any[]
    }
}

export interface DetailedResultState {
    page: Page<File>
    loading: boolean
    complete: boolean
    appState: string
    status: string
    app: {
        name: string
        version: string
    }
    stdout: string
    stderr: string
    stdoutLine: number
    stderrLine: number
    stdoutOldTop: number,
    stderrOldTop: number,
    reloadIntervalId: number
    promises: PromiseKeeper
}

export type StdElement = { scrollTop: number, scrollHeight: number }

export interface RunAppState {
    promises: PromiseKeeper
    loading: boolean
    appName: string
    displayAppName: string
    appVersion: string
    appDescription: string
    appAuthor: string[]
    parameters: any[] // FIXME
    parameterValues: {}
    jobInfo: {
        maxTime?: {
            hours: number | null
            minutes: number | null
            seconds: number | null
        }
        numberOfNodes: number | null
        tasksPerNode: number | null
    }
    tool: {} // ???
    comment: string
    jobSubmitted: boolean
}

export interface RunAppProps {
    uppy: any
    history: History
    updatePageTitle: () => void
}