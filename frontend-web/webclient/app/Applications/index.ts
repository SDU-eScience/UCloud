import {AnalysisReduxObject} from "DefaultObjects";
import {File, SortOrder} from "Files";
import {History} from "history";
import {SetStatusLoading} from "Navigation/Redux/StatusActions";
import PromiseKeeper from "PromiseKeeper";
import * as React from "react";
import {match} from "react-router";
import {ParameterValues} from "Utilities/ApplicationUtilities";
import {Product} from "Accounting";

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

export interface AdvancedSearchRequest extends PaginationRequest {
    query?: string;
    tags?: string[];
    showAllVersions: boolean;
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
    creditsCharged?: number;

    metadata: ApplicationMetadata;

    // Fake props used only for the frontend
    checked?: boolean;
}

export type AnalysesStateProps = AnalysisReduxObject;
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
        info: ApplicationInfo;
        container: string;
        defaultNumberOfNodes: number;
        defaultAllocationTime: MaxTime;
        requiredModules: string[];
        authors: string[];
        title: string;
        description: string;
        backend: string;
        license: string;
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
    invocation: Invocation[];
    parameters: ApplicationParameter[];
    outputFileGlobs: string[];
    website?: string;
    resources: {multiNodeSupport: boolean};
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
    name: React.RefObject<HTMLInputElement>;
}

export interface AdditionalMountedFolder {
    ref: React.RefObject<HTMLInputElement>;
    defaultValue?: string;
}

export interface AdditionalPeer {
    nameRef: React.RefObject<HTMLInputElement>;
    jobIdRef: React.RefObject<HTMLInputElement>;
}

export interface LicenseServerId {
    id: string;
    name: string;
}

export interface RunAppState {
    promises: PromiseKeeper;
    jobSubmitted: boolean;
    initialSubmit: boolean;
    application?: FullAppInfo;
    parameterValues: ParameterValues;
    schedulingOptions: JobSchedulingOptionsForInput;
    useUrl: boolean;
    url: React.RefObject<HTMLInputElement>;
    useIp: boolean;
    ip: React.RefObject<HTMLInputElement>;
    favorite: boolean;
    favoriteLoading: boolean;
    mountedFolders: AdditionalMountedFolder[];
    additionalPeers: AdditionalPeer[];
    fsShown: boolean;
    previousRuns: Page<File>;
    unknownParameters: string[];
    reservation: string;
    reservationMachine?: Product;
    balance: number;
    inlineError?: string;
}

export interface RunOperations extends SetStatusLoading {
    onInit: () => void;
}

export interface RunAppProps extends RunOperations {
    match: match<{appName: string; appVersion: string}>;
    history: History;
    onInit: () => void;
    project?: string;
}

export interface NumberParameter extends BaseParameter {
    defaultValue: {value: number; type: "double" | "int"} | null;
    min: number | null;
    max: number | null;
    step: number | null;
    type: ParameterTypes.Integer | ParameterTypes.FloatingPoint;
}

export interface BooleanParameter extends BaseParameter {
    defaultValue: {value: boolean; type: "bool"} | null;
    trueValue?: string | null;
    falseValue?: string | null;
    type: ParameterTypes.Boolean;
}

interface EnumOption {
    name: string;
    value: string;
}
export interface EnumerationParameter extends BaseParameter {
    default: {value: string; type: "enum"} | null;
    options: EnumOption[];
    type: ParameterTypes.Enumeration;
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
    defaultValue: {value: string; type: "string"} | null;
    type: ParameterTypes.Text;
}

export interface RangeParameter extends BaseParameter {
    type: ParameterTypes.Range;
    defaultValue: {min: number; max: number};
    min: number;
    max: number;
}

export interface PeerParameter extends BaseParameter {
    suggestedApplication: string | null;
    type: ParameterTypes.Peer;
}

export interface LicenseServerParameter extends BaseParameter {
    type: ParameterTypes.LicenseServer;
    tagged: string[];
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
    RangeParameter |
    PeerParameter |
    LicenseServerParameter |
    EnumerationParameter;

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
    Enumeration = "enumeration",
    Range = "range",
    Peer = "peer",
    LicenseServer = "license_server"
}

export interface DetailedApplicationSearchReduxState {
    hidden: boolean;
    appQuery: string;
    tags: Set<string>;
    showAllVersions: boolean;
    error?: string;
    loading: boolean;
}

export interface DetailedApplicationOperations {
    setAppQuery: (n: string) => void;
    addTag: (tag: string) => void;
    removeTag: (tag: string) => void;
    clearTags: () => void;
    setSearch: (search: string) => void;
    setShowAllVersions: () => void;
    // tslint:disable-next-line:ban-types
    fetchApplications: (b: AdvancedSearchRequest, c?: Function) => void;
}

// New interfaces
export interface ApplicationMetadata {
    name: string;
    version: string;
    authors: string[];
    title: string;
    description: string;
    website?: string;
    public: boolean;
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
    licenseServers: string[];
    allowMultiNode: boolean;
    container: null | {
        changeWorkingDirectory: boolean;
        runAsRoot: boolean;
        runAsRealUser: boolean;
    };
}

export interface Tool {
    name: string;
    version: string;
    tool: ToolReference;
}

export interface ToolReference {
    owner: string;
    createdAt: number;
    modifiedAt: number;
    description: ToolDescription;
}

interface NameAndVersion {
    name: string;
    version: string;
}

export interface ToolDescription {
    info: NameAndVersion;
    container: string;
    defaultNumberOfNodes: number;
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
