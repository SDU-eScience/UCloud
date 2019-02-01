import { File } from "Files";
import { Page } from "Types";
import { match } from "react-router";
import PromiseKeeper from "PromiseKeeper";
import { History } from "history";
import { DetailedResultReduxObject } from "DefaultObjects";

export interface Analysis {
    status: string
    state: AppState
    jobId: string
    appName: string
    appVersion: string
    createdAt: number
    modifiedAt: number
    owner: string
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
    setActivePage: () => void
    setRefresh: (refresh?: () => void) => void
}

export interface AnalysesState {
    reloadIntervalId: number
}

export interface DetailedResultOperations {
    receivePage: (page: Page<File>) => void,
    setPageTitle: (jobId: string) => void
    setLoading: (loading: boolean) => void
    detailedResultError: (error: string) => void
    fetchPage: (jobId: string, pageNumber: number, itemsPerPage: number) => void
    setRefresh: (refresh?: () => void) => void
}

export interface DetailedResultProps extends DetailedResultReduxObject, DetailedResultOperations {
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
    imageUrl: string
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

export interface ApplicationDescription {
    info: ApplicationInfo
    tool: ApplicationInfo
    authors: string[]
    title: string
    description: string
    invocation: any[]
    parameters: ApplicationParameter[]
    outputFileGlobs: string[]
    website?: string
    resources: { multiNodeSupport: boolean }
    tags: string[]
}

export enum AppState {
    VALIDATED = "VALIDATED",
    PREPARED = "PREPARED",
    SCHEDULED = "SCHEDULED",
    RUNNING = "RUNNING",
    TRANSFER_SUCCESS = "TRANSFER_SUCCESS",
    SUCCESS = "SUCCESS",
    FAILURE = "FAILURE"
}

export interface DetailedResultState {
    complete: boolean
    appState: AppState
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
    hours: number
    minutes: number
    seconds: number
}

// export interface JobSchedulingOptions {
//     maxTime: MaxTime
//     numberOfNodes: number | null
//     tasksPerNode: number | null
// }

export interface MaxTimeForInput {
    hours: number | null,
    minutes: number | null,
    seconds: number | null

}

export interface JobSchedulingOptionsForInput {
    maxTime: MaxTimeForInput | null
    numberOfNodes: number | null
    tasksPerNode: number | null
}

export interface RunAppState {
    promises: PromiseKeeper
    jobSubmitted: boolean

    error?: string
    loading: boolean

    application?: Application
    parameterValues: {}
    schedulingOptions: JobSchedulingOptionsForInput
    favorite: boolean
    favoriteLoading: boolean
}

export interface RunAppProps {
    match: match<{ appName: string, appVersion: string }>
    history: History
    updatePageTitle: () => void
}

export interface NumberParameter extends BaseParameter {
    defaultValue: { value: number, type: "double" | "int" } | null
    min: number | null
    max: number | null
    step: number | null
    type: "integer" | "floating_point"
}

export interface BooleanParameter extends BaseParameter {
    defaultValue: { value: boolean, type: "bool" } | null
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
    defaultValue: { value: string, type: "string" } | null
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

type Info = { name: string, version: string }
export interface Description {
    info: Info
    tool: Info
    authors: string[]
    title: string
    description: string
    invocation: Invocation[]
    parameters: ApplicationParameter[]
    outputFileGlobs: [string, string]
    tags: string[]
}
interface Tool {
    owner: string
    createdAt: number
    modifiedAt: number
    description: ToolDescription
}

interface ToolDescription {
    info: Info
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

export interface DetailedApplicationSearchReduxState {
    hidden: boolean
    appName: string
    appVersion: string
    tags: string
    error?: string
    loading: boolean
}

export interface DetailedApplicationOperations {
    setAppName: (n: string) => void
    setVersionName: (v: string) => void
    setError: (err?: string) => void
    fetchApplicationsFromName: (q: string, i: number, p: number, c?: Function) => void
    fetchApplicationsFromTag: (t: string, i: number, p: number, c?: Function) => void
}
