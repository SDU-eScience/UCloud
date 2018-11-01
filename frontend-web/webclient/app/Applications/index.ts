import { File } from "Files";
import { Page } from "Types";
import { match } from "react-router";
import PromiseKeeper from "PromiseKeeper";
import { History } from "history";
import { DetailedResultReduxObject, ApplicationReduxObject } from "DefaultObjects";

export type ApplicationsProps = ApplicationReduxObject & ApplicationsOperations;

export interface Analysis {
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
    setFavoritesLoading: (loading: boolean) => void
    fetchApplications: (a: number, b: number) => void
    fetchFavorites: (a: number, b: number) => void
    receiveApplications: (applications: Page<Application>) => void
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

export interface DetailedResultOperations {
    receivePage: (page: Page<File>) => void,
    setPageTitle: (jobId: string) => void
    setLoading: (loading: boolean) => void
    detailedResultError: (error: string) => void
    fetchPage: (jobId: string, pageNumber: number, itemsPerPage: number) => void
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
}

export interface DetailedResultState {
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


export interface ApplicationInformation {
    owner: string
    favorite?: boolean
    createdAt, modifiedAt: number
    description: Description
    tool: Tool
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