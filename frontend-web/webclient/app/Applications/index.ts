import {SharedFileSystemMount} from "Applications/FileSystems";
import {AnalysisReduxObject, ResponsiveReduxObject} from "DefaultObjects";
import {File, SortOrder} from "Files";
import {History} from "history";
import {SetStatusLoading} from "Navigation/Redux/StatusActions";
import PromiseKeeper from "PromiseKeeper";
import * as React from "react";
import {match} from "react-router";
import {Page} from "Types";
import {ParameterValues} from "Utilities/ApplicationUtilities";

/** @deprecated */
export type Analysis = JobWithStatus;

/** @deprecated */
export type AppState = JobState;

export enum JobState {
    VALIDATED = "VALIDATED",
    PREPARED = "PREPARED",
    SCHEDULED = "SCHEDULED",
    RUNNING = "RUNNING",
    TRANSFER_SUCCESS = "TRANSFER_SUCCESS",
    SUCCESS = "SUCCESS",
    FAILURE = "FAILURE",
    CANCELLING = "CANCELLING"
}

export function isJobStateFinal(state: JobState): boolean {
    return state === JobState.SUCCESS || state === JobState.FAILURE;
}

export interface JobWithStatus {
    jobId: string;
    name: string | null;
    owner: string;

    state: JobState;
    status: string;
    failedState: JobState | null;

    createdAt: number;
    modifiedAt: number;
    expiresAt: number | null;
    maxTime: number | null;
    outputFolder: string | null;

    metadata: ApplicationMetadata;

    // Fake props used only for the frontend
    checked?: boolean;
}

export type AnalysesStateProps = AnalysisReduxObject & { responsive: ResponsiveReduxObject };
export type AnalysesProps = AnalysesStateProps & AnalysesOperations;

type FetchJobsOperation = (
    itemsPerPage: number,
    pageNumber: number,
    sortOrder: SortOrder,
    sortBy: RunsSortBy,
    minTimestamp?: number,
    maxTimestamp?: number,
    filter?: AppState
) => void;

export interface AnalysesOperations {
    setLoading: (loading: boolean) => void;
    fetchJobs: FetchJobsOperation;
    onInit: () => void;
    setRefresh: (refresh?: () => void) => void;
    checkAnalysis: (jobId: string, checked: boolean) => void;
    checkAllAnalyses: (checked: boolean) => void;
}


export interface Application {
    favorite: boolean;
    owner: string;
    createdAt: number;
    modifiedAt: number;
    description: ApplicationDescription;
    tool: ApplicationTool;
    imageUrl: string;
}

interface ApplicationTool {
    owner: string;
    createdAt: number;
    modifiedAt: number;
    description: {
        info: ApplicationInfo
        container: string
        defaultNumberOfNodes: number
        defaultTasksPerNode: number
        defaultAllocationTime: MaxTime
        requiredModules: string[]
        authors: string[]
        title: string
        description: string
        backend: string
        license: string
    };
}

interface ApplicationInfo {
    name: string;
    version: string;
}

export interface ApplicationDescription {
    info: ApplicationInfo;
    tool: ApplicationInfo;
    authors: string[];
    title: string;
    description: string;
    invocation: any[]; // FIXME: Add type
    parameters: ApplicationParameter[];
    outputFileGlobs: string[];
    website?: string;
    resources: { multiNodeSupport: boolean };
    tags: string[];
}

export interface MaxTime {
    hours: number;
    minutes: number;
    seconds: number;
}

export interface MaxTimeForInput {
    hours: number;
    minutes: number;
    seconds: number;
}

export interface JobSchedulingOptionsForInput {
    maxTime: MaxTimeForInput;
    numberOfNodes: number;
    tasksPerNode: number;
    name: React.RefObject<HTMLInputElement>;
}

export interface AdditionalMountedFolder {
    readOnly: boolean;
    ref: React.RefObject<HTMLInputElement>;
    defaultValue?: string;
}

export interface AdditionalPeer {
    nameRef: React.RefObject<HTMLInputElement>;
    jobIdRef: React.RefObject<HTMLInputElement>;
}

export interface RunAppState {
    promises: PromiseKeeper;
    jobSubmitted: boolean;
    initialSubmit: boolean;
    application?: FullAppInfo;
    parameterValues: ParameterValues;
    schedulingOptions: JobSchedulingOptionsForInput;
    favorite: boolean;
    favoriteLoading: boolean;
    mountedFolders: AdditionalMountedFolder[];
    additionalPeers: AdditionalPeer[];
    fsShown: boolean;
    sharedFileSystems: { mounts: SharedFileSystemMount[] };
    previousRuns: Page<File>;
}

export interface RunOperations extends SetStatusLoading {
    updatePageTitle: () => void;
}

export interface RunAppProps extends RunOperations {
    match: match<{ appName: string, appVersion: string }>;
    history: History;
    updatePageTitle: () => void;
}

export interface NumberParameter extends BaseParameter {
    defaultValue: { value: number, type: "double" | "int" } | null;
    min: number | null;
    max: number | null;
    step: number | null;
    type: ParameterTypes.Integer | ParameterTypes.FloatingPoint;
}

export interface BooleanParameter extends BaseParameter {
    defaultValue: { value: boolean, type: "bool" } | null;
    trueValue?: string | null;
    falseValue?: string | null;
    type: ParameterTypes.Boolean;
}

export interface InputFileParameter extends BaseParameter {
    defaultValue: string | null;
    type: ParameterTypes.InputFile;
}

export interface InputDirectoryParameter extends BaseParameter {
    defaultValue: string | null;
    type: ParameterTypes.InputDirectory;
}

export interface TextParameter extends BaseParameter {
    defaultValue: { value: string, type: "string" } | null;
    type: ParameterTypes.Text;
}

export interface PeerParameter extends BaseParameter {
    suggestedApplication: string | null;
    type: ParameterTypes.Peer;
}

export interface SharedFileSystemParameter extends BaseParameter {
    fsType: "EPHEMERAL" | "PERSISTENT";
    mountLocation: string;
    exportToPeers: boolean;
    type: ParameterTypes.SharedFileSystem;
}

interface BaseParameter {
    name: string;
    optional: boolean;
    title: string;
    description: string;
    unitName?: string | React.ReactNode | null;
    type: string;
    visible?: boolean;
}

export type ApplicationParameter =
    InputFileParameter |
    InputDirectoryParameter |
    NumberParameter |
    BooleanParameter |
    TextParameter |
    PeerParameter |
    SharedFileSystemParameter;

type Invocation = WordInvocation | VarInvocation;

interface WordInvocation {
    type: "word";
    word: string;
}

interface VarInvocation {
    type: "var";
    variableNames: string[];
    prefixGlobal: string;
    suffixGlobal: string;
    prefixVariable: string;
    suffixVariable: string;
    variableSeparator: string;
}

export enum ParameterTypes {
    InputFile = "input_file",
    InputDirectory = "input_directory",
    Integer = "integer",
    FloatingPoint = "floating_point",
    Text = "text",
    Boolean = "boolean",
    Peer = "peer",
    SharedFileSystem = "shared_file_system"
}

export interface DetailedApplicationSearchReduxState {
    hidden: boolean;
    appName: string;
    appVersion: string;
    tags: string;
    error?: string;
    loading: boolean;
}

export interface DetailedApplicationOperations {
    setAppName: (n: string) => void;
    setVersionName: (v: string) => void;
    // tslint:disable-next-line:ban-types
    fetchApplicationsFromName: (q: string, i: number, p: number, c?: Function) => void;
    // tslint:disable-next-line:ban-types
    fetchApplicationsFromTag: (t: string, i: number, p: number, c?: Function) => void;
}

// New interfaces
export interface ApplicationMetadata {
    name: string;
    version: string;
    authors: string[];
    title: string;
    description: string;
    tags: string[];
    website?: string;
}

export enum ApplicationType {
    BATCH = "BATCH",
    VNC = "VNC",
    WEB = "WEB"
}

export interface ApplicationInvocationDescription {
    tool: Tool;
    invocation: Invocation[];
    parameters: ApplicationParameter[];
    outputFileGlobs: string[];
    applicationType: ApplicationType;
    shouldAllowAdditionalMounts: boolean;
    shouldAllowAdditionalPeers: boolean;
    allowMultiNode: boolean;
}

interface Tool {
    name: string;
    version: string;
    tool: ToolReference;
}

interface ToolReference {
    owner: string;
    createdAt: number;
    modifiedAt: number;
    description: ToolDescription;
}

interface NameAndVersion {
    name: string;
    version: string;
}

interface ToolDescription {
    info: NameAndVersion;
    container: string;
    defaultNumberOfNodes: number;
    defaultTasksPerNode: number;
    defaultTimeAllocation: MaxTime;
    requiredModules: string[];
    authors: string[];
    title: string;
    description: string;
    backend: string;
    license: string;
}

export interface WithAppMetadata {
    metadata: ApplicationMetadata;
}

export interface WithAppInvocation {
    invocation: ApplicationInvocationDescription;
}

export interface WithAppFavorite {
    favorite: boolean;
}

export enum RunsSortBy {
    state = "STATE",
    application = "APPLICATION",
    startedAt = "STARTED_AT",
    lastUpdate = "LAST_UPDATE",
    createdAt = "CREATED_AT",
    name = "NAME"
}

export interface WithAllAppTags {
    tags: string[];
}

export interface FollowStdStreamResponse {
    failedState: AppState | null;
    state: AppState | null;
    status: string | null;
    stdout: string | null;
    stderr: string | null;
}

export type FullAppInfo = WithAppFavorite & WithAppInvocation & WithAppMetadata & WithAllAppTags;
