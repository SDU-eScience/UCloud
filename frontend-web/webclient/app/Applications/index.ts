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
    favorite: boolean
    owner: string
    createdAt: number
    modifiedAt: number
    description: ApplicationDescription
    tool: ApplicationTool
}

interface ApplicationTool {
    owner: string
    createdAt: number
    modifiedAt: number
    description: {
        info: ApplicationInfo
        container: string
        defaultNumberOfNodes: number
        defaultTasksPerNode: number
        defaultMaxTime: MaxTime
        requiredModules: string[]
        authors: string[]
        title: string
        description: string
        backend: string
    }
}

interface ApplicationInfo {
    name: string
    version: string
}

interface ApplicationDescription {
    info: ApplicationInfo
    tool: ApplicationInfo
    authors: string[]
    title: string
    description: string
    invocation: any[]
    parameters: ApplicationParameter[]
    outputFileGlobs: string[]
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

export interface NumberParameter extends BaseParameter {
    defaultValue: number | null
    min: number | null
    max: number | null
    step: number | null
    type: "integer" | "floating_point"
}

export interface BooleanParameter extends BaseParameter {
    defaultValue: boolean | null
    trueValue?: string | null
    falseValue?: string | null
    type: "boolean"
}

export interface InputFileParameter extends BaseParameter {
    defaultValue: string | null
    type: "input_file"
}

export interface InputDirectoryParameter extends BaseParameter {
    defaultValue: string | null
    type: "input_directory"
}

export interface TextParameter extends BaseParameter {
    defaultValue: string | null
    type: "text"
}

interface BaseParameter {
    name: string
    optional: boolean
    title: string
    description: string
    unitName?: string | null
}

export type ApplicationParameter = InputFileParameter | InputDirectoryParameter | NumberParameter | BooleanParameter | TextParameter

type Invocation = WordInvocation | VarInvocation

interface WordInvocation {
    type: "word"
    word: string   
}

interface VarInvocation {
    type: "var"
    variableNames: string[]
    prefixGlobal: string
    suffixGlobal: string
    prefixVariable: string
    suffixVariable: string
    variableSeparator: string
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
        invocation: Invocation[]
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