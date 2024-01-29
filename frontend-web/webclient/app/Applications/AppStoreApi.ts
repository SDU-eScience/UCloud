import {buildQueryString} from "@/Utilities/URIUtilities";
import {apiBrowse, apiRetrieve, apiSearch, apiUpdate} from "@/Authentication/DataHook";
import {Client} from "@/Authentication/HttpClientInstance";
import {inSuccessRange} from "@/UtilityFunctions";
import {FindByLongId} from "@/UCloud";
import {b64EncodeUnicode} from "@/Utilities/XHRUtils";

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
    group?: ApplicationGroup
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

export interface ApplicationWithFavoriteAndTags {
    metadata: ApplicationMetadata;
    invocation: ApplicationInvocationDescription;
    favorite: boolean;
    tags: string[];
}

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

export interface ApplicationSummaryWithFavorite {
    metadata: ApplicationMetadata;
    favorite: boolean;
    tags: string[];
}

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
}

export interface ApplicationGroupStatus {
    applications?: ApplicationWithFavoriteAndTags[] | null;
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
export function findByName(request: {
    appName: string;
    itemsPerPage?: number;
    page?: number;
}): APICallParameters<unknown, Page<ApplicationSummaryWithFavorite>> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString(`${baseContext}/byName`, request),
        reloadId: Math.random(),
    };
}

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

export function create(file: File): Promise<{ error?: string }> {
    return uploadFile("PUT", baseContext, file);
}

async function uploadFile(method: string, path: string, file: File, headers?: Record<string, string>): Promise<{ error?: string }> {
    const token = await Client.receiveAccessTokenOrRefreshIt();

    const actualHeaders: Record<string, string> = {...(headers ?? {})};
    actualHeaders["Authorization"] = `Bearer ${token}`;

    const response = await fetch(Client.computeURL("/", path), {
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

        return { error: message };
    } else {
        return {};
    }
}

export function search(request: {
    query: string
} & PaginationRequestV2): APICallParameters<unknown, PageV2<ApplicationSummaryWithFavorite>> {
    return apiSearch(request, baseContext);
}

export function browseOpenWithRecommendations(request: {
    files: string[];
} & PaginationRequestV2): APICallParameters<unknown, PageV2<ApplicationWithExtension>> {
    return apiBrowse(request, baseContext, "openWith");
}

// Application management
// =================================================================================================================
export function updateApplicationFlavor(request: {
    applicationName: string;
    flavorName: string;
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

export function retrieveStars(request: {}): APICallParameters<unknown, { items: ApplicationSummaryWithFavorite[] }> {
    return apiRetrieve(request, baseContext, "stars");
}

// Group management
// =================================================================================================================
export function createGroup(request: ApplicationGroupSpecification): APICallParameters<unknown, { id: number }> {
    return apiUpdate(request, baseContext, "createGroup");
}

export function retrieveGroup(request: { id: number }): APICallParameters<unknown, ApplicationGroup> {
    return apiRetrieve(request, baseContext, "groups");
}

export function browseGroups(request: PaginationRequestV2): APICallParameters<unknown, PageV2<ApplicationGroup>> {
    return apiBrowse(request, baseContext, "groups");
}

export function updateGroup(request: {
    id: number;
    newTitle?: string | null;
    newDefaultFlavor?: string | null;
    newDescription?: string | null;
}): APICallParameters<unknown, unknown> {
    return apiUpdate(request, baseContext, "updateGroup");
}

export function deleteGroup(request: { id: number }): APICallParameters<unknown, unknown> {
    return apiUpdate(request, baseContext, "deleteGroup");
}

export function addLogoToGroup(groupId: number, logo: File): Promise<{ error?: string }> {
    return uploadFile("POST", `${baseContext}/uploadLogo`, logo, { "Upload-Name": b64EncodeUnicode(groupId.toString()) });
}

export function removeLogoFromGroup(request: { id: number }): APICallParameters<unknown, unknown> {
    return apiUpdate(request, baseContext, "removeLogoFromGroup");
}

export function retrieveGroupLogo(request: { id: number }): string {
    return buildQueryString(`${baseContext}/retrieveGroupLogo`, request);
}

export function retrieveAppLogo(request: { name: string }): string {
    return buildQueryString(`${baseContext}/retrieveAppLogo`, request);
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

export function browseCategories(request: PaginationRequestV2): APICallParameters<unknown, PageV2<ApplicationCategory>> {
    return apiBrowse(request, baseContext, "categories");
}

export function retrieveCategory(request: FindByLongId): APICallParameters<unknown, ApplicationCategory> {
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

// Landing page
// =================================================================================================================
export interface LandingPage {
    carrousel: CarrouselItem[];
    topPicks: TopPick[]
    categories: ApplicationCategory[];
    spotlight?: Spotlight | null;
    newApplications: ApplicationSummaryWithFavorite[];
    recentlyUpdated: ApplicationSummaryWithFavorite[];
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
}

export interface Spotlight {
    title: string;
    body: string;
    applications: TopPick[];
    active: boolean;
}

export function retrieveLandingPage(request: {}): APICallParameters<unknown, LandingPage> {
    return apiRetrieve(request, baseContext, "landingPage");
}

export function retrieveCarrouselImage(request: {
    index: number;
    slideTitle: string;
}): string {
    return buildQueryString(`${baseContext}/retrieveCarrouselImage`, request);
}
