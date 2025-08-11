import {buildQueryString} from "@/Utilities/URIUtilities";
import {apiBrowse, apiRetrieve, apiSearch, apiUpdate} from "@/Authentication/DataHook";
import {Client} from "@/Authentication/HttpClientInstance";
import {FindByLongId, PaginationRequestV2} from "@/UCloud";
import {b64EncodeUnicode} from "@/Utilities/XHRUtils";
import {getStoredProject} from "@/Project/ReduxState";

const baseContext = "/api/hpc/apps";

export interface Tool {
    owner: string;
    createdAt: number;
    modifiedAt: number;
    description: NormalizedToolDescription;
}

export interface NormalizedToolDescription {
    info: NameAndVersion;
    container?: string;
    defaultNumberOfNodes: number;
    defaultTimeAllocation: SimpleDuration;
    requiredModules: string[];
    authors: string[];
    title: string;
    description: string;
    backend: ("SINGULARITY" | "DOCKER" | "VIRTUAL_MACHINE" | "NATIVE");
    license: string;
    image?: string;
    supportedProviders?: string[];
}

export interface ToolReference {
    name: string;
    version: string;
    tool?: Tool;
}

export interface Application {
    metadata: ApplicationMetadata;
    invocation: ApplicationInvocationDescription;
    versions?: string[] | null;
    favorite?: boolean | null;
}

export interface NameAndVersion {
    name: string;
    version: string;
}

export interface ApplicationMetadata {
    name: string;
    version: string;
    authors: string[];
    title: string;
    description: string;
    website?: string;
    public: boolean;
    flavorName?: string;
    groupId?: number | null;
}

export interface ApplicationInvocationDescription {
    tool: ToolReference;
    invocation: InvocationParameter[];
    parameters: ApplicationParameter[];
    outputFileGlobs: string[];
    applicationType: ("BATCH" | "VNC" | "WEB");
    vnc?: VncDescription;
    web?: WebDescription;
    ssh?: SshDescription;
    container?: ContainerDescription;
    environment?: Record<string, InvocationParameter>;
    allowAdditionalMounts?: boolean;
    allowAdditionalPeers?: boolean;
    allowPublicLink?: boolean;
    allowMultiNode: boolean;
    allowPublicIp: boolean;
    fileExtensions: string[];
    licenseServers: string[];
}

export interface SimpleDuration {
    hours: number;
    minutes: number;
    seconds: number;
}

export type InvocationParameter =
    EnvironmentVariableParameter
    | WordInvocationParameter
    | VariableInvocationParameter
    | BooleanFlagParameter

export interface EnvironmentVariableParameter {
    variable: string;
    type: ("env");
}

export interface WordInvocationParameter {
    word: string;
    type: ("word");
}

export interface VariableInvocationParameter {
    variableNames: string[];
    prefixGlobal: string;
    suffixGlobal: string;
    prefixVariable: string;
    suffixVariable: string;
    isPrefixVariablePartOfArg: boolean;
    isSuffixVariablePartOfArg: boolean;
    type: ("var");
}

export interface BooleanFlagParameter {
    variableName: string;
    flag: string;
    type: ("bool_flag");
}

export namespace ApplicationParameterNS {
    export interface InputFile {
        name: string;
        optional: boolean;
        defaultValue?: any;
        title: string;
        description: string;
        type: ("input_file");
    }

    export interface InputDirectory {
        name: string;
        optional: boolean;
        defaultValue?: any;
        title: string;
        description: string;
        type: ("input_directory");
    }

    export interface Text {
        name: string;
        optional: boolean;
        defaultValue?: any;
        title: string;
        description: string;
        type: ("text");
    }

    export interface TextArea {
        name: string;
        optional: boolean;
        defaultValue?: any;
        title: string;
        description: string;
        type: ("textarea");
    }

    export interface Integer {
        name: string;
        optional: boolean;
        defaultValue?: any;
        title: string;
        description: string;
        min?: number;
        max?: number;
        step?: number;
        unitName?: string;
        type: ("integer");
    }

    export interface FloatingPoint {
        name: string;
        optional: boolean;
        defaultValue?: any;
        title: string;
        description: string;
        min?: number;
        max?: number;
        step?: number;
        unitName?: string;
        type: ("floating_point");
    }

    export interface Bool {
        name: string;
        optional: boolean;
        defaultValue?: any;
        title: string;
        description: string;
        trueValue: string;
        falseValue: string;
        type: ("boolean");
    }

    export interface Enumeration {
        name: string;
        optional: boolean;
        defaultValue?: any;
        title: string;
        description: string;
        options: EnumOption[];
        type: ("enumeration");
    }

    export interface EnumOption {
        name: string;
        value: string;
    }

    export interface Peer {
        name: string;
        title: string;
        description: string;
        suggestedApplication?: string;
        defaultValue?: any;
        optional: boolean;
        type: ("peer");
    }

    export interface Ingress {
        name: string;
        title: string;
        description: string;
        defaultValue?: any;
        optional: boolean;
        type: ("ingress");
    }

    export interface LicenseServer {
        name: string;
        title: string;
        optional: boolean;
        description: string;
        tagged: string[];
        defaultValue?: any;
        type: ("license_server");
    }

    export interface NetworkIP {
        name: string;
        title: string;
        description: string;
        defaultValue?: any;
        optional: boolean;
        type: ("network_ip");
    }

    export interface Workflow {
        name: string;
        title: string;
        description: string;
        defaultValue?: any;
        optional: boolean;
        type: ("workflow");
    }

    export interface Readme {
        name: string;
        title: string;
        description: string;
        defaultValue?: any;
        optional: boolean;
        type: ("readme");
    }

    export interface ModuleList {
        name: string;
        title: string;
        description: string;
        defaultValue?: any;
        optional: boolean;
        type: ("modules");
        supportedModules: Module[];
    }

    export interface Module {
        name: string;
        description: string;
        shortDescription: string;
        dependsOn: string[][];
        documentationUrl?: string | null;
    }
}

export type ApplicationParameter =
    ApplicationParameterNS.InputFile
    | ApplicationParameterNS.InputDirectory
    | ApplicationParameterNS.Text
    | ApplicationParameterNS.TextArea
    | ApplicationParameterNS.Integer
    | ApplicationParameterNS.FloatingPoint
    | ApplicationParameterNS.Bool
    | ApplicationParameterNS.Enumeration
    | ApplicationParameterNS.Peer
    | ApplicationParameterNS.Ingress
    | ApplicationParameterNS.LicenseServer
    | ApplicationParameterNS.NetworkIP
    | ApplicationParameterNS.Workflow
    | ApplicationParameterNS.Readme
    | ApplicationParameterNS.ModuleList

export interface VncDescription {
    password?: string;
    port: number;
}

export interface WebDescription {
    port: number;
}

export interface SshDescription {
    mode: "DISABLED" | "OPTIONAL" | "MANDATORY";
}

export interface ContainerDescription {
    changeWorkingDirectory: boolean;
    runAsRoot: boolean;
    runAsRealUser: boolean;
}

export type ApplicationWithFavoriteAndTags = Application;

export interface DetailedEntityWithPermission {
    entity: DetailedAccessEntity;
    permission: ("LAUNCH");
}

export interface DetailedAccessEntity {
    user?: string;
    project?: Project;
    group?: Project;
}

export interface Project {
    id: string;
    title: string;
}

export interface ACLEntryRequest {
    entity: AccessEntity;
    rights: ("LAUNCH");
    revoke: boolean;
}

export interface AccessEntity {
    user?: string;
    project?: string;
    group?: string;
}

export interface ApplicationWithExtension {
    metadata: ApplicationMetadata;
    extensions: string[];
}

export type ApplicationSummaryWithFavorite = Application;

export interface ApplicationGroup {
    metadata: ApplicationGroupMetadata;
    specification: ApplicationGroupSpecification;
    status: ApplicationGroupStatus;
}

export interface ApplicationGroupMetadata {
    id: number;
}

export interface ApplicationGroupSpecification {
    title: string;
    description: string;
    defaultFlavor?: string | null;
    categories: number[];
    logoHasText?: boolean;
}

export interface ApplicationGroupStatus {
    applications?: Application[] | null;
}

export interface ApplicationCategory {
    metadata: ApplicationCategoryMetadata;
    specification: ApplicationCategorySpecification;
    status: ApplicationCategoryStatus;
}

export interface ApplicationCategoryMetadata {
    id: number;
}

export interface ApplicationCategorySpecification {
    title: string;
    description?: string | null;
}

export interface ApplicationCategoryStatus {
    groups?: ApplicationGroup[] | null;
}

// Tool API
// =====================================================================================================================
const toolContext = "/api/hpc/tools";

export function findToolByName(request: {
    appName: string;
    itemsPerPage: number;
    page: number;
}): APICallParameters<unknown, Page<Tool>> {
    return {
        context: "",
        method: "GET",
        reloadId: Math.random(),
        path: buildQueryString(`${toolContext}/byName`, request),
    };
}

export function findToolByNameAndVersion(request: {
    name: string;
    version: string;
}): APICallParameters<unknown, Tool> {
    return {
        context: "",
        method: "GET",
        reloadId: Math.random(),
        path: buildQueryString(`${toolContext}/byNameAndVersion`, request),
    }
}

export function createTool(file: File): Promise<{ error?: string }> {
    return uploadFile("PUT", toolContext, file);
}

// Core API
// =====================================================================================================================
export function findByNameAndVersion(request: {
    appName: string;
    appVersion?: string | null;
}): APICallParameters<unknown, ApplicationWithFavoriteAndTags> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString(`${baseContext}/byNameAndVersion`, request),
        reloadId: Math.random(),
    };
}

export interface ApplicationFlags {
    // If categories are requested, should the groups in the categories be included?
    includeGroups?: boolean | null;

    // If groups are included, should the applications in the groups be included?
    includeApplications?: boolean | null;

    // If an application is included, should the star status be included?
    includeStars?: boolean | null;

    // If an application is included, should the invocation be included?
    includeInvocation?: boolean | null;

    // If an application is included, should the invocation be included?
    includeVersions?: boolean | null;
}

export function findGroupByApplication(request: {
    appName: string;
    appVersion?: string | null;
    flags?: ApplicationFlags
} & CatalogDiscovery): APICallParameters<unknown, ApplicationGroup> {
    return apiUpdate(request, baseContext, "findGroupByApplication");
}

export function create(file: File): Promise<{ error?: string }> {
    return uploadFile("PUT", baseContext, file);
}

async function uploadFile(method: string, path: string, file: File, headers?: Record<string, string>): Promise<{
    error?: string
}> {
    const token = await Client.receiveAccessTokenOrRefreshIt();

    const actualHeaders: Record<string, string> = {...(headers ?? {})};
    actualHeaders["Authorization"] = `Bearer ${token}`;
    const projectId = getStoredProject();
    if (projectId) actualHeaders["Project"] = projectId;

    const response = await fetch(Client.computeURL("", path), {
        method: method,
        headers: actualHeaders,
        body: file,
    });

    if (!response.ok) {
        const text = await response.text();
        let message: string;
        try {
            message = JSON.parse(text).why;
        } catch (e) {
            message = "Upload failed: " + text;
            console.log(e, text);
        }

        return {error: message};
    } else {
        return {};
    }
}

export function search(request: {
    query: string
} & PaginationRequestV2 & CatalogDiscovery): APICallParameters<unknown, PageV2<ApplicationSummaryWithFavorite>> {
    return apiSearch(request, baseContext);
}

export function browseOpenWithRecommendations(request: {
    files: string[];
} & PaginationRequestV2): APICallParameters<unknown, PageV2<ApplicationWithExtension>> {
    return apiUpdate(request, baseContext, "openWith");
}

// Application management
// =================================================================================================================
export function updateApplicationFlavor(request: {
    applicationName: string;
    flavorName: string | null;
}): APICallParameters<unknown, unknown> {
    return apiUpdate(request, baseContext, "updateApplicationFlavor");
}

export function retrieveAcl(request: {
    name: string;
}): APICallParameters<unknown, { entries: DetailedEntityWithPermission[] }> {
    return apiRetrieve(request, baseContext, "acl");
}

export function updateAcl(request: {
    name: string;
    changes: ACLEntryRequest[];
}): APICallParameters<unknown, unknown> {
    return apiUpdate(request, baseContext, "updateAcl");
}

export function updatePublicFlag(request: {
    name: string;
    version: string;
    public: boolean;
}): APICallParameters<unknown, unknown> {
    return apiUpdate(request, baseContext, "updatePublicFlag");
}

export function listAllApplications(request: {}): APICallParameters<unknown, { items: NameAndVersion[] }> {
    return apiRetrieve(request, baseContext, "allApplications");
}

// Starred applications
// =================================================================================================================
export function toggleStar(request: { name: string }): APICallParameters<unknown, unknown> {
    return apiUpdate(request, baseContext, "toggleStar");
}

export function retrieveStars(request: CatalogDiscovery): APICallParameters<unknown, { items: Application[] }> {
    return apiRetrieve(request, baseContext, "stars");
}

// Group management
// =================================================================================================================
export function createGroup(request: ApplicationGroupSpecification): APICallParameters<unknown, { id: number }> {
    return apiUpdate(request, baseContext, "createGroup");
}

export function retrieveGroup(request: { id: number } & CatalogDiscovery): APICallParameters<unknown, ApplicationGroup> {
    return apiRetrieve(request, baseContext, "groups");
}

export function retrieveStudioGroup(request: { id: number } & CatalogDiscovery): APICallParameters<unknown, ApplicationGroup> {
    return apiRetrieve(request, baseContext, "studioGroups");
}

export function browseGroups(request: PaginationRequestV2): APICallParameters<unknown, PageV2<ApplicationGroup>> {
    return apiBrowse(request, baseContext, "groups");
}

export function updateGroup(request: {
    id: number;
    newTitle?: string | null;
    newDefaultFlavor?: string | null;
    newDescription?: string | null;
    newBackgroundColor?: string | null;
    newLogoHasText?: boolean;
}): APICallParameters<unknown, unknown> {
    return apiUpdate(request, baseContext, "updateGroup");
}

export function deleteGroup(request: { id: number }): APICallParameters<unknown, unknown> {
    return apiUpdate(request, baseContext, "deleteGroup");
}

export function addLogoToGroup(groupId: number, logo: File): Promise<{ error?: string }> {
    updateAppLogoCacheInvalidationParameter();
    return uploadFile("POST", `${baseContext}/uploadLogo`, logo, {"Upload-Name": b64EncodeUnicode(groupId.toString())});
}

export function removeLogoFromGroup(request: { id: number }): APICallParameters<unknown, unknown> {
    return apiUpdate(request, baseContext, "removeLogoFromGroup");
}

export function retrieveGroupLogo(request: {
    id: number;
    darkMode?: boolean;
    includeText?: boolean;
    placeTextUnderLogo?: boolean;
}): string {
    const updatedRequest = {...request};
    updatedRequest["cacheBust"] = getAppLogoCacheInvalidationParameter();
    return buildQueryString(`${baseContext}/retrieveGroupLogo`, updatedRequest);
}

export function retrieveAppLogo(request: {
    name: string;
    darkMode?: boolean;
    includeText?: boolean;
    placeTextUnderLogo?: boolean;
}): string {
    const updatedRequest = {...request};
    updatedRequest["cacheBust"] = getAppLogoCacheInvalidationParameter();
    return buildQueryString(`${baseContext}/retrieveAppLogo`, updatedRequest);
}

let appLogoCacheInvalidationParameter: number | null = null;

function getAppLogoCacheInvalidationParameter(): number {
    if (appLogoCacheInvalidationParameter === null) {
        const currentValue = localStorage.getItem("app-logo-cache-bust");
        if (currentValue === null) {
            appLogoCacheInvalidationParameter = 0;
            return 0;
        } else {
            let value = parseInt(currentValue);
            if (isNaN(value)) {
                value = 0;
            }
            appLogoCacheInvalidationParameter = value;
            return value;
        }
    } else {
        return appLogoCacheInvalidationParameter;
    }
}

export function updateAppLogoCacheInvalidationParameter() {
    const newValue = getAppLogoCacheInvalidationParameter() + 1;
    appLogoCacheInvalidationParameter = newValue;
    localStorage.setItem("app-logo-cache-bust", newValue.toString());
}

export function assignApplicationToGroup(request: {
    name: string;
    group?: number | null;
}): APICallParameters<unknown, unknown> {
    return apiUpdate(request, baseContext, "assignApplicationToGroup");
}

// Category management
// =================================================================================================================
export function createCategory(request: ApplicationCategorySpecification): APICallParameters<unknown, { id: number }> {
    return apiUpdate(request, baseContext, "createCategory");
}

export function browseStudioCategories(request: PaginationRequestV2): APICallParameters<unknown, PageV2<ApplicationCategory>> {
    return apiBrowse(request, baseContext, "categories");
}

export function retrieveCategory(request: FindByLongId & CatalogDiscovery): APICallParameters<unknown, ApplicationCategory> {
    return apiRetrieve(request, baseContext, "category");
}

export function addGroupToCategory(request: {
    groupId: number;
    categoryId: number;
}): APICallParameters<unknown, unknown> {
    return apiUpdate(request, baseContext, "addGroupToCategory");
}

export function removeGroupFromCategory(request: {
    groupId: number;
    categoryId: number;
}): APICallParameters<unknown, unknown> {
    return apiUpdate(request, baseContext, "removeGroupFromCategory");
}

export function assignPriorityToCategory(request: {
    id: number,
    priority: number
}): APICallParameters<unknown, unknown> {
    return apiUpdate(request, baseContext, "assignPriorityToCategory");
}

export function deleteCategory(request: { id: number }): APICallParameters<unknown, unknown> {
    return apiUpdate(request, baseContext, "deleteCategory");
}

// Spotlight management
// =================================================================================================================
export function createSpotlight(request: Spotlight): APICallParameters<unknown, FindByLongId> {
    return apiUpdate(request, baseContext, "createSpotlight");
}

export function updateSpotlight(request: Spotlight): APICallParameters<unknown, unknown> {
    return apiUpdate(request, baseContext, "updateSpotlight");
}

export function deleteSpotlight(request: { id: number }): APICallParameters<unknown, unknown> {
    return apiUpdate(request, baseContext, "deleteSpotlight");
}

export function retrieveSpotlight(request: { id: number }): APICallParameters<unknown, Spotlight> {
    return apiRetrieve(request, baseContext, "spotlight");
}

export function browseSpotlights(request: PaginationRequestV2): APICallParameters<unknown, PageV2<Spotlight>> {
    return apiBrowse(request, baseContext, "spotlight");
}

export function activateSpotlight(request: { id: number }): APICallParameters<unknown, unknown> {
    return apiUpdate(request, baseContext, "activateSpotlight");
}

// Carrousel management
// =================================================================================================================
export function updateCarrousel(request: { newSlides: CarrouselItem[] }): APICallParameters<unknown, unknown> {
    return apiUpdate(request, baseContext, "updateCarrousel");
}

export function updateCarrouselImage(slideIndex: number, image: File) {
    return uploadFile("POST", `${baseContext}/updateCarrouselImage`, image, {"Slide-Index": b64EncodeUnicode(slideIndex.toString())});
}

// Top picks management
// =================================================================================================================
export function updateTopPicks(request: { newTopPicks: TopPick[] }): APICallParameters<unknown, unknown> {
    return apiUpdate(request, baseContext, "updateTopPicks");
}

// Landing page
// =================================================================================================================
export interface LandingPage {
    carrousel: CarrouselItem[];
    topPicks: TopPick[]
    categories: ApplicationCategory[];
    spotlight?: Spotlight | null;
    newApplications: ApplicationSummaryWithFavorite[];
    recentlyUpdated: ApplicationSummaryWithFavorite[];
    availableProviders: string[];
}

export const emptyLandingPage: LandingPage = {
    carrousel: [],
    topPicks: [],
    categories: [],
    newApplications: [],
    recentlyUpdated: [],
    availableProviders: [],
};

// Import/export features
// =================================================================================================================
export async function doExport(): Promise<string> {
    const token = await Client.receiveAccessTokenOrRefreshIt();

    const actualHeaders: Record<string, string> = {};
    actualHeaders["Authorization"] = `Bearer ${token}`;

    const response = await fetch(Client.computeURL("/", `${baseContext}/export`), {
        method: "POST",
        headers: actualHeaders,
    });
    const blob = await response.blob();
    return URL.createObjectURL(blob);
}

export async function doImport(file: File) {
    return uploadFile("POST", `${baseContext}/importFromFile`, file);
}

export interface CarrouselItem {
    title: string;
    body: string;
    imageCredit: string;
    linkedApplication?: string | null;
    linkedWebPage?: string | null;
    linkedGroup?: number | null;
    resolvedLinkedApp?: string | null;
}

export interface TopPick {
    title: string;
    applicationName?: string | null
    groupId?: number | null;
    description: string;
    defaultApplicationToRun?: string | null;
    logoHasText?: boolean;
    logoBackgroundColor?: string;
}

export interface Spotlight {
    title: string;
    body: string;
    applications: TopPick[];
    active: boolean;
    id?: number | null;
}

export function retrieveLandingPage(request: CatalogDiscovery): APICallParameters<unknown, LandingPage> {
    return apiRetrieve(request, baseContext, "landingPage");
}

export function retrieveCarrouselImage(request: {
    index: number;
    slideTitle: string;
}): string {
    return buildQueryString(`${baseContext}/retrieveCarrouselImage`, request);
}

export function findStudioApplication(request: { name: string }): APICallParameters<unknown, {
    versions: Application[]
}> {
    return apiRetrieve(request, baseContext, "studioApplication");
}

export enum CatalogDiscoveryMode {
    ALL = "ALL",
    AVAILABLE = "AVAILABLE",
    SELECTED = "SELECTED"
}

export interface CatalogDiscovery {
    discovery?: CatalogDiscoveryMode;
    selected?: string;
}

export const defaultCatalogDiscovery: CatalogDiscovery = {
    discovery: CatalogDiscoveryMode.ALL,
};
