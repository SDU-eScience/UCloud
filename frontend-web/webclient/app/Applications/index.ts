import { SortBy, SortOrder, File } from "Files";
import { Page } from "Types";
import { Dispatch } from "redux";
import { match } from "react-router-dom";
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
    prioritizeApplicationSearch: () => void
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
    match: match<{ jobId: string }>
    history: History
}

export interface Application {
    owner: string
    createdAt: number
    modifiedAt: number
    favorite: boolean
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

export type StdElement = { scrollTop: number, scrollHeight: number } | null

export type MaxTime = {
    hours: number | null
    minutes: number | null
    seconds: number | null
} | null

export interface JobInfo {
    maxTime: MaxTime
    numberOfNodes: number | null
    tasksPerNode: number | null
}

export interface RunAppState {
    promises: PromiseKeeper
    error?: string
    loading: boolean
    appName: string
    favorite: boolean
    displayAppName: string
    appVersion: string
    appDescription: string
    appAuthor: string[]
    parameters: ApplicationParameter[]
    parameterValues: {}
    jobInfo: JobInfo
    tool: {} // ???
    comment: string
    jobSubmitted: boolean
}

export interface RunAppProps {
    uppy: any
    history: History
    updatePageTitle: () => void
}

export interface ApplicationParameter {
    name: string
    optional: boolean
    defaultValue: any
    title: string
    description: string
    trueValue?: boolean
    falseValue?: boolean
    type: string
}

export interface ApplicationInformation {
    owner: string
    favorite?: boolean
    createdAt, modifiedAt: number
    description: {
        info: {
            name: string
            version: string
        }
        tool: {
            name: string
            version: string
        }
        authors: string[]
        title: string
        description: string
        invocation: any
        parameters: ApplicationParameter[]
        outputFileGlobs: [string, string]
    }
    tool: {
        owner: string
        createdAt: number
        modifiedAt: number
        description: {
            info: {
                name: string
                version: string
            }
            container: string
            defaultNumberOfNodes: number,
            defaultTasksPerNode: number,
            defaultMaxTime: {
                hours: number
                minutes: number
                seconds: number
            }
            requiredModules: any[],
            authors: string[]
            title: string,
            description: string
            backend: string
        }
    }
}

export enum ParameterTypes {
    InputFile = "input_file",
    InputDirectory = "input_directory",
    Integer = "integer",
    FloatingPoint = "floating_point",
    Text = "text",
    Boolean = "boolean"
}

export interface SearchFieldProps {
    onSubmit: () => void
    icon: string
    placeholder: string
    value: string
    loading: boolean
    onValueChange: (value: string) => void
}