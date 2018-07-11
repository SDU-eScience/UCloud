import * as Types from "../../Types";
import { SortBy, SortOrder } from "../Files";
import { Page, Analysis } from "../../Types";
import { Dispatch } from "redux";
import PromiseKeeper from "../../PromiseKeeper";
import { History } from "history";

export interface ApplicationsProps extends ApplicationsStateProps, ApplicationsOperations { }

export interface ApplicationsOperations {
    updatePageTitle: () => void
    setLoading: (loading: boolean) => void
    fetchApplications: () => void
    updateApplications: (applications: Page<Types.Application>) => void
    toPage: (pageNumber: number) => void
    updateApplicationsPerPage: (applicationsPerPage: number) => void
}

export interface ApplicationsStateProps {
    applications: Page<Types.Application>
    loading: boolean
    itemsPerPage, pageNumber: number
    sortBy: SortBy
    sortOrder: SortOrder
}

export interface AnalysesProps extends AnalysesStateProps, AnalysesOperations {}

export interface AnalysesStateProps {
    page: Page<Analysis>
    loading: boolean
}

export interface AnalysesOperations {
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