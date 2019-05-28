import { File } from "Files";
import { Page } from "Types";
import { match } from "react-router";
import PromiseKeeper from "PromiseKeeper";
import { History } from "history";
import { DetailedResultReduxObject, ResponsiveReduxObject } from "DefaultObjects";
import { ParameterValues } from "Utilities/ApplicationUtilities";
import { SetStatusLoading } from "Navigation/Redux/StatusActions";

export interface Analysis {
    status: string
    state: AppState
    jobId: string
    appName: string
    appVersion: string
    createdAt: number
    modifiedAt: number
    owner: string
    metadata: ApplicationMetadata
}

export interface AnalysesProps extends AnalysesStateProps, AnalysesOperations { }

export interface AnalysesStateProps {
    page: Page<Analysis>
    loading: boolean
    responsive: ResponsiveReduxObject
    error?: string
}

export interface AnalysesOperations {
    onErrorDismiss: () => void
    setLoading: (loading: boolean) => void
    fetchJobs: (itemsPerPage: number, pageNumber: number) => void
    onInit: () => void
    setRefresh: (refresh?: () => void) => void
}

export interface AnalysesState {
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
    FAILURE = "FAILURE",
    CANCELLING = "CANCELLING"
}

export interface DetailedResultState {
    complete: boolean
    appState: AppState
    status: string
    app?: ApplicationMetadata
    stdout: string
    stderr: string
    stdoutLine: number
    stderrLine: number
    stdoutOldTop: number,
    stderrOldTop: number,
    reloadIntervalId: number
    promises: PromiseKeeper
    fsError?: string
    fsLoading: boolean
    fsShown: boolean
    fsPath: string
    fsPage: Page<File>
    fsDisallowedPaths: string[]
    fsCallback: Function
    fsIsFavorite: boolean
    outputFolder?: string
    appType?: ApplicationType
    webLink?: string
}

export type StdElement = { scrollTop: number, scrollHeight: number } | null

export interface MaxTime {
    hours: number
    minutes: number
    seconds: number
}

export interface MaxTimeForInput {
    hours: number
    minutes: number
    seconds: number
}

export interface JobSchedulingOptionsForInput {
    maxTime: MaxTimeForInput
    numberOfNodes: number
    tasksPerNode: number
}

export interface RefReadPair {
    readOnly: boolean
    ref: React.RefObject<HTMLInputElement>
    defaultValue?: string
}

export interface RunAppState {
    promises: PromiseKeeper
    jobSubmitted: boolean
    initialSubmit: boolean
    error?: string
    application?: WithAppMetadata & WithAppInvocation & WithAppFavorite
    parameterValues: ParameterValues
    schedulingOptions: JobSchedulingOptionsForInput
    favorite: boolean
    favoriteLoading: boolean
    mountedFolders: RefReadPair[]
}

export interface RunOperations extends SetStatusLoading {
    updatePageTitle: () => void
}

export interface RunAppProps extends RunOperations {
    match: match<{ appName: string, appVersion: string }>
    history: History
    updatePageTitle: () => void
}

export interface NumberParameter extends BaseParameter {
    defaultValue: { value: number, type: "double" | "int" } | null
    min: number | null
    max: number | null
    step: number | null
    type: ParameterTypes.Integer | ParameterTypes.FloatingPoint
}

export interface BooleanParameter extends BaseParameter {
    defaultValue: { value: boolean, type: "bool" } | null
    trueValue?: string | null
    falseValue?: string | null
    type: ParameterTypes.Boolean
}

export interface InputFileParameter extends BaseParameter {
    defaultValue: string | null
    type: ParameterTypes.InputFile
}

export interface InputDirectoryParameter extends BaseParameter {
    defaultValue: string | null
    type: ParameterTypes.InputDirectory
}

export interface TextParameter extends BaseParameter {
    defaultValue: { value: string, type: "string" } | null
    type: ParameterTypes.Text
}

interface BaseParameter {
    name: string
    optional: boolean
    title: string
    description: string
    unitName?: string | React.ReactNode | null
    type: string
    visible?: boolean
}

export type ApplicationParameter = InputFileParameter | InputDirectoryParameter | NumberParameter | BooleanParameter | TextParameter;

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



// New interfaces
export interface ApplicationMetadata {
    name: string
    version: string
    authors: string[]
    title: string
    description: string
    tags: string[]
    website?: string
}

type ApplicationType = "BATCH" | "VNC" | "WEB"

export interface ApplicationInvocationDescription {
    tool: Tool
    invocation: Invocation[]
    parameters: ApplicationParameter[]
    outputFileGlobs: string[]
    applicationType: ApplicationType
    resources: Resources
}

interface Resources {
    multiNodeSupport: boolean
    coreRequirements: number
    memoryRequirementsMb: number
    gpuRequirements: number
    tempStorageRequirementsGb: number
    persistentStorageRequirementsGb: number
}

interface Tool {
    name: string
    version: string
    tool: ToolReference
}

interface ToolReference {
    owner: string
    createdAt: number
    modifiedAt: number
    description: ToolDescription
}

interface NameAndVersion {
    name: string
    version: string
}

interface ToolDescription {
    info: NameAndVersion
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

export interface WithAppMetadata {
    metadata: ApplicationMetadata
}

export interface WithAppInvocation {
    invocation: ApplicationInvocationDescription
}

export interface WithAppFavorite {
    favorite: boolean
}
