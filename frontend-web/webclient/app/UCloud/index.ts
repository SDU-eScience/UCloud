/* eslint-disable */
/* AUTO GENERATED CODE - DO NOT MODIFY */
/* Generated at: Thu Jan 07 12:14:57 CET 2021 */

import {buildQueryString} from "Utilities/URIUtilities";

/**
 * Generic error message
 */
export interface CommonErrorMessage {
    /**
     * Human readable description of why the error occurred This value is generally not stable
     */
    why: string,
    /**
     * Machine readable description of why the error occurred This value is stable and can be relied upon
     */
    errorCode?: string,
}

export interface Page<T> {
    itemsInTotal: number /* int32 */
    ,
    itemsPerPage: number /* int32 */
    ,
    pageNumber: number /* int32 */
    ,
    items: T[],
    pagesInTotal: number /* int32 */
    ,
}

export interface PageV2<T> {
    itemsPerPage: number /* int32 */
    ,
    items: T[],
    next?: string,
}

export type BulkRequest<T> = T | { type: "bulk", items: T[] }

export interface FindByStringId {
    id: string,
}

export interface PaginationRequest {
    itemsPerPage?: number /* int32 */
    ,
    page?: number /* int32 */
    ,
}

export interface BinaryStream {
}

export interface FindByLongId {
    id: number /* int64 */
    ,
}

export namespace mail {
    export function send(
        request: SendRequest
    ): APICallParameters<SendRequest, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/api/mail",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function sendSupport(
        request: SendSupportEmailRequest
    ): APICallParameters<SendSupportEmailRequest, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/api/mail" + "/support",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function sendBulk(
        request: SendBulkRequest
    ): APICallParameters<SendBulkRequest, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/api/mail" + "/bulk",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export interface SendRequest {
        userId: string,
        subject: string,
        message: string,
        mandatory?: boolean,
    }

    export interface SendSupportEmailRequest {
        fromEmail: string,
        subject: string,
        message: string,
    }

    export interface SendBulkRequest {
        messages: SendRequest[],
    }
}
export namespace auth {
    export function passwordLogin(): APICallParameters<{}, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/auth" + "/login",
            reloadId: Math.random(),
        };
    }

    export function refresh(): APICallParameters<{}, AccessToken> {
        return {
            context: "",
            method: "POST",
            path: "/auth" + "/refresh",
            reloadId: Math.random(),
        };
    }

    export function webRefresh(): APICallParameters<{}, AccessTokenAndCsrf> {
        return {
            context: "",
            method: "POST",
            path: "/auth" + "/refresh" + "/web",
            reloadId: Math.random(),
        };
    }

    export function tokenExtension(
        request: TokenExtensionRequest
    ): APICallParameters<TokenExtensionRequest, OptionalAuthenticationTokens> {
        return {
            context: "",
            method: "POST",
            path: "/auth" + "/extend",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function requestOneTimeTokenWithAudience(
        request: RequestOneTimeToken
    ): APICallParameters<RequestOneTimeToken, OneTimeAccessToken> {
        return {
            context: "",
            method: "POST",
            path: buildQueryString("/auth" + "/request", {audience: request.audience}),
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export function claim(
        request: ClaimOneTimeToken
    ): APICallParameters<ClaimOneTimeToken, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/auth" + "/claim" + "/" + request.jti,
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export function logout(): APICallParameters<{}, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/auth" + "/logout",
            reloadId: Math.random(),
        };
    }

    export function webLogout(): APICallParameters<{}, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/auth" + "/logout" + "/web",
            reloadId: Math.random(),
        };
    }

    export function bulkInvalidate(
        request: BulkInvalidateRequest
    ): APICallParameters<BulkInvalidateRequest, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/auth" + "/logout" + "/bulk",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function listUserSessions(
        request: ListUserSessionsRequest
    ): APICallParameters<ListUserSessionsRequest, Page<Session>> {
        return {
            context: "",
            method: "GET",
            path: buildQueryString("/auth" + "/sessions", {itemsPerPage: request.itemsPerPage, page: request.page}),
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export function invalidateSessions(): APICallParameters<{}, any /* unknown */> {
        return {
            context: "",
            method: "DELETE",
            path: "/auth" + "/sessions",
            reloadId: Math.random(),
        };
    }

    export interface AccessToken {
        accessToken: string,
    }

    export interface AccessTokenAndCsrf {
        accessToken: string,
        csrfToken: string,
    }

    export interface OptionalAuthenticationTokens {
        accessToken: string,
        csrfToken?: string,
        refreshToken?: string,
    }

    export interface TokenExtensionRequest {
        validJWT: string,
        requestedScopes: string[],
        expiresIn: number /* int64 */
        ,
        allowRefreshes: boolean,
    }

    export interface OneTimeAccessToken {
        accessToken: string,
        jti: string,
    }

    export interface RequestOneTimeToken {
        audience: string,
    }

    export interface ClaimOneTimeToken {
        jti: string,
    }

    export interface BulkInvalidateRequest {
        tokens: string[],
    }

    export interface AuthenticationTokens {
        accessToken: string,
        refreshToken: string,
        csrfToken: string,
    }

    export interface CreateSingleUserRequest {
        username: string,
        password?: string,
        email?: string,
        role?: "GUEST" | "USER" | "ADMIN" | "SERVICE" | "THIRD_PARTY_APP" | "PROVIDER" | "UNKNOWN",
    }

    export interface UpdateUserInfoRequest {
        email?: string,
        firstNames?: string,
        lastName?: string,
    }

    export interface GetUserInfoResponse {
        email?: string,
        firstNames?: string,
        lastName?: string,
    }

    export interface ChangePasswordRequest {
        currentPassword: string,
        newPassword: string,
    }

    export interface ChangePasswordWithResetRequest {
        userId: string,
        newPassword: string,
    }

    export interface LookupUsersResponse {
        results: Record<string, UserLookup>,
    }

    export interface UserLookup {
        subject: string,
        uid: number /* int64 */
        ,
        role: "GUEST" | "USER" | "ADMIN" | "SERVICE" | "THIRD_PARTY_APP" | "PROVIDER" | "UNKNOWN",
    }

    export interface LookupUsersRequest {
        users: string[],
    }

    export interface LookupEmailResponse {
        email: string,
    }

    export interface LookupEmailRequest {
        userId: string,
    }

    export interface LookupUserWithEmailResponse {
        userId: string,
        firstNames: string,
    }

    export interface LookupUserWithEmailRequest {
        email: string,
    }

    export interface WantsEmailsRequest {
        username?: string,
    }

    export interface LookupUIDResponse {
        users: Record<string, UserLookup>,
    }

    export interface LookupUIDRequest {
        uids: number /* int64 */[],
    }

    export type Principal = PersonNS.ByWAYF | PersonNS.ByPassword | ServicePrincipal

    export interface ServicePrincipal {
        id: string,
        role: "GUEST" | "USER" | "ADMIN" | "SERVICE" | "THIRD_PARTY_APP" | "PROVIDER" | "UNKNOWN",
        uid: number /* int64 */
        ,
        emailAddresses: string[],
        preferredEmailAddress?: string,
        type: "service",
    }

    export interface Create2FACredentialsResponse {
        otpAuthUri: string,
        qrCodeB64Data: string,
        secret: string,
        challengeId: string,
    }

    export interface AnswerChallengeRequest {
        challengeId: string,
        verificationCode: number /* int32 */
        ,
    }

    export interface TwoFactorStatusResponse {
        connected: boolean,
    }

    export interface Session {
        ipAddress: string,
        userAgent: string,
        createdAt: number /* int64 */
        ,
    }

    export interface ListUserSessionsRequest {
        itemsPerPage?: number /* int32 */
        ,
        page?: number /* int32 */
        ,
    }

    export interface ServiceAgreementText {
        version: number /* int32 */
        ,
        text: string,
    }

    export interface AcceptSLARequest {
        version: number /* int32 */
        ,
    }

    export namespace users {
        export function createNewUser(
            request: CreateSingleUserRequest[]
        ): APICallParameters<CreateSingleUserRequest[], AuthenticationTokens[]> {
            return {
                context: "",
                method: "POST",
                path: "/auth/users" + "/register",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function updateUserInfo(
            request: UpdateUserInfoRequest
        ): APICallParameters<UpdateUserInfoRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/auth/users" + "/updateUserInfo",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function getUserInfo(): APICallParameters<{}, GetUserInfoResponse> {
            return {
                context: "",
                method: "GET",
                path: "/auth/users" + "/userInfo",
                reloadId: Math.random(),
            };
        }

        export function changePassword(
            request: ChangePasswordRequest
        ): APICallParameters<ChangePasswordRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/auth/users" + "/password",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function changePasswordWithReset(
            request: ChangePasswordWithResetRequest
        ): APICallParameters<ChangePasswordWithResetRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/auth/users" + "/password" + "/reset",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function lookupUsers(
            request: LookupUsersRequest
        ): APICallParameters<LookupUsersRequest, LookupUsersResponse> {
            return {
                context: "",
                method: "POST",
                path: "/auth/users" + "/lookup",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function lookupEmail(
            request: LookupEmailRequest
        ): APICallParameters<LookupEmailRequest, LookupEmailResponse> {
            return {
                context: "",
                method: "POST",
                path: "/auth/users" + "/lookup" + "/email",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function lookupUserWithEmail(
            request: LookupUserWithEmailRequest
        ): APICallParameters<LookupUserWithEmailRequest, LookupUserWithEmailResponse> {
            return {
                context: "",
                method: "POST",
                path: "/auth/users" + "/lookup" + "/with-email",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function toggleEmailSubscription(): APICallParameters<{}, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/auth/users" + "/toggleEmailSubscription",
                reloadId: Math.random(),
            };
        }

        export function wantsEmails(
            request: WantsEmailsRequest
        ): APICallParameters<WantsEmailsRequest, boolean> {
            return {
                context: "",
                method: "POST",
                path: "/auth/users" + "/wantsEmails",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function lookupUID(
            request: LookupUIDRequest
        ): APICallParameters<LookupUIDRequest, LookupUIDResponse> {
            return {
                context: "",
                method: "POST",
                path: "/auth/users" + "/lookup-uid",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function openUserIterator(): APICallParameters<{}, FindByStringId> {
            return {
                context: "",
                method: "POST",
                path: "/auth/users" + "/iterator" + "/open",
                reloadId: Math.random(),
            };
        }

        export function fetchNextIterator(
            request: FindByStringId
        ): APICallParameters<FindByStringId, Principal[]> {
            return {
                context: "",
                method: "POST",
                path: "/auth/users" + "/iterator" + "/next",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function closeIterator(
            request: FindByStringId
        ): APICallParameters<FindByStringId, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/auth/users" + "/iterator" + "/close",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }
    }
    export namespace sla {
        export function find(): APICallParameters<{}, ServiceAgreementText> {
            return {
                context: "",
                method: "GET",
                path: "/api/sla",
                reloadId: Math.random(),
            };
        }

        export function accept(
            request: AcceptSLARequest
        ): APICallParameters<AcceptSLARequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/sla" + "/accept",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }
    }
    export namespace twofactor {
        export function createCredentials(): APICallParameters<{}, Create2FACredentialsResponse> {
            return {
                context: "",
                method: "POST",
                path: "/auth/2fa",
                reloadId: Math.random(),
            };
        }

        export function answerChallenge(
            request: AnswerChallengeRequest
        ): APICallParameters<AnswerChallengeRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/auth/2fa" + "/challenge",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function twoFactorStatus(): APICallParameters<{}, TwoFactorStatusResponse> {
            return {
                context: "",
                method: "GET",
                path: "/auth/2fa" + "/status",
                reloadId: Math.random(),
            };
        }
    }
    export namespace PersonNS {
        export interface ByWAYF {
            id: string,
            role: "GUEST" | "USER" | "ADMIN" | "SERVICE" | "THIRD_PARTY_APP" | "PROVIDER" | "UNKNOWN",
            title?: string,
            firstNames: string,
            lastName: string,
            phoneNumber?: string,
            orcId?: string,
            email?: string,
            uid: number /* int64 */
            ,
            serviceLicenseAgreement: number /* int32 */
            ,
            wantsEmails?: boolean,
            organizationId: string,
            wayfId: string,
            displayName: string,
            twoFactorAuthentication: boolean,
            emailAddresses: string[],
            preferredEmailAddress?: string,
            type: "wayf",
        }

        export interface ByPassword {
            id: string,
            role: "GUEST" | "USER" | "ADMIN" | "SERVICE" | "THIRD_PARTY_APP" | "PROVIDER" | "UNKNOWN",
            title?: string,
            firstNames: string,
            lastName: string,
            phoneNumber?: string,
            orcId?: string,
            email?: string,
            uid: number /* int64 */
            ,
            twoFactorAuthentication: boolean,
            serviceLicenseAgreement: number /* int32 */
            ,
            wantsEmails?: boolean,
            displayName: string,
            emailAddresses: string[],
            preferredEmailAddress?: string,
            type: "password",
        }
    }
}
export namespace filesearch {
    export function advancedSearch(
        request: AdvancedSearchRequest
    ): APICallParameters<AdvancedSearchRequest, Page<file.StorageFile>> {
        return {
            context: "",
            method: "POST",
            path: "/api/file-search" + "/advanced",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export interface AdvancedSearchRequest {
        fileName?: string,
        extensions?: string[],
        fileTypes?: "FILE" | "DIRECTORY"[],
        includeShares?: boolean,
        itemsPerPage?: number /* int32 */
        ,
        page?: number /* int32 */
        ,
    }
}
export namespace compute {
    export interface Job {
        /**
         * Unique identifier for this job.
         *
         * UCloud guarantees that no other job, regardless of compute provider, has the same unique identifier.
         */
        id: string,
        /**
         * A reference to the owner of this job
         */
        owner: JobOwner,
        /**
         * A list of status updates from the compute backend.
         *
         * The status updates tell a story of what happened with the job. This list is ordered by the timestamp in ascending order. The current state of the job will always be the last element. `updates` is guaranteed to always contain at least one element.
         */
        updates: JobUpdate[],
        billing: JobBilling,
        /**
         * The parameters used to launch this job.
         *
         * This property is always available but must be explicitly requested.
         */
        parameters: JobParameters,
        /**
         * A summary of the `Job`'s current status
         */
        status: JobStatus,
        /**
         * Information regarding the output of this job.
         */
        output?: JobOutput,
    }

    export interface JobOwner {
        /**
         * The username of the user which started the job
         */
        launchedBy: string,
        /**
         * The project ID of the project which owns this job
         *
         * This value can be null and this signifies that the job belongs to the personal workspace of the user.
         */
        project?: string,
    }

    export interface JobUpdate {
        timestamp: number /* int64 */
        ,
        state?: "IN_QUEUE" | "RUNNING" | "CANCELING" | "SUCCESS" | "FAILURE" | "EXPIRED",
        status?: string,
    }

    export interface JobBilling {
        /**
         * The amount of credits charged to the `owner` of this job
         */
        creditsCharged: number /* int64 */
        ,
        /**
         * The unit price of this job
         */
        pricePerUnit: number /* int64 */
        ,
    }

    export interface JobParameters {
        /**
         * A reference to the application which this job should execute
         */
        application: NameAndVersion,
        /**
         * A reference to the product that this job will be executed on
         */
        product: accounting.ProductReference,
        /**
         * A name for this job assigned by the user.
         *
         * The name can help a user identify why and with which parameters a job was started. This value is suitable for display in user interfaces.
         */
        name?: string,
        /**
         * The number of replicas to start this job in
         *
         * The `resources` supplied will be mounted in every replica. Some `resources` might only be supported in an 'exclusive use' mode. This will cause the job to fail if `replicas != 1`.
         */
        replicas: number /* int32 */
        ,
        /**
         * Allows the job to be started even when a job is running in an identical configuration
         *
         * By default, UCloud will prevent you from accidentally starting two jobs with identical configuration. This field must be set to `true` to allow you to create two jobs with identical configuration.
         */
        allowDuplicateJob: boolean,
        /**
         * Parameters which are consumed by the job
         *
         * The available parameters are defined by the `application`. This attribute is not included by default unless `includeParameters` is specified.
         */
        parameters?: Record<string, AppParameterValue>,
        /**
         * Additional resources which are made available into the job
         *
         * This attribute is not included by default unless `includeParameters` is specified. Note: Not all resources can be attached to a job. UCloud supports the following parameter types as resources:
         *
         *  - `file`
         *  - `peer`
         *  - `network`
         *  - `block_storage`
         *  - `ingress`
         *
         */
        resources?: AppParameterValue[],
        /**
         * Time allocation for the job
         *
         * This value can be `null` which signifies that the job should not (automatically) expire. Note that some providers do not support `null`. When this value is not `null` it means that the job will be terminated, regardless of result, after the duration has expired. Some providers support extended this duration via the `extend` operation.
         */
        timeAllocation?: SimpleDuration,
        /**
         * The resolved product referenced by `product`.
         *
         * This attribute is not included by default unless `includeProduct` is specified.
         */
        resolvedProduct?: accounting.ProductNS.Compute,
        /**
         * The resolved application referenced by `application`.
         *
         * This attribute is not included by default unless `includeApplication` is specified.
         */
        resolvedApplication?: Application,
    }

    export interface NameAndVersion {
        name: string,
        version: string,
    }

    /**
     * A parameter supplied to a compute job
     */
    export type AppParameterValue =
        AppParameterValueNS.File
        | AppParameterValueNS.Bool
        | AppParameterValueNS.Text
        | AppParameterValueNS.Integer
        | AppParameterValueNS.FloatingPoint
        | AppParameterValueNS.Peer
        | AppParameterValueNS.License
        | AppParameterValueNS.Network
        | AppParameterValueNS.BlockStorage
        | AppParameterValueNS.Ingress

    export interface SimpleDuration {
        hours: number /* int32 */
        ,
        minutes: number /* int32 */
        ,
        seconds: number /* int32 */
        ,
    }

    export interface Application {
        metadata: ApplicationMetadata,
        invocation: ApplicationInvocationDescription,
    }

    export interface ApplicationMetadata {
        name: string,
        version: string,
        authors: string[],
        title: string,
        description: string,
        website?: string,
        public: boolean,
    }

    export interface ApplicationInvocationDescription {
        tool: ToolReference,
        invocation: InvocationParameter[],
        parameters: ApplicationParameter[],
        outputFileGlobs: string[],
        applicationType: "BATCH" | "VNC" | "WEB",
        vnc?: VncDescription,
        web?: WebDescription,
        container?: ContainerDescription,
        environment?: Record<string, InvocationParameter>,
        allowAdditionalMounts?: boolean,
        allowAdditionalPeers?: boolean,
        allowMultiNode: boolean,
        fileExtensions: string[],
        licenseServers: string[],
        shouldAllowAdditionalMounts: boolean,
        shouldAllowAdditionalPeers: boolean,
    }

    export interface ToolReference {
        name: string,
        version: string,
        tool?: Tool,
    }

    export interface Tool {
        owner: string,
        createdAt: number /* int64 */
        ,
        modifiedAt: number /* int64 */
        ,
        description: NormalizedToolDescription,
    }

    export interface NormalizedToolDescription {
        info: NameAndVersion,
        container: string,
        defaultNumberOfNodes: number /* int32 */
        ,
        defaultTimeAllocation: SimpleDuration,
        requiredModules: string[],
        authors: string[],
        title: string,
        description: string,
        backend: "SINGULARITY" | "DOCKER",
        license: string,
    }

    export type InvocationParameter =
        WordInvocationParameter
        | BooleanFlagParameter
        | VariableInvocationParameter
        | EnvironmentVariableParameter

    export interface WordInvocationParameter {
        word: string,
        type: "word",
    }

    export interface BooleanFlagParameter {
        variableName: string,
        flag: string,
        type: "bool_flag",
    }

    export interface VariableInvocationParameter {
        variableNames: string[],
        prefixGlobal: string,
        suffixGlobal: string,
        prefixVariable: string,
        suffixVariable: string,
        isPrefixVariablePartOfArg: boolean,
        isSuffixVariablePartOfArg: boolean,
        type: "var",
    }

    export interface EnvironmentVariableParameter {
        variable: string,
        type: "env",
    }

    export type ApplicationParameter =
        ApplicationParameterNS.InputFile
        | ApplicationParameterNS.InputDirectory
        | ApplicationParameterNS.Text
        | ApplicationParameterNS.Integer
        | ApplicationParameterNS.Bool
        | ApplicationParameterNS.FloatingPoint
        | ApplicationParameterNS.Peer
        | ApplicationParameterNS.Enumeration
        | ApplicationParameterNS.LicenseServer

    export interface VncDescription {
        password?: string,
        port: number /* int32 */
        ,
    }

    export interface WebDescription {
        port: number /* int32 */
        ,
    }

    export interface ContainerDescription {
        changeWorkingDirectory: boolean,
        runAsRoot: boolean,
        runAsRealUser: boolean,
    }

    export interface JobStatus {
        /**
         * The current of state of the `Job`.
         *
         * This will match the latest state set in the `updates`
         */
        state: "IN_QUEUE" | "RUNNING" | "CANCELING" | "SUCCESS" | "FAILURE" | "EXPIRED",
        /**
         * Timestamp matching when the `Job` most recently transitioned to the `RUNNING` state.
         *
         * For `Job`s which suspend this might occur multiple times. This will always point to the latest pointin time it started running.
         */
        startedAt?: number /* int64 */
        ,
        /**
         * Timestamp matching when the `Job` is set to expire.
         *
         * This is generally equal to `startedAt + timeAllocation`. Note that this field might be `null` if the `Job` has no associated deadline. For `Job`s that suspend however, this is more likely to beequal to the initial `RUNNING` state + `timeAllocation`.
         */
        expiresAt?: number /* int64 */
        ,
    }

    export interface JobOutput {
        outputFolder: string,
    }

    export interface ComputeExtendRequestItem {
        job: Job,
        requestedTime: SimpleDuration,
    }

    export interface ComputeOpenInteractiveSessionResponse {
        sessions: OpenSession[],
    }

    export type OpenSession = OpenSessionNS.Shell | OpenSessionNS.Web | OpenSessionNS.Vnc

    export interface ComputeOpenInteractiveSessionRequestItem {
        job: Job,
        rank: number /* int32 */
        ,
        sessionType: "WEB" | "VNC" | "SHELL",
    }

    export interface ProviderManifest {
        features: ManifestFeatureSupport,
    }

    export interface ManifestFeatureSupport {
        compute: ManifestFeatureSupportNS.Compute,
    }

    export interface ComputeUtilizationResponse {
        capacity: CpuAndMemory,
        usedCapacity: CpuAndMemory,
        queueStatus: QueueStatus,
    }

    export interface CpuAndMemory {
        cpu: number /* float64 */
        ,
        memory: number /* int64 */
        ,
    }

    export interface QueueStatus {
        running: number /* int32 */
        ,
        pending: number /* int32 */
        ,
    }

    /**
     * An L7 ingress-point (HTTP)
     */
    export interface Ingress {
        id: string,
        domain: string,
        product: accounting.ProductReference,
        /**
         * Information about the owner of this resource
         */
        owner: IngressOwner,
        /**
         * Information about when this resource was created
         */
        createdAt: number /* int64 */
        ,
        /**
         * The current status of this resource
         */
        status: IngressStatus,
        /**
         * Billing information associated with this `Ingress`
         */
        billing: IngressBilling,
        /**
         * A list of updates for this `Ingress`
         */
        updates: IngressUpdate[],
        resolvedProduct?: accounting.ProductNS.Ingress,
    }

    export interface IngressOwner {
        /**
         * The username of the user which created this resource.
         *
         * In cases where this user is removed from the project the ownership will be transferred to the current PI of the project.
         */
        username: string,
        /**
         * The project which owns the resource
         */
        project?: string,
    }

    /**
     * The status of an `Ingress`
     */
    export interface IngressStatus {
        /**
         * The ID of the `Job` that this `Ingress` is currently bound to
         */
        boundTo?: string,
        state: "PREPARING" | "READY" | "UNAVAILABLE",
    }

    export interface IngressBilling {
        pricePerUnit: number /* int64 */
        ,
        creditsCharged: number /* int64 */
        ,
    }

    export interface IngressUpdate {
        /**
         * A timestamp for when this update was registered by UCloud
         */
        timestamp: number /* int64 */
        ,
        /**
         * The new state that the `Ingress` transitioned to (if any)
         */
        state?: "PREPARING" | "READY" | "UNAVAILABLE",
        /**
         * A new status message for the `Ingress` (if any)
         */
        status?: string,
        didBind: boolean,
        newBinding?: string,
    }

    export interface IngressSettings {
        domainPrefix: string,
        domainSuffix: string,
    }

    /**
     * A `License` for use in `Job`s
     */
    export interface License {
        id: string,
        product: accounting.ProductReference,
        /**
         * Information about the owner of this resource
         */
        owner: LicenseOwner,
        /**
         * Information about when this resource was created
         */
        createdAt: number /* int64 */
        ,
        /**
         * The current status of this resource
         */
        status: LicenseStatus,
        /**
         * Billing information associated with this `License`
         */
        billing: LicenseBilling,
        /**
         * A list of updates for this `License`
         */
        updates: LicenseUpdate[],
        resolvedProduct?: accounting.ProductNS.License,
        acl?: LicenseAclEntry[],
    }

    export interface LicenseOwner {
        /**
         * The username of the user which created this resource.
         *
         * In cases where this user is removed from the project the ownership will be transferred to the current PI of the project.
         */
        username: string,
        /**
         * The project which owns the resource
         */
        project?: string,
    }

    /**
     * The status of an `License`
     */
    export interface LicenseStatus {
        state: "PREPARING" | "READY" | "UNAVAILABLE",
    }

    export interface LicenseBilling {
        pricePerUnit: number /* int64 */
        ,
        creditsCharged: number /* int64 */
        ,
    }

    export interface LicenseUpdate {
        /**
         * A timestamp for when this update was registered by UCloud
         */
        timestamp: number /* int64 */
        ,
        /**
         * The new state that the `License` transitioned to (if any)
         */
        state?: "PREPARING" | "READY" | "UNAVAILABLE",
        /**
         * A new status message for the `License` (if any)
         */
        status?: string,
    }

    export interface LicenseAclEntry {
        entity: LicenseAclEntryNS.Entity,
        permissions: "USE"[],
    }

    export interface JobsCreateResponse {
        ids: string[],
    }

    export interface JobsRetrieveRequest {
        id: string,
        includeParameters?: boolean,
        includeUpdates?: boolean,
        includeApplication?: boolean,
        includeProduct?: boolean,
    }

    export interface JobsUtilizationResponse {
        capacity: CpuAndMemory,
        usedCapacity: CpuAndMemory,
        queueStatus: QueueStatus,
    }

    export interface JobsBrowseRequest {
        itemsPerPage: number /* int32 */
        ,
        next?: string,
        consistency?: "PREFER" | "REQUIRE",
        itemsToSkip?: number /* int64 */
        ,
        includeParameters?: boolean,
        includeUpdates?: boolean,
        includeApplication?: boolean,
        includeProduct?: boolean,
        sortBy?: "CREATED_AT" | "STATE" | "APPLICATION",
        filterApplication?: string,
        filterLaunchedBy?: string,
        filterState?: "IN_QUEUE" | "RUNNING" | "CANCELING" | "SUCCESS" | "FAILURE" | "EXPIRED",
        filterTitle?: string,
        filterBefore?: number /* int64 */
        ,
        filterAfter?: number /* int64 */
        ,
    }

    export interface JobsExtendRequestItem {
        jobId: string,
        requestedTime: SimpleDuration,
    }

    export interface JobsOpenInteractiveSessionResponse {
        sessions: OpenSessionWithProvider[],
    }

    export interface OpenSessionWithProvider {
        providerDomain: string,
        providerId: string,
        session: OpenSession,
    }

    export interface JobsOpenInteractiveSessionRequestItem {
        id: string,
        rank: number /* int32 */
        ,
        sessionType: "WEB" | "VNC" | "SHELL",
    }

    export interface JobsControlUpdateRequestItem {
        jobId: string,
        state?: "IN_QUEUE" | "RUNNING" | "CANCELING" | "SUCCESS" | "FAILURE" | "EXPIRED",
        status?: string,
    }

    export interface JobsControlChargeCreditsResponse {
        /**
         * A list of jobs which could not be charged due to lack of funds. If all jobs were charged successfully then this will empty.
         */
        insufficientFunds: FindByStringId[],
        /**
         * A list of jobs which could not be charged due to it being a duplicate charge. If all jobs were charged successfully this will be empty.
         */
        duplicateCharges: FindByStringId[],
    }

    export interface JobsControlChargeCreditsRequestItem {
        /**
         * The ID of the job
         */
        id: string,
        /**
         * The ID of the charge
         *
         * This charge ID must be unique for the job, UCloud will reject charges which are not unique.
         */
        chargeId: string,
        /**
         * Amount of compute time to charge the user
         *
         * The wall duration should be for a single job replica and should only be for the time used since the lastupdate. UCloud will automatically multiply the amount with the number of job replicas.
         */
        wallDuration: SimpleDuration,
    }

    export interface JobsControlRetrieveRequest {
        id: string,
        includeParameters?: boolean,
        includeUpdates?: boolean,
        includeApplication?: boolean,
        includeProduct?: boolean,
    }

    export interface JobsControlSubmitFileRequest {
        jobId: string,
        filePath: string,
    }

    export interface IngressesBrowseRequest {
        includeUpdates?: boolean,
        includeProduct?: boolean,
        itemsPerPage?: number /* int32 */
        ,
        next?: string,
        consistency?: "PREFER" | "REQUIRE",
        itemsToSkip?: number /* int64 */
        ,
        domain?: string,
        provider?: string,
    }

    export interface IngressesCreateResponse {
        ids: string[],
    }

    export interface IngressCreateRequestItem {
        domain: string,
        product: accounting.ProductReference,
    }

    export interface IngressRetrieve {
        id: string,
    }

    export interface IngressRetrieveWithFlags {
        id: string,
        includeUpdates?: boolean,
        includeProduct?: boolean,
    }

    export interface IngressControlUpdateRequestItem {
        id: string,
        state?: "PREPARING" | "READY" | "UNAVAILABLE",
        status?: string,
        clearBindingToJob?: boolean,
    }

    export interface IngressControlChargeCreditsResponse {
        /**
         * A list of jobs which could not be charged due to lack of funds. If all jobs were charged successfully then this will empty.
         */
        insufficientFunds: IngressId[],
        /**
         * A list of ingresses which could not be charged due to it being a duplicate charge. If all ingresses were charged successfully this will be empty.
         */
        duplicateCharges: IngressId[],
    }

    export interface IngressId {
        id: string,
    }

    export interface IngressControlChargeCreditsRequestItem {
        /**
         * The ID of the `Ingress`
         */
        id: string,
        /**
         * The ID of the charge
         *
         * This charge ID must be unique for the `Ingress`, UCloud will reject charges which are not unique.
         */
        chargeId: string,
        /**
         * Amount of units to charge the user
         */
        units: number /* int64 */
        ,
    }

    export interface LicensesBrowseRequest {
        includeUpdates?: boolean,
        includeProduct?: boolean,
        includeAcl?: boolean,
        itemsPerPage?: number /* int32 */
        ,
        next?: string,
        consistency?: "PREFER" | "REQUIRE",
        itemsToSkip?: number /* int64 */
        ,
        provider?: string,
        tag?: string,
    }

    export interface LicensesCreateResponse {
        ids: string[],
    }

    export interface LicenseCreateRequestItem {
        product: accounting.ProductReference,
    }

    export interface LicenseRetrieve {
        id: string,
    }

    export interface LicenseRetrieveWithFlags {
        id: string,
        includeUpdates?: boolean,
        includeProduct?: boolean,
        includeAcl?: boolean,
    }

    export interface LicensesUpdateAclRequestItem {
        id: string,
        acl: LicenseAclEntry[],
    }

    export interface LicenseControlUpdateRequestItem {
        id: string,
        state?: "PREPARING" | "READY" | "UNAVAILABLE",
        status?: string,
    }

    export interface LicenseControlChargeCreditsResponse {
        /**
         * A list of jobs which could not be charged due to lack of funds. If all jobs were charged successfully then this will empty.
         */
        insufficientFunds: LicenseId[],
        /**
         * A list of ingresses which could not be charged due to it being a duplicate charge. If all ingresses were charged successfully this will be empty.
         */
        duplicateCharges: LicenseId[],
    }

    export interface LicenseId {
        id: string,
    }

    export interface LicenseControlChargeCreditsRequestItem {
        /**
         * The ID of the `License`
         */
        id: string,
        /**
         * The ID of the charge
         *
         * This charge ID must be unique for the `License`, UCloud will reject charges which are not unique.
         */
        chargeId: string,
        /**
         * Amount of units to charge the user
         */
        units: number /* int64 */
        ,
    }

    export interface ApplicationWithFavoriteAndTags {
        metadata: ApplicationMetadata,
        invocation: ApplicationInvocationDescription,
        favorite: boolean,
        tags: string[],
    }

    export interface FindApplicationAndOptionalDependencies {
        appName: string,
        appVersion: string,
    }

    export interface HasPermissionRequest {
        appName: string,
        appVersion: string,
        permission: "LAUNCH"[],
    }

    export interface DetailedEntityWithPermission {
        entity: DetailedAccessEntity,
        permission: "LAUNCH",
    }

    export interface DetailedAccessEntity {
        user?: string,
        project?: Project,
        group?: Project,
    }

    export interface Project {
        id: string,
        title: string,
    }

    export interface ListAclRequest {
        appName: string,
    }

    export interface UpdateAclRequest {
        applicationName: string,
        changes: ACLEntryRequest[],
    }

    export interface ACLEntryRequest {
        entity: AccessEntity,
        rights: "LAUNCH",
        revoke: boolean,
    }

    export interface AccessEntity {
        user?: string,
        project?: string,
        group?: string,
    }

    export interface ApplicationWithExtension {
        metadata: ApplicationMetadata,
        extensions: string[],
    }

    export interface FindBySupportedFileExtension {
        files: string[],
    }

    export interface ApplicationSummaryWithFavorite {
        metadata: ApplicationMetadata,
        favorite: boolean,
        tags: string[],
    }

    export interface FindByNameAndPagination {
        appName: string,
        itemsPerPage?: number /* int32 */
        ,
        page?: number /* int32 */
        ,
    }

    export interface DeleteAppRequest {
        appName: string,
        appVersion: string,
    }

    export interface FindLatestByToolRequest {
        tool: string,
        itemsPerPage?: number /* int32 */
        ,
        page?: number /* int32 */
        ,
    }

    export interface FindByNameAndVersion {
        name: string,
        version: string,
    }

    export interface UploadApplicationLogoRequest {
        name: string,
        data: BinaryStream,
    }

    export interface ClearLogoRequest {
        name: string,
    }

    export interface FetchLogoRequest {
        name: string,
    }

    export interface CreateTagsRequest {
        tags: string[],
        applicationName: string,
    }

    export interface TagSearchRequest {
        query: string,
        excludeTools?: string,
        itemsPerPage?: number /* int32 */
        ,
        page?: number /* int32 */
        ,
    }

    export interface AppSearchRequest {
        query: string,
        itemsPerPage?: number /* int32 */
        ,
        page?: number /* int32 */
        ,
    }

    export interface AdvancedSearchRequest {
        query?: string,
        tags?: string[],
        showAllVersions: boolean,
        itemsPerPage?: number /* int32 */
        ,
        page?: number /* int32 */
        ,
    }

    export interface IsPublicResponse {
        public: Record<string, boolean>,
    }

    export interface IsPublicRequest {
        applications: NameAndVersion[],
    }

    export interface SetPublicRequest {
        appName: string,
        appVersion: string,
        public: boolean,
    }

    export interface FavoriteRequest {
        appName: string,
        appVersion: string,
    }

    export namespace AppParameterValueNS {
        /**
         * A reference to a UCloud file
         *
         * The path to the file most always be absolute an refers to either a UCloud directory or file.
         */
        export interface File {
            path: string,
            readOnly: boolean,
            type: "file",
        }

        /**
         * A boolean value
         */
        export interface Bool {
            value: boolean,
            type: "boolean",
        }

        /**
         * A textual value
         */
        export interface Text {
            value: string,
            type: "text",
        }

        /**
         * An integral value
         *
         * Internally this uses a big integer type and there are no defined limits.
         */
        export interface Integer {
            value: number /* int64 */
            ,
            type: "integer",
        }

        /**
         * A floating point value
         *
         * Internally this uses a big decimal type and there are no defined limits.
         */
        export interface FloatingPoint {
            value: number /* float32 */
            ,
            type: "floating_point",
        }

        /**
         * A reference to a separate UCloud job
         *
         * The compute provider should use this information to make sure that the two jobs can communicate with each other.
         */
        export interface Peer {
            hostname: string,
            jobId: string,
            type: "peer",
        }

        /**
         * A reference to a license
         */
        export interface License {
            id: string,
            type: "license_server",
        }

        /**
         * A reference to block storage
         */
        export interface Network {
            id: string,
            type: "network",
        }

        /**
         * A reference to block storage
         */
        export interface BlockStorage {
            id: string,
            type: "block_storage",
        }

        /**
         * HTTP Ingress
         */
        export interface Ingress {
            id: string,
            type: "ingress",
        }
    }
    export namespace tools {
        export function findByName(
            request: FindByNameAndPagination
        ): APICallParameters<FindByNameAndPagination, Page<Tool>> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/hpc/tools" + "/" + request.appName, {
                    itemsPerPage: request.itemsPerPage,
                    page: request.page
                }),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function findByNameAndVersion(
            request: FindByNameAndVersion
        ): APICallParameters<FindByNameAndVersion, Tool> {
            return {
                context: "",
                method: "GET",
                path: "/api/hpc/tools" + "/" + request.name + "/" + request.version,
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function listAll(
            request: PaginationRequest
        ): APICallParameters<PaginationRequest, Page<Tool>> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/hpc/tools", {itemsPerPage: request.itemsPerPage, page: request.page}),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function create(
            request: BinaryStream
        ): APICallParameters<BinaryStream, any /* unknown */> {
            return {
                context: "",
                method: "PUT",
                path: "/api/hpc/tools",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function uploadLogo(
            request: UploadApplicationLogoRequest
        ): APICallParameters<UploadApplicationLogoRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/hpc/tools" + "/uploadLogo",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function clearLogo(
            request: ClearLogoRequest
        ): APICallParameters<ClearLogoRequest, any /* unknown */> {
            return {
                context: "",
                method: "DELETE",
                path: "/api/hpc/tools" + "/clearLogo" + "/" + request.name,
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function fetchLogo(
            request: FetchLogoRequest
        ): APICallParameters<FetchLogoRequest, BinaryStream> {
            return {
                context: "",
                method: "GET",
                path: "/api/hpc/tools" + "/logo" + "/" + request.name,
                parameters: request,
                reloadId: Math.random(),
            };
        }
    }
    export namespace apps {
        export function findByNameAndVersion(
            request: FindApplicationAndOptionalDependencies
        ): APICallParameters<FindApplicationAndOptionalDependencies, ApplicationWithFavoriteAndTags> {
            return {
                context: "",
                method: "GET",
                path: "/api/hpc/apps" + "/" + request.appName + "/" + request.appVersion,
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function hasPermission(
            request: HasPermissionRequest
        ): APICallParameters<HasPermissionRequest, boolean> {
            return {
                context: "",
                method: "GET",
                path: "/api/hpc/apps" + "/permission" + "/" + request.appName + "/" + request.appVersion + "/" + request.permission,
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function listAcl(
            request: ListAclRequest
        ): APICallParameters<ListAclRequest, DetailedEntityWithPermission[]> {
            return {
                context: "",
                method: "GET",
                path: "/api/hpc/apps" + "/list-acl" + "/" + request.appName,
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function updateAcl(
            request: UpdateAclRequest
        ): APICallParameters<UpdateAclRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/hpc/apps" + "/updateAcl",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function findBySupportedFileExtension(
            request: FindBySupportedFileExtension
        ): APICallParameters<FindBySupportedFileExtension, ApplicationWithExtension[]> {
            return {
                context: "",
                method: "POST",
                path: "/api/hpc/apps" + "/bySupportedFileExtension",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function findByName(
            request: FindByNameAndPagination
        ): APICallParameters<FindByNameAndPagination, Page<ApplicationSummaryWithFavorite>> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/hpc/apps" + "/" + request.appName, {
                    itemsPerPage: request.itemsPerPage,
                    page: request.page
                }),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function listAll(
            request: PaginationRequest
        ): APICallParameters<PaginationRequest, Page<ApplicationSummaryWithFavorite>> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/hpc/apps", {itemsPerPage: request.itemsPerPage, page: request.page}),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function remove(
            request: DeleteAppRequest
        ): APICallParameters<DeleteAppRequest, any /* unknown */> {
            return {
                context: "",
                method: "DELETE",
                path: "/api/hpc/apps",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function create(
            request: BinaryStream
        ): APICallParameters<BinaryStream, any /* unknown */> {
            return {
                context: "",
                method: "PUT",
                path: "/api/hpc/apps",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function findLatestByTool(
            request: FindLatestByToolRequest
        ): APICallParameters<FindLatestByToolRequest, Page<Application>> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/hpc/apps" + "/byTool" + "/" + request.tool, {
                    itemsPerPage: request.itemsPerPage,
                    page: request.page
                }),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function uploadLogo(
            request: UploadApplicationLogoRequest
        ): APICallParameters<UploadApplicationLogoRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/hpc/apps" + "/uploadLogo",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function clearLogo(
            request: ClearLogoRequest
        ): APICallParameters<ClearLogoRequest, any /* unknown */> {
            return {
                context: "",
                method: "DELETE",
                path: "/api/hpc/apps" + "/clearLogo" + "/" + request.name,
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function fetchLogo(
            request: FetchLogoRequest
        ): APICallParameters<FetchLogoRequest, BinaryStream> {
            return {
                context: "",
                method: "GET",
                path: "/api/hpc/apps" + "/logo" + "/" + request.name,
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function createTag(
            request: CreateTagsRequest
        ): APICallParameters<CreateTagsRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/hpc/apps" + "/createTag",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function removeTag(
            request: CreateTagsRequest
        ): APICallParameters<CreateTagsRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/hpc/apps" + "/deleteTag",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function searchTags(
            request: TagSearchRequest
        ): APICallParameters<TagSearchRequest, Page<ApplicationSummaryWithFavorite>> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/hpc/apps" + "/searchTags", {
                    query: request.query,
                    excludeTools: request.excludeTools,
                    itemsPerPage: request.itemsPerPage,
                    page: request.page
                }),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function searchApps(
            request: AppSearchRequest
        ): APICallParameters<AppSearchRequest, Page<ApplicationSummaryWithFavorite>> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/hpc/apps" + "/search", {
                    query: request.query,
                    itemsPerPage: request.itemsPerPage,
                    page: request.page
                }),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function advancedSearch(
            request: AdvancedSearchRequest
        ): APICallParameters<AdvancedSearchRequest, Page<ApplicationSummaryWithFavorite>> {
            return {
                context: "",
                method: "POST",
                path: "/api/hpc/apps" + "/advancedSearch",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function isPublic(
            request: IsPublicRequest
        ): APICallParameters<IsPublicRequest, IsPublicResponse> {
            return {
                context: "",
                method: "POST",
                path: "/api/hpc/apps" + "/isPublic",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function setPublic(
            request: SetPublicRequest
        ): APICallParameters<SetPublicRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/hpc/apps" + "/setPublic",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function toggleFavorite(
            request: FavoriteRequest
        ): APICallParameters<FavoriteRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/hpc/apps" + "/favorites" + "/" + request.appName + "/" + request.appVersion,
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function retrieveFavorites(
            request: PaginationRequest
        ): APICallParameters<PaginationRequest, Page<ApplicationSummaryWithFavorite>> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/hpc/apps" + "/favorites", {
                    itemsPerPage: request.itemsPerPage,
                    page: request.page
                }),
                parameters: request,
                reloadId: Math.random(),
            };
        }
    }
    export namespace control {
        /**
         * Push state changes to UCloud (update)
         *
         * ![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
         * ![Auth: Provider](https://img.shields.io/static/v1?label=Auth&message=Provider&color=informational&style=flat-square)
         *
         * Pushes one or more state changes to UCloud. UCloud will always treat all updates as a single
         * transaction. UCloud may reject the status updates if it deems them to be invalid. For example, an
         * update may be rejected if it performs an invalid state transition, such as from a terminal state to
         * a running state.
         */
        export function update(
            request: BulkRequest<JobsControlUpdateRequestItem>
        ): APICallParameters<BulkRequest<JobsControlUpdateRequestItem>, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/jobs/control" + "/update",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        /**
         * Charge the user for the job (chargeCredits)
         *
         * ![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
         * ![Auth: Provider](https://img.shields.io/static/v1?label=Auth&message=Provider&color=informational&style=flat-square)
         *
         *
         */
        export function chargeCredits(
            request: BulkRequest<JobsControlChargeCreditsRequestItem>
        ): APICallParameters<BulkRequest<JobsControlChargeCreditsRequestItem>, JobsControlChargeCreditsResponse> {
            return {
                context: "",
                method: "POST",
                path: "/api/jobs/control" + "/chargeCredits",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        /**
         * Retrieve job information (retrieve)
         *
         * ![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
         * ![Auth: Provider](https://img.shields.io/static/v1?label=Auth&message=Provider&color=informational&style=flat-square)
         *
         * Allows the compute backend to query the UCloud database for a job owned by the compute provider.
         */
        export function retrieve(
            request: JobsControlRetrieveRequest
        ): APICallParameters<JobsControlRetrieveRequest, Job> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/jobs/control" + "/retrieve", {
                    id: request.id,
                    includeApplication: request.includeApplication,
                    includeParameters: request.includeParameters,
                    includeProduct: request.includeProduct,
                    includeUpdates: request.includeUpdates
                }),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        /**
         * Submit output file to UCloud (submitFile)
         *
         * ![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
         * ![Auth: Provider](https://img.shields.io/static/v1?label=Auth&message=Provider&color=informational&style=flat-square)
         *
         * Submits an output file to UCloud which is not available to be put directly into the storage resources
         * mounted by the compute provider.
         *
         * Note: We do not recommend using this endpoint for transferring large quantities of data/files.
         */
        export function submitFile(
            request: JobsControlSubmitFileRequest
        ): APICallParameters<JobsControlSubmitFileRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/jobs/control" + "/submitFile",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }
    }
    export namespace ucloud {
        export interface DrainNodeRequest {
            node: string,
        }

        export interface IsPausedResponse {
            paused: boolean,
        }

        export interface UpdatePauseStateRequest {
            paused: boolean,
        }

        export interface KillJobRequest {
            jobId: string,
        }

        export interface KubernetesLicense {
            id: string,
            address: string,
            port: number /* int32 */
            ,
            tags: string[],
            license?: string,
        }

        export interface KubernetesLicenseBrowseRequest {
            tag?: string,
            itemsPerPage?: number /* int32 */
            ,
            next?: string,
            consistency?: "PREFER" | "REQUIRE",
            itemsToSkip?: number /* int64 */
            ,
        }

        export namespace licenses {
            export function create(
                request: BulkRequest<License>
            ): APICallParameters<BulkRequest<License>, any /* unknown */> {
                return {
                    context: "",
                    method: "POST",
                    path: "/ucloud/ucloud/licenses",
                    parameters: request,
                    reloadId: Math.random(),
                    payload: request,
                };
            }

            export function remove(
                request: BulkRequest<License>
            ): APICallParameters<BulkRequest<License>, any /* unknown */> {
                return {
                    context: "",
                    method: "DELETE",
                    path: "/ucloud/ucloud/licenses",
                    parameters: request,
                    reloadId: Math.random(),
                    payload: request,
                };
            }

            export function verify(
                request: BulkRequest<License>
            ): APICallParameters<BulkRequest<License>, any /* unknown */> {
                return {
                    context: "",
                    method: "POST",
                    path: "/ucloud/ucloud/licenses" + "/verify",
                    parameters: request,
                    reloadId: Math.random(),
                    payload: request,
                };
            }

            export namespace maintenance {
                export function create(
                    request: BulkRequest<KubernetesLicense>
                ): APICallParameters<BulkRequest<KubernetesLicense>, any /* unknown */> {
                    return {
                        context: "",
                        method: "POST",
                        path: "/ucloud/ucloud/licenses/maintenance",
                        parameters: request,
                        reloadId: Math.random(),
                        payload: request,
                    };
                }

                export function browse(
                    request: KubernetesLicenseBrowseRequest
                ): APICallParameters<KubernetesLicenseBrowseRequest, PageV2<KubernetesLicense>> {
                    return {
                        context: "",
                        method: "GET",
                        path: buildQueryString("/ucloud/ucloud/licenses/maintenance" + "/browse", {
                            consistency: request.consistency,
                            itemsPerPage: request.itemsPerPage,
                            itemsToSkip: request.itemsToSkip,
                            next: request.next,
                            tag: request.tag
                        }),
                        parameters: request,
                        reloadId: Math.random(),
                    };
                }

                export function update(
                    request: BulkRequest<KubernetesLicense>
                ): APICallParameters<BulkRequest<KubernetesLicense>, any /* unknown */> {
                    return {
                        context: "",
                        method: "POST",
                        path: "/ucloud/ucloud/licenses/maintenance" + "/update",
                        parameters: request,
                        reloadId: Math.random(),
                        payload: request,
                    };
                }
            }
        }
        export namespace maintenance {
            export function drainCluster(): APICallParameters<{}, any /* unknown */> {
                return {
                    context: "",
                    method: "POST",
                    path: "/api/app/compute/kubernetes/maintenance" + "/drain-cluster",
                    reloadId: Math.random(),
                };
            }

            export function drainNode(
                request: DrainNodeRequest
            ): APICallParameters<DrainNodeRequest, any /* unknown */> {
                return {
                    context: "",
                    method: "POST",
                    path: "/api/app/compute/kubernetes/maintenance" + "/drain-node",
                    parameters: request,
                    reloadId: Math.random(),
                    payload: request,
                };
            }

            export function isPaused(): APICallParameters<{}, IsPausedResponse> {
                return {
                    context: "",
                    method: "GET",
                    path: "/api/app/compute/kubernetes/maintenance" + "/paused",
                    reloadId: Math.random(),
                };
            }

            export function updatePauseState(
                request: UpdatePauseStateRequest
            ): APICallParameters<UpdatePauseStateRequest, any /* unknown */> {
                return {
                    context: "",
                    method: "POST",
                    path: "/api/app/compute/kubernetes/maintenance" + "/pause",
                    parameters: request,
                    reloadId: Math.random(),
                    payload: request,
                };
            }

            export function killJob(
                request: KillJobRequest
            ): APICallParameters<KillJobRequest, any /* unknown */> {
                return {
                    context: "",
                    method: "POST",
                    path: "/api/app/compute/kubernetes/maintenance" + "/kill-job",
                    parameters: request,
                    reloadId: Math.random(),
                    payload: request,
                };
            }
        }
        export namespace jobs {
            /**
             * Start a compute job (create)
             *
             * ![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
             * ![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)
             *
             * Starts one or more compute  The jobs have already been verified by UCloud and it is assumed to be
             * ready for the provider. The provider can choose to reject the entire batch by responding with a 4XX or
             * 5XX status code. Note that the batch must be handled as a single transaction.
             *
             * The provider should respond to this request as soon as the jobs have been scheduled. The provider should
             * then switch to [`control.update`](#operation/control.update) in order to provide updates about the progress.
             */
            export function create(
                request: BulkRequest<Job>
            ): APICallParameters<BulkRequest<Job>, any /* unknown */> {
                return {
                    context: "",
                    method: "POST",
                    path: "/ucloud/ucloud/compute/jobs",
                    parameters: request,
                    reloadId: Math.random(),
                    payload: request,
                };
            }

            /**
             * Request job cancellation and destruction (delete)
             *
             * ![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
             * ![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)
             *
             * Deletes one or more compute  The provider should not only stop the compute job but also delete
             * _compute_ related resources. For example, if the job is a virtual machine job, the underlying machine
             * should also be deleted. None of the resources attached to the job, however, should be deleted.
             */
            export function remove(
                request: BulkRequest<Job>
            ): APICallParameters<BulkRequest<Job>, any /* unknown */> {
                return {
                    context: "",
                    method: "DELETE",
                    path: "/ucloud/ucloud/compute/jobs",
                    parameters: request,
                    reloadId: Math.random(),
                    payload: request,
                };
            }

            /**
             * Extend the duration of a job (extend)
             *
             * ![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
             * ![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)
             *
             *
             */
            export function extend(
                request: BulkRequest<ComputeExtendRequestItem>
            ): APICallParameters<BulkRequest<ComputeExtendRequestItem>, any /* unknown */> {
                return {
                    context: "",
                    method: "POST",
                    path: "/ucloud/ucloud/compute/jobs" + "/extend",
                    parameters: request,
                    reloadId: Math.random(),
                    payload: request,
                };
            }

            /**
             * Suspend a job (suspend)
             *
             * ![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
             * ![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)
             *
             *
             */
            export function suspend(
                request: BulkRequest<Job>
            ): APICallParameters<BulkRequest<Job>, any /* unknown */> {
                return {
                    context: "",
                    method: "POST",
                    path: "/ucloud/ucloud/compute/jobs" + "/suspend",
                    parameters: request,
                    reloadId: Math.random(),
                    payload: request,
                };
            }

            /**
             * Verify UCloud data is synchronized with provider (verify)
             *
             * ![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
             * ![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)
             *
             * This call is periodically executed by UCloud against all active providers. It is the job of the
             * provider to ensure that the jobs listed in the request are in its local database. If some of the
             * jobs are not in the provider's database then this should be treated as a job which is no longer valid.
             * The compute backend should trigger normal cleanup code and notify UCloud about the job's termination.
             *
             * The backend should _not_ attempt to start the job.
             */
            export function verify(
                request: BulkRequest<Job>
            ): APICallParameters<BulkRequest<Job>, any /* unknown */> {
                return {
                    context: "",
                    method: "POST",
                    path: "/ucloud/ucloud/compute/jobs" + "/verify",
                    parameters: request,
                    reloadId: Math.random(),
                    payload: request,
                };
            }

            export function openInteractiveSession(
                request: BulkRequest<ComputeOpenInteractiveSessionRequestItem>
            ): APICallParameters<BulkRequest<ComputeOpenInteractiveSessionRequestItem>, ComputeOpenInteractiveSessionResponse> {
                return {
                    context: "",
                    method: "POST",
                    path: "/ucloud/ucloud/compute/jobs" + "/interactiveSession",
                    parameters: request,
                    reloadId: Math.random(),
                    payload: request,
                };
            }

            /**
             * Retrieves the compute provider manifest (retrieveManifest)
             *
             * ![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
             * ![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)
             *
             *
             */
            export function retrieveManifest(): APICallParameters<{}, ProviderManifest> {
                return {
                    context: "",
                    method: "GET",
                    path: "/ucloud/ucloud/compute/jobs" + "/retrieveManifest",
                    reloadId: Math.random(),
                };
            }

            export function retrieveUtilization(): APICallParameters<{}, ComputeUtilizationResponse> {
                return {
                    context: "",
                    method: "GET",
                    path: "/ucloud/ucloud/compute/jobs" + "/retrieveUtilization",
                    reloadId: Math.random(),
                };
            }
        }
        export namespace ingresses {
            export function create(
                request: BulkRequest<Ingress>
            ): APICallParameters<BulkRequest<Ingress>, any /* unknown */> {
                return {
                    context: "",
                    method: "POST",
                    path: "/ucloud/ucloud/ingresses",
                    parameters: request,
                    reloadId: Math.random(),
                    payload: request,
                };
            }

            export function remove(
                request: BulkRequest<Ingress>
            ): APICallParameters<BulkRequest<Ingress>, any /* unknown */> {
                return {
                    context: "",
                    method: "DELETE",
                    path: "/ucloud/ucloud/ingresses",
                    parameters: request,
                    reloadId: Math.random(),
                    payload: request,
                };
            }

            export function retrieveSettings(
                request: accounting.ProductReference
            ): APICallParameters<accounting.ProductReference, IngressSettings> {
                return {
                    context: "",
                    method: "GET",
                    path: buildQueryString("/ucloud/ucloud/ingresses" + "/retrieveSettings", {
                        category: request.category,
                        id: request.id,
                        provider: request.provider
                    }),
                    parameters: request,
                    reloadId: Math.random(),
                };
            }

            export function verify(
                request: BulkRequest<Ingress>
            ): APICallParameters<BulkRequest<Ingress>, any /* unknown */> {
                return {
                    context: "",
                    method: "POST",
                    path: "/ucloud/ucloud/ingresses" + "/verify",
                    parameters: request,
                    reloadId: Math.random(),
                    payload: request,
                };
            }
        }
    }
    export namespace licenses {
        export function browse(
            request: LicensesBrowseRequest
        ): APICallParameters<LicensesBrowseRequest, PageV2<License>> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/licenses" + "/browse", {
                    consistency: request.consistency,
                    includeAcl: request.includeAcl,
                    includeProduct: request.includeProduct,
                    includeUpdates: request.includeUpdates,
                    itemsPerPage: request.itemsPerPage,
                    itemsToSkip: request.itemsToSkip,
                    next: request.next,
                    provider: request.provider,
                    tag: request.tag
                }),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function create(
            request: BulkRequest<LicenseCreateRequestItem>
        ): APICallParameters<BulkRequest<LicenseCreateRequestItem>, LicensesCreateResponse> {
            return {
                context: "",
                method: "POST",
                path: "/api/licenses",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function remove(
            request: BulkRequest<LicenseRetrieve>
        ): APICallParameters<BulkRequest<LicenseRetrieve>, any /* unknown */> {
            return {
                context: "",
                method: "DELETE",
                path: "/api/licenses",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function retrieve(
            request: LicenseRetrieveWithFlags
        ): APICallParameters<LicenseRetrieveWithFlags, License> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/licenses" + "/retrieve", {
                    id: request.id,
                    includeAcl: request.includeAcl,
                    includeProduct: request.includeProduct,
                    includeUpdates: request.includeUpdates
                }),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function updateAcl(
            request: BulkRequest<LicensesUpdateAclRequestItem>
        ): APICallParameters<BulkRequest<LicensesUpdateAclRequestItem>, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/licenses" + "/acl",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export namespace control {
            export function update(
                request: BulkRequest<LicenseControlUpdateRequestItem>
            ): APICallParameters<BulkRequest<LicenseControlUpdateRequestItem>, any /* unknown */> {
                return {
                    context: "",
                    method: "POST",
                    path: "/api/licenses/control" + "/update",
                    parameters: request,
                    reloadId: Math.random(),
                    payload: request,
                };
            }

            export function retrieve(
                request: LicenseRetrieveWithFlags
            ): APICallParameters<LicenseRetrieveWithFlags, License> {
                return {
                    context: "",
                    method: "GET",
                    path: buildQueryString("/api/licenses/control" + "/retrieve", {
                        id: request.id,
                        includeAcl: request.includeAcl,
                        includeProduct: request.includeProduct,
                        includeUpdates: request.includeUpdates
                    }),
                    parameters: request,
                    reloadId: Math.random(),
                };
            }

            export function chargeCredits(
                request: BulkRequest<LicenseControlChargeCreditsRequestItem>
            ): APICallParameters<BulkRequest<LicenseControlChargeCreditsRequestItem>, LicenseControlChargeCreditsResponse> {
                return {
                    context: "",
                    method: "POST",
                    path: "/api/licenses/control" + "/chargeCredits",
                    parameters: request,
                    reloadId: Math.random(),
                    payload: request,
                };
            }
        }
    }
    export namespace license {
        export function get(
            request: LicenseServerRequest
        ): APICallParameters<LicenseServerRequest, LicenseServerWithId> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/app/license", {serverId: request.serverId}),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function remove(
            request: DeleteServerRequest
        ): APICallParameters<DeleteServerRequest, any /* unknown */> {
            return {
                context: "",
                method: "DELETE",
                path: buildQueryString("/api/app/license", {id: request.id}),
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function updateAcl(
            request: UpdateAclRequest
        ): APICallParameters<UpdateAclRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/app/license" + "/updateAcl",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function listAcl(
            request: ListAclRequest
        ): APICallParameters<ListAclRequest, DetailedAccessEntityWithPermission[]> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/app/license" + "/listAcl", {serverId: request.serverId}),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function findByTag(
            request: FindByTagRequest
        ): APICallParameters<FindByTagRequest, LicenseServerId[]> {
            return {
                context: "",
                method: "POST",
                path: "/api/app/license" + "/by-tag",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function listAll(): APICallParameters<{}, LicenseServerWithId[]> {
            return {
                context: "",
                method: "GET",
                path: "/api/app/license" + "/listAll",
                reloadId: Math.random(),
            };
        }

        export function update(
            request: LicenseServerWithId
        ): APICallParameters<LicenseServerWithId, UpdateServerResponse> {
            return {
                context: "",
                method: "POST",
                path: "/api/app/license" + "/update",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function new_(
            request: LicenseServer
        ): APICallParameters<LicenseServer, NewServerResponse> {
            return {
                context: "",
                method: "POST",
                path: "/api/app/license" + "/new",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export interface LicenseServerWithId {
            id: string,
            name: string,
            address: string,
            port: number /* int32 */
            ,
            license?: string,
        }

        export interface LicenseServerRequest {
            serverId: string,
        }

        export interface DeleteServerRequest {
            id: string,
        }

        export interface UpdateAclRequest {
            serverId: string,
            changes: AclChange[],
        }

        export interface AclChange {
            entity: AccessEntity,
            rights: "READ" | "READ_WRITE",
            revoke: boolean,
        }

        export interface AccessEntity {
            user?: string,
            project?: string,
            group?: string,
        }

        export interface DetailedAccessEntityWithPermission {
            entity: DetailedAccessEntity,
            permission: "READ" | "READ_WRITE",
        }

        export interface DetailedAccessEntity {
            user?: string,
            project?: Project,
            group?: Project,
        }

        export interface Project {
            id: string,
            title: string,
        }

        export interface ListAclRequest {
            serverId: string,
        }

        export interface LicenseServerId {
            id: string,
            name: string,
        }

        export interface FindByTagRequest {
            tags: string[],
        }

        export interface UpdateServerResponse {
            serverId: string,
        }

        export interface NewServerResponse {
            serverId: string,
        }

        export interface LicenseServer {
            name: string,
            address: string,
            port: number /* int32 */
            ,
            license?: string,
        }

        export interface AddTagRequest {
            serverId: string,
            tag: string,
        }

        export interface DeleteTagRequest {
            serverId: string,
            tag: string,
        }

        export interface ListTagsResponse {
            tags: string[],
        }

        export interface ListTagsRequest {
            serverId: string,
        }

        export namespace tag {
            export function add(
                request: AddTagRequest
            ): APICallParameters<AddTagRequest, any /* unknown */> {
                return {
                    context: "",
                    method: "POST",
                    path: "/api/app/license/tag" + "/add",
                    parameters: request,
                    reloadId: Math.random(),
                    payload: request,
                };
            }

            export function remove(
                request: DeleteTagRequest
            ): APICallParameters<DeleteTagRequest, any /* unknown */> {
                return {
                    context: "",
                    method: "POST",
                    path: "/api/app/license/tag" + "/delete",
                    parameters: request,
                    reloadId: Math.random(),
                    payload: request,
                };
            }

            export function list(
                request: ListTagsRequest
            ): APICallParameters<ListTagsRequest, ListTagsResponse> {
                return {
                    context: "",
                    method: "GET",
                    path: buildQueryString("/api/app/license/tag" + "/list", {serverId: request.serverId}),
                    parameters: request,
                    reloadId: Math.random(),
                };
            }
        }
    }
    export namespace ingresses {
        export function browse(
            request: IngressesBrowseRequest
        ): APICallParameters<IngressesBrowseRequest, PageV2<Ingress>> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/ingresses" + "/browse", {
                    consistency: request.consistency,
                    domain: request.domain,
                    includeProduct: request.includeProduct,
                    includeUpdates: request.includeUpdates,
                    itemsPerPage: request.itemsPerPage,
                    itemsToSkip: request.itemsToSkip,
                    next: request.next,
                    provider: request.provider
                }),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function create(
            request: BulkRequest<IngressCreateRequestItem>
        ): APICallParameters<BulkRequest<IngressCreateRequestItem>, IngressesCreateResponse> {
            return {
                context: "",
                method: "POST",
                path: "/api/ingresses",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function remove(
            request: BulkRequest<IngressRetrieve>
        ): APICallParameters<BulkRequest<IngressRetrieve>, any /* unknown */> {
            return {
                context: "",
                method: "DELETE",
                path: "/api/ingresses",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function retrieve(
            request: IngressRetrieveWithFlags
        ): APICallParameters<IngressRetrieveWithFlags, Ingress> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/ingresses" + "/retrieve", {
                    id: request.id,
                    includeProduct: request.includeProduct,
                    includeUpdates: request.includeUpdates
                }),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function retrieveSettings(
            request: accounting.ProductReference
        ): APICallParameters<accounting.ProductReference, IngressSettings> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/ingresses" + "/retrieveSettings", {
                    category: request.category,
                    id: request.id,
                    provider: request.provider
                }),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export namespace control {
            export function update(
                request: BulkRequest<IngressControlUpdateRequestItem>
            ): APICallParameters<BulkRequest<IngressControlUpdateRequestItem>, any /* unknown */> {
                return {
                    context: "",
                    method: "POST",
                    path: "/api/ingresses/control" + "/update",
                    parameters: request,
                    reloadId: Math.random(),
                    payload: request,
                };
            }

            export function retrieve(
                request: IngressRetrieveWithFlags
            ): APICallParameters<IngressRetrieveWithFlags, Ingress> {
                return {
                    context: "",
                    method: "GET",
                    path: buildQueryString("/api/ingresses/control" + "/retrieve", {
                        id: request.id,
                        includeProduct: request.includeProduct,
                        includeUpdates: request.includeUpdates
                    }),
                    parameters: request,
                    reloadId: Math.random(),
                };
            }

            export function chargeCredits(
                request: BulkRequest<IngressControlChargeCreditsRequestItem>
            ): APICallParameters<BulkRequest<IngressControlChargeCreditsRequestItem>, IngressControlChargeCreditsResponse> {
                return {
                    context: "",
                    method: "POST",
                    path: "/api/ingresses/control" + "/chargeCredits",
                    parameters: request,
                    reloadId: Math.random(),
                    payload: request,
                };
            }
        }
    }
    export namespace LicenseAclEntryNS {
        export type Entity = EntityNS.ProjectGroup
        export namespace EntityNS {
            export interface ProjectGroup {
                projectId: string,
                group: string,
                type: "project_group",
            }
        }
    }
    export namespace ManifestFeatureSupportNS {
        export interface Compute {
            docker: ComputeNS.Docker,
            virtualMachine: ComputeNS.VirtualMachine,
        }

        export namespace ComputeNS {
            export interface Docker {
                enabled: boolean,
                web: boolean,
                vnc: boolean,
                batch: boolean,
                logs: boolean,
                terminal: boolean,
                peers: boolean,
            }

            export interface VirtualMachine {
                enabled: boolean,
                logs: boolean,
                vnc: boolean,
                terminal: boolean,
            }
        }
    }
    export namespace jobs {
        /**
         * Start a compute job (create)
         *
         * ![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
         * ![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)
         *
         *
         */
        export function create(
            request: BulkRequest<JobParameters>
        ): APICallParameters<BulkRequest<JobParameters>, JobsCreateResponse> {
            return {
                context: "",
                method: "POST",
                path: "/api/jobs",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        /**
         * Request job cancellation and destruction (delete)
         *
         * ![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
         * ![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)
         *
         * This call will request the cancellation of the associated  This will make sure that the jobs
         * are eventually stopped and resources are released. If the job is running a virtual machine, then the
         * virtual machine will be stopped and destroyed. Persistent storage attached to the job will not be
         * deleted only temporary data from the job will be deleted.
         *
         * This call is asynchronous and the cancellation may not be immediately visible in the job. Progress can
         * be followed using the [`retrieve`](#operation/retrieve), [`browse`](#operation/browse), [`follow`](#operation/follow) calls.
         */
        export function remove(
            request: BulkRequest<FindByStringId>
        ): APICallParameters<BulkRequest<FindByStringId>, any /* unknown */> {
            return {
                context: "",
                method: "DELETE",
                path: "/api/jobs",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        /**
         * Retrieve a single Job (retrieve)
         *
         * ![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
         * ![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)
         *
         *
         */
        export function retrieve(
            request: JobsRetrieveRequest
        ): APICallParameters<JobsRetrieveRequest, Job> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/jobs" + "/retrieve", {
                    id: request.id,
                    includeApplication: request.includeApplication,
                    includeParameters: request.includeParameters,
                    includeProduct: request.includeProduct,
                    includeUpdates: request.includeUpdates
                }),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        /**
         * Retrieve utilization information from cluster (retrieveUtilization)
         *
         * ![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
         * ![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)
         *
         *
         */
        export function retrieveUtilization(): APICallParameters<{}, JobsUtilizationResponse> {
            return {
                context: "",
                method: "GET",
                path: "/api/jobs" + "/retrieveUtilization",
                reloadId: Math.random(),
            };
        }

        /**
         * Browse the jobs available to this user (browse)
         *
         * ![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
         * ![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)
         *
         *
         */
        export function browse(
            request: JobsBrowseRequest
        ): APICallParameters<JobsBrowseRequest, PageV2<Job>> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/jobs" + "/browse", {
                    consistency: request.consistency,
                    filterAfter: request.filterAfter,
                    filterApplication: request.filterApplication,
                    filterBefore: request.filterBefore,
                    filterLaunchedBy: request.filterLaunchedBy,
                    filterState: request.filterState,
                    filterTitle: request.filterTitle,
                    includeApplication: request.includeApplication,
                    includeParameters: request.includeParameters,
                    includeProduct: request.includeProduct,
                    includeUpdates: request.includeUpdates,
                    itemsPerPage: request.itemsPerPage,
                    itemsToSkip: request.itemsToSkip,
                    next: request.next,
                    sortBy: request.sortBy
                }),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        /**
         * Extend the duration of one or more jobs (extend)
         *
         * ![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
         * ![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)
         *
         * This will extend the duration of one or more jobs in a bulk request. Extension of a job will add to
         * the current deadline of a job. Note that not all providers support this features. Providers which
         * do not support it will have it listed in their manifest. If a provider is asked to extend a deadline
         * when not supported it will send back a 400 bad request.
         *
         * This call makes no guarantee that all jobs are extended in a single transaction. If the provider
         * supports it, then all requests made against a single provider should be made in a single transaction.
         * Clients can determine if their extension request against a specific target was successful by checking
         * if the time remaining of the job has been updated.
         *
         * This call will return 2XX if all jobs have successfully been extended. The job will fail with a
         * status code from the provider one the first extension which fails. UCloud will not attempt to extend
         * more jobs after the first failure.
         */
        export function extend(
            request: BulkRequest<JobsExtendRequestItem>
        ): APICallParameters<BulkRequest<JobsExtendRequestItem>, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/jobs" + "/extend",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function openInteractiveSession(
            request: BulkRequest<JobsOpenInteractiveSessionRequestItem>
        ): APICallParameters<BulkRequest<JobsOpenInteractiveSessionRequestItem>, JobsOpenInteractiveSessionResponse> {
            return {
                context: "",
                method: "POST",
                path: "/api/jobs" + "/interactiveSession",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }
    }
    export namespace OpenSessionNS {
        export interface Shell {
            jobId: string,
            rank: number /* int32 */
            ,
            sessionIdentifier: string,
            type: "shell",
        }

        export interface Web {
            jobId: string,
            rank: number /* int32 */
            ,
            redirectClientTo: string,
            type: "web",
        }

        export interface Vnc {
            jobId: string,
            rank: number /* int32 */
            ,
            url: string,
            password?: string,
            type: "vnc",
        }
    }
    export namespace ApplicationParameterNS {
        export interface InputFile {
            name: string,
            optional: boolean,
            defaultValue?: any /* unknown */
            ,
            title: string,
            description: string,
            type: "input_file",
        }

        export interface InputDirectory {
            name: string,
            optional: boolean,
            defaultValue?: any /* unknown */
            ,
            title: string,
            description: string,
            type: "input_directory",
        }

        export interface Text {
            name: string,
            optional: boolean,
            defaultValue?: any /* unknown */
            ,
            title: string,
            description: string,
            type: "text",
        }

        export interface Integer {
            name: string,
            optional: boolean,
            defaultValue?: any /* unknown */
            ,
            title: string,
            description: string,
            min?: number /* int64 */
            ,
            max?: number /* int64 */
            ,
            step?: number /* int64 */
            ,
            unitName?: string,
            type: "integer",
        }

        export interface Bool {
            name: string,
            optional: boolean,
            defaultValue?: any /* unknown */
            ,
            title: string,
            description: string,
            trueValue: string,
            falseValue: string,
            type: "boolean",
        }

        export interface FloatingPoint {
            name: string,
            optional: boolean,
            defaultValue?: any /* unknown */
            ,
            title: string,
            description: string,
            min?: number /* float32 */
            ,
            max?: number /* float32 */
            ,
            step?: number /* float32 */
            ,
            unitName?: string,
            type: "floating_point",
        }

        export interface Peer {
            name: string,
            title: string,
            description: string,
            suggestedApplication?: string,
            defaultValue?: any /* unknown */
            ,
            optional: boolean,
            type: "peer",
        }

        export interface Enumeration {
            name: string,
            optional: boolean,
            defaultValue?: any /* unknown */
            ,
            title: string,
            description: string,
            options: EnumOption[],
            type: "enumeration",
        }

        export interface EnumOption {
            name: string,
            value: string,
        }

        export interface LicenseServer {
            name: string,
            title: string,
            optional: boolean,
            description: string,
            tagged: string[],
            defaultValue?: any /* unknown */
            ,
            type: "license_server",
        }
    }
}
export namespace project {
    export function create(
        request: CreateProjectRequest
    ): APICallParameters<CreateProjectRequest, FindByStringId> {
        return {
            context: "",
            method: "POST",
            path: "/api/projects",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function invite(
        request: InviteRequest
    ): APICallParameters<InviteRequest, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/api/projects" + "/invites",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function viewMemberInProject(
        request: ViewMemberInProjectRequest
    ): APICallParameters<ViewMemberInProjectRequest, ViewMemberInProjectResponse> {
        return {
            context: "",
            method: "GET",
            path: buildQueryString("/api/projects" + "/members", {
                projectId: request.projectId,
                username: request.username
            }),
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export function deleteMember(
        request: DeleteMemberRequest
    ): APICallParameters<DeleteMemberRequest, any /* unknown */> {
        return {
            context: "",
            method: "DELETE",
            path: "/api/projects" + "/members",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function exists(
        request: ExistsRequest
    ): APICallParameters<ExistsRequest, ExistsResponse> {
        return {
            context: "",
            method: "POST",
            path: "/api/projects" + "/exists",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function changeUserRole(
        request: ChangeUserRoleRequest
    ): APICallParameters<ChangeUserRoleRequest, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/api/projects" + "/members" + "/change-role",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function listFavoriteProjects(
        request: ListFavoriteProjectsRequest
    ): APICallParameters<ListFavoriteProjectsRequest, Page<UserProjectSummary>> {
        return {
            context: "",
            method: "GET",
            path: buildQueryString("/api/projects" + "/listFavorites", {
                itemsPerPage: request.itemsPerPage,
                page: request.page,
                user: request.user,
                archived: request.archived,
                showAncestorPath: request.showAncestorPath
            }),
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export function listProjects(
        request: ListProjectsRequest
    ): APICallParameters<ListProjectsRequest, Page<UserProjectSummary>> {
        return {
            context: "",
            method: "GET",
            path: buildQueryString("/api/projects" + "/list", {
                itemsPerPage: request.itemsPerPage,
                page: request.page,
                user: request.user,
                archived: request.archived,
                noFavorites: request.noFavorites,
                showAncestorPath: request.showAncestorPath
            }),
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export function verifyMembership(): APICallParameters<{}, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/api/projects" + "/verify-membership",
            reloadId: Math.random(),
        };
    }

    export function acceptInvite(
        request: AcceptInviteRequest
    ): APICallParameters<AcceptInviteRequest, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/api/projects" + "/invites" + "/accept",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function rejectInvite(
        request: RejectInviteRequest
    ): APICallParameters<RejectInviteRequest, any /* unknown */> {
        return {
            context: "",
            method: "DELETE",
            path: "/api/projects" + "/invites" + "/reject",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function leaveProject(): APICallParameters<{}, any /* unknown */> {
        return {
            context: "",
            method: "DELETE",
            path: "/api/projects" + "/leave",
            reloadId: Math.random(),
        };
    }

    export function listIngoingInvites(
        request: ListIngoingInvitesRequest
    ): APICallParameters<ListIngoingInvitesRequest, Page<IngoingInvite>> {
        return {
            context: "",
            method: "GET",
            path: buildQueryString("/api/projects" + "/invites" + "/ingoing", {
                itemsPerPage: request.itemsPerPage,
                page: request.page
            }),
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export function listOutgoingInvites(
        request: ListOutgoingInvitesRequest
    ): APICallParameters<ListOutgoingInvitesRequest, Page<OutgoingInvite>> {
        return {
            context: "",
            method: "GET",
            path: buildQueryString("/api/projects" + "/invites" + "/outgoing", {
                itemsPerPage: request.itemsPerPage,
                page: request.page
            }),
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export function transferPiRole(
        request: TransferPiRoleRequest
    ): APICallParameters<TransferPiRoleRequest, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/api/projects" + "/transfer-pi",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function archive(
        request: ArchiveRequest
    ): APICallParameters<ArchiveRequest, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/api/projects" + "/archive",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function archiveBulk(
        request: ArchiveBulkRequest
    ): APICallParameters<ArchiveBulkRequest, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/api/projects" + "/archiveBulk",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function viewProject(
        request: ViewProjectRequest
    ): APICallParameters<ViewProjectRequest, UserProjectSummary> {
        return {
            context: "",
            method: "GET",
            path: buildQueryString("/api/projects" + "/view", {id: request.id}),
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export function listSubProjects(
        request: ListSubProjectsRequest
    ): APICallParameters<ListSubProjectsRequest, Page<Project>> {
        return {
            context: "",
            method: "GET",
            path: buildQueryString("/api/projects" + "/sub-projects", {
                itemsPerPage: request.itemsPerPage,
                page: request.page
            }),
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export function countSubProjects(): APICallParameters<{}, number /* int64 */> {
        return {
            context: "",
            method: "GET",
            path: "/api/projects" + "/sub-projects-count",
            reloadId: Math.random(),
        };
    }

    export function viewAncestors(): APICallParameters<{}, Project[]> {
        return {
            context: "",
            method: "GET",
            path: "/api/projects" + "/ancestors",
            reloadId: Math.random(),
        };
    }

    export function lookupByPath(
        request: LookupByTitleRequest
    ): APICallParameters<LookupByTitleRequest, Project> {
        return {
            context: "",
            method: "GET",
            path: buildQueryString("/api/projects" + "/lookupByTitle", {title: request.title}),
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export function lookupById(
        request: LookupByIdRequest
    ): APICallParameters<LookupByIdRequest, Project> {
        return {
            context: "",
            method: "GET",
            path: buildQueryString("/api/projects" + "/lookupById", {id: request.id}),
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export function lookupByIdBulk(
        request: LookupByIdBulkRequest
    ): APICallParameters<LookupByIdBulkRequest, Project[]> {
        return {
            context: "",
            method: "POST",
            path: "/api/projects" + "/lookupByIdBulk",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function lookupPrincipalInvestigator(): APICallParameters<{}, LookupPrincipalInvestigatorResponse> {
        return {
            context: "",
            method: "GET",
            path: "/api/projects" + "/lookup-pi",
            reloadId: Math.random(),
        };
    }

    export function allowsRenaming(
        request: AllowsRenamingRequest
    ): APICallParameters<AllowsRenamingRequest, AllowsRenamingResponse> {
        return {
            context: "",
            method: "GET",
            path: buildQueryString("/api/projects" + "/renameable", {projectId: request.projectId}),
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export function allowsSubProjectRenaming(
        request: AllowsRenamingRequest
    ): APICallParameters<AllowsRenamingRequest, AllowsRenamingResponse> {
        return {
            context: "",
            method: "GET",
            path: buildQueryString("/api/projects" + "/renameable-sub", {projectId: request.projectId}),
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export function toggleRenaming(
        request: ToggleRenamingRequest
    ): APICallParameters<ToggleRenamingRequest, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/api/projects" + "/toggleRenaming",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function rename(
        request: RenameProjectRequest
    ): APICallParameters<RenameProjectRequest, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/api/projects" + "/rename",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function updateDataManagementPlan(
        request: UpdateDataManagementPlanRequest
    ): APICallParameters<UpdateDataManagementPlanRequest, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/api/projects" + "/update-dmp",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function fetchDataManagementPlan(): APICallParameters<{}, FetchDataManagementPlanResponse> {
        return {
            context: "",
            method: "GET",
            path: "/api/projects" + "/dmp",
            reloadId: Math.random(),
        };
    }

    export interface CreateProjectRequest {
        title: string,
        parent?: string,
        principalInvestigator?: string,
    }

    export interface InviteRequest {
        projectId: string,
        usernames: string[],
    }

    export interface DeleteMemberRequest {
        projectId: string,
        member: string,
    }

    export interface ViewMemberInProjectResponse {
        member: ProjectMember,
    }

    export interface ProjectMember {
        username: string,
        role: "PI" | "ADMIN" | "USER",
        memberOfAnyGroup?: boolean,
    }

    export interface ViewMemberInProjectRequest {
        projectId: string,
        username: string,
    }

    export interface ExistsResponse {
        exists: boolean,
    }

    export interface ExistsRequest {
        projectId: string,
    }

    export interface ChangeUserRoleRequest {
        projectId: string,
        member: string,
        newRole: "PI" | "ADMIN" | "USER",
    }

    export interface UserProjectSummary {
        projectId: string,
        title: string,
        whoami: ProjectMember,
        needsVerification: boolean,
        isFavorite: boolean,
        archived: boolean,
        parent?: string,
        ancestorPath?: string,
    }

    export interface ListFavoriteProjectsRequest {
        user?: string,
        itemsPerPage: number /* int32 */
        ,
        page: number /* int32 */
        ,
        archived: boolean,
        showAncestorPath?: boolean,
    }

    export interface ListProjectsRequest {
        user?: string,
        itemsPerPage?: number /* int32 */
        ,
        page?: number /* int32 */
        ,
        archived?: boolean,
        noFavorites?: boolean,
        showAncestorPath?: boolean,
    }

    export interface AcceptInviteRequest {
        projectId: string,
    }

    export interface RejectInviteRequest {
        username?: string,
        projectId: string,
    }

    export interface IngoingInvite {
        project: string,
        title: string,
        invitedBy: string,
        timestamp: number /* int64 */
        ,
    }

    export interface ListIngoingInvitesRequest {
        itemsPerPage?: number /* int32 */
        ,
        page?: number /* int32 */
        ,
    }

    export interface OutgoingInvite {
        username: string,
        invitedBy: string,
        timestamp: number /* int64 */
        ,
    }

    export interface ListOutgoingInvitesRequest {
        itemsPerPage?: number /* int32 */
        ,
        page?: number /* int32 */
        ,
    }

    export interface TransferPiRoleRequest {
        newPrincipalInvestigator: string,
    }

    export interface ArchiveRequest {
        archiveStatus: boolean,
    }

    export interface ArchiveBulkRequest {
        projects: UserProjectSummary[],
    }

    export interface ViewProjectRequest {
        id: string,
    }

    export interface Project {
        id: string,
        title: string,
        parent?: string,
        archived: boolean,
    }

    export interface ListSubProjectsRequest {
        itemsPerPage?: number /* int32 */
        ,
        page?: number /* int32 */
        ,
    }

    export interface LookupByTitleRequest {
        title: string,
    }

    export interface LookupByIdRequest {
        id: string,
    }

    export interface LookupByIdBulkRequest {
        ids: string[],
    }

    export interface LookupPrincipalInvestigatorResponse {
        principalInvestigator: string,
    }

    export interface AllowsRenamingResponse {
        allowed: boolean,
    }

    export interface AllowsRenamingRequest {
        projectId: string,
    }

    export interface ToggleRenamingRequest {
        projectId: string,
    }

    export interface RenameProjectRequest {
        id: string,
        newTitle: string,
    }

    export interface UpdateDataManagementPlanRequest {
        id: string,
        dmp?: string,
    }

    export interface FetchDataManagementPlanResponse {
        dmp?: string,
    }

    export interface CreateGroupRequest {
        group: string,
    }

    export interface DeleteGroupsRequest {
        groups: string[],
    }

    export interface GroupWithSummary {
        groupId: string,
        groupTitle: string,
        numberOfMembers: number /* int32 */
        ,
    }

    export interface ListGroupsWithSummaryRequest {
        itemsPerPage?: number /* int32 */
        ,
        page?: number /* int32 */
        ,
    }

    export interface AddGroupMemberRequest {
        group: string,
        memberUsername: string,
    }

    export interface RemoveGroupMemberRequest {
        group: string,
        memberUsername: string,
    }

    export interface ListGroupMembersRequest {
        group: string,
        itemsPerPage?: number /* int32 */
        ,
        page?: number /* int32 */
        ,
    }

    export interface UpdateGroupNameRequest {
        groupId: string,
        newGroupName: string,
    }

    export interface IsMemberResponse {
        responses: boolean[],
    }

    export interface IsMemberRequest {
        queries: IsMemberQuery[],
    }

    export interface IsMemberQuery {
        project: string,
        group: string,
        username: string,
    }

    export interface GroupExistsResponse {
        exists: boolean[],
    }

    export interface GroupExistsRequest {
        project: string,
        groups: string[],
    }

    export interface ViewGroupRequest {
        id: string,
    }

    export interface LookupByGroupTitleRequest {
        projectId: string,
        title: string,
    }

    export interface ProjectAndGroup {
        project: Project,
        group: ProjectGroup,
    }

    export interface ProjectGroup {
        id: string,
        title: string,
    }

    export interface LookupProjectAndGroupRequest {
        project: string,
        group: string,
    }

    export interface UserStatusResponse {
        membership: UserStatusInProject[],
        groups: UserGroupSummary[],
    }

    export interface UserStatusInProject {
        projectId: string,
        title: string,
        whoami: ProjectMember,
        parent?: string,
    }

    export interface UserGroupSummary {
        project: string,
        group: string,
        username: string,
    }

    export interface UserStatusRequest {
        username?: string,
    }

    export interface SearchRequest {
        query: string,
        notInGroup?: string,
        itemsPerPage?: number /* int32 */
        ,
        page?: number /* int32 */
        ,
    }

    export interface LookupAdminsResponse {
        admins: ProjectMember[],
    }

    export interface LookupAdminsRequest {
        projectId: string,
    }

    export interface LookupAdminsBulkResponse {
        admins: kotlin.Pair<string, ProjectMember[]>[],
    }

    export interface LookupAdminsBulkRequest {
        projectId: string[],
    }

    export namespace members {
        export function userStatus(
            request: UserStatusRequest
        ): APICallParameters<UserStatusRequest, UserStatusResponse> {
            return {
                context: "",
                method: "POST",
                path: "/api/projects/membership",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function search(
            request: SearchRequest
        ): APICallParameters<SearchRequest, Page<ProjectMember>> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/projects/membership" + "/search", {
                    query: request.query,
                    itemsPerPage: request.itemsPerPage,
                    page: request.page,
                    notInGroup: request.notInGroup
                }),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function count(): APICallParameters<{}, number /* int64 */> {
            return {
                context: "",
                method: "GET",
                path: "/api/projects/membership" + "/count",
                reloadId: Math.random(),
            };
        }

        export function lookupAdmins(
            request: LookupAdminsRequest
        ): APICallParameters<LookupAdminsRequest, LookupAdminsResponse> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/projects/membership" + "/lookup-admins", {projectId: request.projectId}),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function lookupAdminsBulk(
            request: LookupAdminsBulkRequest
        ): APICallParameters<LookupAdminsBulkRequest, LookupAdminsBulkResponse> {
            return {
                context: "",
                method: "POST",
                path: "/api/projects/membership" + "/lookup-admins",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }
    }
    export namespace favorite {
        export interface ToggleFavoriteRequest {
            projectId: string,
        }

        export namespace favorite {
            export function toggleFavorite(
                request: ToggleFavoriteRequest
            ): APICallParameters<ToggleFavoriteRequest, any /* unknown */> {
                return {
                    context: "",
                    method: "POST",
                    path: "/api/projects/favorite",
                    parameters: request,
                    reloadId: Math.random(),
                    payload: request,
                };
            }
        }
    }
    export namespace repository {
        export function list(
            request: RepositoryListRequest
        ): APICallParameters<RepositoryListRequest, Page<Repository>> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/projects/repositories", {
                    itemsPerPage: request.itemsPerPage,
                    page: request.page,
                    user: request.user
                }),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function create(
            request: RepositoryCreateRequest
        ): APICallParameters<RepositoryCreateRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/projects/repositories",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function remove(
            request: RepositoryDeleteRequest
        ): APICallParameters<RepositoryDeleteRequest, any /* unknown */> {
            return {
                context: "",
                method: "DELETE",
                path: "/api/projects/repositories",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function update(
            request: RepositoryUpdateRequest
        ): APICallParameters<RepositoryUpdateRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/projects/repositories" + "/update",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function listFiles(
            request: RepositoryListRequest
        ): APICallParameters<RepositoryListRequest, Page<file.StorageFile>> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/projects/repositories" + "/list-files", {
                    itemsPerPage: request.itemsPerPage,
                    page: request.page,
                    user: request.user
                }),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function updatePermissions(
            request: UpdatePermissionsRequest
        ): APICallParameters<UpdatePermissionsRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/projects/repositories" + "/update-permissions",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export interface RepositoryCreateRequest {
            name: string,
        }

        export interface RepositoryDeleteRequest {
            name: string,
        }

        export interface Repository {
            name: string,
        }

        export interface RepositoryListRequest {
            user?: string,
            itemsPerPage?: number /* int32 */
            ,
            page?: number /* int32 */
            ,
        }

        export interface RepositoryUpdateRequest {
            oldName: string,
            newName: string,
        }

        export interface UpdatePermissionsRequest {
            repository: string,
            newAcl: ProjectAclEntry[],
        }

        export interface ProjectAclEntry {
            group: string,
            rights: "READ" | "WRITE"[],
        }
    }
    export namespace group {
        export function remove(
            request: DeleteGroupsRequest
        ): APICallParameters<DeleteGroupsRequest, any /* unknown */> {
            return {
                context: "",
                method: "DELETE",
                path: "/api/projects/groups",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function create(
            request: CreateGroupRequest
        ): APICallParameters<CreateGroupRequest, FindByStringId> {
            return {
                context: "",
                method: "PUT",
                path: "/api/projects/groups",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function listGroupsWithSummary(
            request: ListGroupsWithSummaryRequest
        ): APICallParameters<ListGroupsWithSummaryRequest, Page<GroupWithSummary>> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/projects/groups" + "/summary", {
                    itemsPerPage: request.itemsPerPage,
                    page: request.page
                }),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function listGroupMembers(
            request: ListGroupMembersRequest
        ): APICallParameters<ListGroupMembersRequest, Page<string>> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/projects/groups" + "/members", {
                    group: request.group,
                    itemsPerPage: request.itemsPerPage,
                    page: request.page
                }),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function removeGroupMember(
            request: RemoveGroupMemberRequest
        ): APICallParameters<RemoveGroupMemberRequest, any /* unknown */> {
            return {
                context: "",
                method: "DELETE",
                path: "/api/projects/groups" + "/members",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function addGroupMember(
            request: AddGroupMemberRequest
        ): APICallParameters<AddGroupMemberRequest, any /* unknown */> {
            return {
                context: "",
                method: "PUT",
                path: "/api/projects/groups" + "/members",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function updateGroupName(
            request: UpdateGroupNameRequest
        ): APICallParameters<UpdateGroupNameRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/projects/groups" + "/update-name",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function listAllGroupMembers(): APICallParameters<{}, string[]> {
            return {
                context: "",
                method: "POST",
                path: "/api/projects/groups" + "/list-all-group-members",
                reloadId: Math.random(),
            };
        }

        export function isMember(
            request: IsMemberRequest
        ): APICallParameters<IsMemberRequest, IsMemberResponse> {
            return {
                context: "",
                method: "POST",
                path: "/api/projects/groups" + "/is-member",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function groupExists(
            request: GroupExistsRequest
        ): APICallParameters<GroupExistsRequest, GroupExistsResponse> {
            return {
                context: "",
                method: "POST",
                path: "/api/projects/groups" + "/exists",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function count(): APICallParameters<{}, number /* int64 */> {
            return {
                context: "",
                method: "GET",
                path: "/api/projects/groups" + "/count",
                reloadId: Math.random(),
            };
        }

        export function view(
            request: ViewGroupRequest
        ): APICallParameters<ViewGroupRequest, GroupWithSummary> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/projects/groups" + "/view", {id: request.id}),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function lookupByTitle(
            request: LookupByGroupTitleRequest
        ): APICallParameters<LookupByGroupTitleRequest, GroupWithSummary> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/projects/groups" + "/lookup-by-title", {
                    projectId: request.projectId,
                    title: request.title
                }),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function lookupProjectAndGroup(
            request: LookupProjectAndGroupRequest
        ): APICallParameters<LookupProjectAndGroupRequest, ProjectAndGroup> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/projects/groups" + "/lookup-project-and-group", {
                    project: request.project,
                    group: request.group
                }),
                parameters: request,
                reloadId: Math.random(),
            };
        }
    }
}
export namespace accounting {
    export interface AddToBalanceRequest {
        wallet: Wallet,
        credits: number /* int64 */
        ,
    }

    export interface Wallet {
        id: string,
        type: "USER" | "PROJECT",
        paysFor: ProductCategoryId,
    }

    export interface ProductCategoryId {
        id: string,
        provider: string,
    }

    export interface AddToBalanceBulkRequest {
        requests: AddToBalanceRequest[],
    }

    export interface ReserveCreditsRequest {
        jobId: string,
        amount: number /* int64 */
        ,
        expiresAt: number /* int64 */
        ,
        account: Wallet,
        jobInitiatedBy: string,
        productId: string,
        productUnits: number /* int64 */
        ,
        discardAfterLimitCheck: boolean,
        chargeImmediately: boolean,
        skipIfExists: boolean,
        skipLimitCheck: boolean,
        transactionType: "GIFTED" | "TRANSFERRED_TO_PERSONAL" | "TRANSFERRED_TO_PROJECT" | "PAYMENT",
    }

    export interface ReserveCreditsBulkRequest {
        reservations: ReserveCreditsRequest[],
    }

    export interface ChargeReservationRequest {
        name: string,
        amount: number /* int64 */
        ,
        productUnits: number /* int64 */
        ,
    }

    export interface TransferToPersonalRequest {
        transfers: SingleTransferRequest[],
    }

    export interface SingleTransferRequest {
        initiatedBy: string,
        amount: number /* int64 */
        ,
        sourceAccount: Wallet,
        destinationAccount: Wallet,
    }

    export interface RetrieveBalanceResponse {
        wallets: WalletBalance[],
    }

    export interface WalletBalance {
        wallet: Wallet,
        balance: number /* int64 */
        ,
        allocated: number /* int64 */
        ,
        used: number /* int64 */
        ,
        area: "STORAGE" | "COMPUTE" | "INGRESS" | "LICENSE",
    }

    export interface RetrieveBalanceRequest {
        id?: string,
        type?: "USER" | "PROJECT",
        includeChildren?: boolean,
    }

    export interface SetBalanceRequest {
        wallet: Wallet,
        lastKnownBalance: number /* int64 */
        ,
        newBalance: number /* int64 */
        ,
    }

    export type Product = ProductNS.Storage | ProductNS.Compute | ProductNS.Ingress | ProductNS.License
    export type ProductAvailability = ProductAvailabilityNS.Available | ProductAvailabilityNS.Unavailable

    export interface FindProductRequest {
        provider: string,
        productCategory: string,
        product: string,
    }

    export interface ListProductsRequest {
        provider: string,
        itemsPerPage?: number /* int32 */
        ,
        page?: number /* int32 */
        ,
    }

    export interface ListProductsByAreaRequest {
        provider: string,
        area: "STORAGE" | "COMPUTE" | "INGRESS" | "LICENSE",
        itemsPerPage?: number /* int32 */
        ,
        page?: number /* int32 */
        ,
    }

    export interface RetrieveAllFromProviderRequest {
        provider: string,
    }

    export interface ProductsBrowseRequest {
        itemsPerPage?: number /* int32 */
        ,
        next?: string,
        consistency?: "PREFER" | "REQUIRE",
        itemsToSkip?: number /* int64 */
        ,
        filterProvider?: string,
        filterArea?: "STORAGE" | "COMPUTE" | "INGRESS" | "LICENSE",
        filterUsable?: boolean,
        filterCategory?: string,
        includeBalance?: boolean,
    }

    export interface UsageResponse {
        charts: UsageChart[],
    }

    export interface UsageChart {
        provider: string,
        lines: UsageLine[],
    }

    export interface UsageLine {
        area: "STORAGE" | "COMPUTE" | "INGRESS" | "LICENSE",
        category: string,
        projectPath?: string,
        projectId?: string,
        points: UsagePoint[],
    }

    export interface UsagePoint {
        timestamp: number /* int64 */
        ,
        creditsUsed: number /* int64 */
        ,
    }

    export interface UsageRequest {
        bucketSize: number /* int64 */
        ,
        periodStart: number /* int64 */
        ,
        periodEnd: number /* int64 */
        ,
    }

    export interface ProductReference {
        id: string,
        category: string,
        provider: string,
    }

    export namespace products {
        export function findProduct(
            request: FindProductRequest
        ): APICallParameters<FindProductRequest, Product> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/products", {
                    provider: request.provider,
                    productCategory: request.productCategory,
                    product: request.product
                }),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function updateProduct(
            request: Product
        ): APICallParameters<Product, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/products",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function createProduct(
            request: Product
        ): APICallParameters<Product, any /* unknown */> {
            return {
                context: "",
                method: "PUT",
                path: "/api/products",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function listProducts(
            request: ListProductsRequest
        ): APICallParameters<ListProductsRequest, Page<Product>> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/products" + "/list", {
                    provider: request.provider,
                    itemsPerPage: request.itemsPerPage,
                    page: request.page
                }),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function listProductionsByType(
            request: ListProductsByAreaRequest
        ): APICallParameters<ListProductsByAreaRequest, Page<Product>> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/products" + "/listByArea", {
                    provider: request.provider,
                    area: request.area,
                    itemsPerPage: request.itemsPerPage,
                    page: request.page
                }),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function retrieveAllFromProvider(
            request: RetrieveAllFromProviderRequest
        ): APICallParameters<RetrieveAllFromProviderRequest, Product[]> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/products" + "/retrieve", {provider: request.provider}),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function browse(
            request: ProductsBrowseRequest
        ): APICallParameters<ProductsBrowseRequest, PageV2<Product>> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/products" + "/browse", {
                    consistency: request.consistency,
                    filterArea: request.filterArea,
                    filterCategory: request.filterCategory,
                    filterProvider: request.filterProvider,
                    filterUsable: request.filterUsable,
                    includeBalance: request.includeBalance,
                    itemsPerPage: request.itemsPerPage,
                    itemsToSkip: request.itemsToSkip,
                    next: request.next
                }),
                parameters: request,
                reloadId: Math.random(),
            };
        }
    }
    export namespace ProductAvailabilityNS {
        export interface Available {
            type: "available",
        }

        export interface Unavailable {
            reason: string,
            type: "unavailable",
        }
    }
    export namespace visualization {
        export function usage(
            request: UsageRequest
        ): APICallParameters<UsageRequest, UsageResponse> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/accounting/visualization" + "/usage", {
                    bucketSize: request.bucketSize,
                    periodEnd: request.periodEnd,
                    periodStart: request.periodStart
                }),
                parameters: request,
                reloadId: Math.random(),
            };
        }
    }
    export namespace wallets {
        export function addToBalance(
            request: AddToBalanceRequest
        ): APICallParameters<AddToBalanceRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/accounting/wallets" + "/add-credits",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function addToBalanceBulk(
            request: AddToBalanceBulkRequest
        ): APICallParameters<AddToBalanceBulkRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/accounting/wallets" + "/add-credits-bulk",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function reserveCredits(
            request: ReserveCreditsRequest
        ): APICallParameters<ReserveCreditsRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/accounting/wallets" + "/reserve-credits",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function reserveCreditsBulk(
            request: ReserveCreditsBulkRequest
        ): APICallParameters<ReserveCreditsBulkRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/accounting/wallets" + "/reserve-credits-bulk",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function chargeReservation(
            request: ChargeReservationRequest
        ): APICallParameters<ChargeReservationRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/accounting/wallets" + "/charge-reservation",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function transferToPersonal(
            request: TransferToPersonalRequest
        ): APICallParameters<TransferToPersonalRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/accounting/wallets" + "/transfer",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function retrieveBalance(
            request: RetrieveBalanceRequest
        ): APICallParameters<RetrieveBalanceRequest, RetrieveBalanceResponse> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/accounting/wallets" + "/balance", {
                    id: request.id,
                    type: request.type,
                    includeChildren: request.includeChildren
                }),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function setBalance(
            request: SetBalanceRequest
        ): APICallParameters<SetBalanceRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/accounting/wallets" + "/set-balance",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }
    }
    export namespace ProductNS {
        export interface Storage {
            id: string,
            pricePerUnit: number /* int64 */
            ,
            category: ProductCategoryId,
            description: string,
            availability: ProductAvailability,
            priority: number /* int32 */
            ,
            /**
             * Included only with certain endpoints which support `includeBalance`
             */
            balance?: number /* int64 */
            ,
            type: "storage",
        }

        export interface Compute {
            id: string,
            pricePerUnit: number /* int64 */
            ,
            category: ProductCategoryId,
            description: string,
            availability: ProductAvailability,
            priority: number /* int32 */
            ,
            cpu?: number /* int32 */
            ,
            memoryInGigs?: number /* int32 */
            ,
            gpu?: number /* int32 */
            ,
            /**
             * Included only with certain endpoints which support `includeBalance`
             */
            balance?: number /* int64 */
            ,
            type: "compute",
        }

        export interface Ingress {
            id: string,
            pricePerUnit: number /* int64 */
            ,
            category: ProductCategoryId,
            description: string,
            availability: ProductAvailability,
            priority: number /* int32 */
            ,
            paymentModel: "FREE_BUT_REQUIRE_BALANCE" | "PER_ACTIVATION",
            /**
             * Included only with certain endpoints which support `includeBalance`
             */
            balance?: number /* int64 */
            ,
            type: "ingress",
        }

        export interface License {
            id: string,
            pricePerUnit: number /* int64 */
            ,
            category: ProductCategoryId,
            description: string,
            availability: ProductAvailability,
            priority: number /* int32 */
            ,
            tags: string[],
            paymentModel: "FREE_BUT_REQUIRE_BALANCE" | "PER_ACTIVATION",
            /**
             * Included only with certain endpoints which support `includeBalance`
             */
            balance?: number /* int64 */
            ,
            type: "license",
        }
    }
}
export namespace password {

    export namespace reset {
        export function reset(
            request: PasswordResetRequest
        ): APICallParameters<PasswordResetRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/password/reset",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function newPassword(
            request: NewPasswordRequest
        ): APICallParameters<NewPasswordRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/password/reset" + "/new",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export interface PasswordResetRequest {
            email: string,
        }

        export interface NewPasswordRequest {
            token: string,
            newPassword: string,
        }
    }
}
export namespace activity {
    export function listByPath(
        request: ListActivityByPathRequest
    ): APICallParameters<ListActivityByPathRequest, Page<ActivityForFrontend>> {
        return {
            context: "",
            method: "GET",
            path: buildQueryString("/api/activity" + "/by-path", {
                itemsPerPage: request.itemsPerPage,
                page: request.page,
                path: request.path
            }),
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export function activityFeed(
        request: ActivityNS.BrowseByUserNS.Request
    ): APICallParameters<ActivityNS.BrowseByUserNS.Request, ActivityNS.BrowseByUserNS.Response> {
        return {
            context: "",
            method: "GET",
            path: buildQueryString("/api/activity" + "/browse", {
                user: request.user,
                offset: request.offset,
                scrollSize: request.scrollSize,
                type: request.type,
                minTimestamp: request.minTimestamp,
                maxTimestamp: request.maxTimestamp
            }),
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export interface ActivityForFrontend {
        type: "download" | "deleted" | "favorite" | "moved" | "copy" | "usedInApp" | "directoryCreated" | "reclassify" | "upload" | "updatedACL" | "sharedWith" | "allUsedInApp",
        timestamp: number /* int64 */
        ,
        activityEvent: ActivityEvent,
    }

    export interface ActivityEvent {
        filePath: string,
        timestamp: number /* int64 */
        ,
        username: string,
    }

    export interface ListActivityByPathRequest {
        path: string,
        itemsPerPage?: number /* int32 */
        ,
        page?: number /* int32 */
        ,
    }

    export namespace ActivityNS {

        export namespace BrowseByUserNS {
            export interface Response {
                endOfScroll: boolean,
                items: ActivityForFrontend[],
                nextOffset: number /* int32 */
                ,
            }

            export interface Request {
                user?: string,
                type?: "download" | "deleted" | "favorite" | "moved" | "copy" | "usedInApp" | "directoryCreated" | "reclassify" | "upload" | "updatedACL" | "sharedWith" | "allUsedInApp",
                minTimestamp?: number /* int64 */
                ,
                maxTimestamp?: number /* int64 */
                ,
                offset?: number /* int32 */
                ,
                scrollSize?: number /* int32 */
                ,
            }
        }
    }
}
export namespace notification {
    export function list(
        request: ListNotificationRequest
    ): APICallParameters<ListNotificationRequest, Page<Notification>> {
        return {
            context: "",
            method: "GET",
            path: buildQueryString("/api/notifications", {
                type: request.type,
                since: request.since,
                itemsPerPage: request.itemsPerPage,
                page: request.page
            }),
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export function create(
        request: CreateNotification
    ): APICallParameters<CreateNotification, FindByLongId> {
        return {
            context: "",
            method: "PUT",
            path: "/api/notifications",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function markAsRead(
        request: MarkAsReadRequest
    ): APICallParameters<MarkAsReadRequest, MarkResponse> {
        return {
            context: "",
            method: "POST",
            path: "/api/notifications" + "/read" + "/" + request.bulkId,
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export function markAllAsRead(): APICallParameters<{}, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/api/notifications" + "/read" + "/all",
            reloadId: Math.random(),
        };
    }

    export function remove(
        request: DeleteNotificationRequest
    ): APICallParameters<DeleteNotificationRequest, DeleteResponse> {
        return {
            context: "",
            method: "DELETE",
            path: "/api/notifications" + "/" + request.bulkId,
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export interface Notification {
        type: string,
        message: string,
        id?: number /* int64 */
        ,
        meta: Record<string, any /* unknown */>,
        ts: number /* int64 */
        ,
        read: boolean,
    }

    export interface ListNotificationRequest {
        type?: string,
        since?: number /* int64 */
        ,
        itemsPerPage?: number /* int32 */
        ,
        page?: number /* int32 */
        ,
    }

    export interface CreateNotification {
        user: string,
        notification: Notification,
    }

    export interface MarkResponse {
        failures: number /* int64 */[],
    }

    export interface MarkAsReadRequest {
        bulkId: FindByNotificationIdBulk,
    }

    export interface FindByNotificationIdBulk {
        ids: number /* int64 */[],
    }

    export interface DeleteResponse {
        failures: number /* int64 */[],
    }

    export interface DeleteNotificationRequest {
        bulkId: FindByNotificationIdBulk,
    }
}
export namespace share {
    export function list(
        request: SharesNS.ListNS.Request
    ): APICallParameters<SharesNS.ListNS.Request, Page<SharesByPath>> {
        return {
            context: "",
            method: "GET",
            path: buildQueryString("/api/shares", {
                sharedByMe: request.sharedByMe,
                itemsPerPage: request.itemsPerPage,
                page: request.page
            }),
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export function create(
        request: SharesNS.CreateNS.Request
    ): APICallParameters<SharesNS.CreateNS.Request, any /* unknown */> {
        return {
            context: "",
            method: "PUT",
            path: "/api/shares",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function accept(
        request: SharesNS.AcceptNS.Request
    ): APICallParameters<SharesNS.AcceptNS.Request, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/api/shares" + "/accept",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function revoke(
        request: SharesNS.RevokeNS.Request
    ): APICallParameters<SharesNS.RevokeNS.Request, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/api/shares" + "/revoke",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function update(
        request: SharesNS.UpdateNS.Request
    ): APICallParameters<SharesNS.UpdateNS.Request, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/api/shares" + "/update",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function findByPath(
        request: SharesNS.FindByPathNS.Request
    ): APICallParameters<SharesNS.FindByPathNS.Request, SharesByPath> {
        return {
            context: "",
            method: "GET",
            path: buildQueryString("/api/shares" + "/byPath", {path: request.path}),
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export function listFiles(
        request: SharesNS.ListFilesNS.Request
    ): APICallParameters<SharesNS.ListFilesNS.Request, Page<file.StorageFile>> {
        return {
            context: "",
            method: "GET",
            path: buildQueryString("/api/shares" + "/list-files", {
                itemsPerPage: request.itemsPerPage,
                page: request.page
            }),
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export interface SharesByPath {
        path: string,
        sharedBy: string,
        sharedByMe: boolean,
        shares: MinimalShare[],
    }

    export interface MinimalShare {
        sharedWith: string,
        rights: "READ" | "WRITE"[],
        state: "REQUEST_SENT" | "UPDATING" | "ACCEPTED",
    }

    export namespace SharesNS {

        export namespace ListFilesNS {
            export interface Request {
                itemsPerPage?: number /* int32 */
                ,
                page?: number /* int32 */
                ,
            }
        }
        export namespace FindByPathNS {
            export interface Request {
                path: string,
            }
        }
        export namespace RevokeNS {
            export interface Request {
                path: string,
                sharedWith: string,
            }
        }
        export namespace UpdateNS {
            export interface Request {
                path: string,
                sharedWith: string,
                rights: "READ" | "WRITE"[],
            }
        }
        export namespace AcceptNS {
            export interface Request {
                path: string,
            }
        }
        export namespace CreateNS {
            export interface Request {
                sharedWith: string,
                path: string,
                rights: "READ" | "WRITE"[],
            }
        }
        export namespace ListNS {
            export interface Request {
                sharedByMe: boolean,
                itemsPerPage?: number /* int32 */
                ,
                page?: number /* int32 */
                ,
            }
        }
    }
}
export namespace file {
    export function createPersonalRepository(
        request: CreatePersonalRepositoryRequest
    ): APICallParameters<CreatePersonalRepositoryRequest, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/api/files" + "/create-personal-repository",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function createDirectory(
        request: CreateDirectoryRequest
    ): APICallParameters<CreateDirectoryRequest, LongRunningResponse<any /* unknown */>> {
        return {
            context: "",
            method: "POST",
            path: "/api/files" + "/directory",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function listAtPath(
        request: ListDirectoryRequest
    ): APICallParameters<ListDirectoryRequest, Page<StorageFile>> {
        return {
            context: "",
            method: "GET",
            path: buildQueryString("/api/files", {
                path: request.path,
                itemsPerPage: request.itemsPerPage,
                page: request.page,
                order: request.order,
                sortBy: request.sortBy,
                attributes: request.attributes,
                type: request.type
            }),
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export function deleteFile(
        request: DeleteFileRequest
    ): APICallParameters<DeleteFileRequest, LongRunningResponse<any /* unknown */>> {
        return {
            context: "",
            method: "DELETE",
            path: "/api/files",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function move(
        request: MoveRequest
    ): APICallParameters<MoveRequest, LongRunningResponse<any /* unknown */>> {
        return {
            context: "",
            method: "POST",
            path: buildQueryString("/api/files" + "/move", {
                path: request.path,
                newPath: request.newPath,
                policy: request.policy
            }),
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export function copy(
        request: CopyRequest
    ): APICallParameters<CopyRequest, LongRunningResponse<any /* unknown */>> {
        return {
            context: "",
            method: "POST",
            path: buildQueryString("/api/files" + "/copy", {
                path: request.path,
                newPath: request.newPath,
                policy: request.policy
            }),
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export function normalizePermissions(
        request: NormalizePermissionsRequest
    ): APICallParameters<NormalizePermissionsRequest, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/api/files" + "/normalize-permissions",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function retrieveQuota(
        request: RetrieveQuotaRequest
    ): APICallParameters<RetrieveQuotaRequest, Quota> {
        return {
            context: "",
            method: "GET",
            path: buildQueryString("/api/files" + "/quota", {path: request.path, includeUsage: request.includeUsage}),
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export function updateQuota(
        request: UpdateQuotaRequest
    ): APICallParameters<UpdateQuotaRequest, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/api/files" + "/quota",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function transferQuota(
        request: TransferQuotaRequest
    ): APICallParameters<TransferQuotaRequest, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/api/files" + "/transfer-quota",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function lookupFileInDirectory(
        request: LookupFileInDirectoryRequest
    ): APICallParameters<LookupFileInDirectoryRequest, Page<StorageFile>> {
        return {
            context: "",
            method: "GET",
            path: buildQueryString("/api/files" + "/lookup", {
                path: request.path,
                itemsPerPage: request.itemsPerPage,
                sortBy: request.sortBy,
                order: request.order,
                attributes: request.attributes
            }),
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export function stat(
        request: StatRequest
    ): APICallParameters<StatRequest, StorageFile> {
        return {
            context: "",
            method: "GET",
            path: buildQueryString("/api/files" + "/stat", {path: request.path, attributes: request.attributes}),
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export function findHomeFolder(
        request: FindHomeFolderRequest
    ): APICallParameters<FindHomeFolderRequest, FindHomeFolderResponse> {
        return {
            context: "",
            method: "GET",
            path: buildQueryString("/api/files" + "/homeFolder", {username: request.username}),
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export function updateAcl(
        request: UpdateAclRequest
    ): APICallParameters<UpdateAclRequest, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/api/files" + "/update-acl",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function updateProjectAcl(
        request: UpdateProjectAclRequest
    ): APICallParameters<UpdateProjectAclRequest, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/api/files" + "/update-project-acl",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function reclassify(
        request: ReclassifyRequest
    ): APICallParameters<ReclassifyRequest, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/api/files" + "/reclassify",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function verifyFileKnowledge(
        request: VerifyFileKnowledgeRequest
    ): APICallParameters<VerifyFileKnowledgeRequest, VerifyFileKnowledgeResponse> {
        return {
            context: "",
            method: "POST",
            path: "/api/files" + "/verify-knowledge",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function download(
        request: DownloadByURI
    ): APICallParameters<DownloadByURI, BinaryStream> {
        return {
            context: "",
            method: "GET",
            path: buildQueryString("/api/files" + "/download", {path: request.path, token: request.token}),
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export function extract(
        request: ExtractRequest
    ): APICallParameters<ExtractRequest, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/api/files" + "/extract",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export interface StorageFile {
        acl?: AccessEntry[],
        createdAt?: number /* int64 */
        ,
        fileType?: "FILE" | "DIRECTORY",
        modifiedAt?: number /* int64 */
        ,
        ownSensitivityLevel?: "PRIVATE" | "CONFIDENTIAL" | "SENSITIVE",
        ownerName?: string,
        path?: string,
        permissionAlert: boolean,
        sensitivityLevel?: "PRIVATE" | "CONFIDENTIAL" | "SENSITIVE",
        size?: number /* int64 */
        ,
    }

    export interface AccessEntry {
        entity: ACLEntity,
        rights: "READ" | "WRITE"[],
    }

    export interface ACLEntity {
    }

    export interface CreatePersonalRepositoryRequest {
        project: string,
        username: string,
    }

    export interface LongRunningResponse<T> {
        type: string;
    }

    export interface CreateDirectoryRequest {
        path: string,
        owner?: string,
        sensitivity?: "PRIVATE" | "CONFIDENTIAL" | "SENSITIVE",
    }

    export interface DeleteFileRequest {
        path: string,
    }

    export interface ListDirectoryRequest {
        path: string,
        itemsPerPage?: number /* int32 */
        ,
        page?: number /* int32 */
        ,
        order?: "ASCENDING" | "DESCENDING",
        sortBy?: "TYPE" | "PATH" | "CREATED_AT" | "MODIFIED_AT" | "SIZE" | "SENSITIVITY",
        attributes?: string,
        type?: "FILE" | "DIRECTORY",
    }

    export interface MoveRequest {
        path: string,
        newPath: string,
        policy?: "OVERWRITE" | "MERGE" | "RENAME" | "REJECT",
    }

    export interface CopyRequest {
        path: string,
        newPath: string,
        policy?: "OVERWRITE" | "MERGE" | "RENAME" | "REJECT",
    }

    export interface NormalizePermissionsRequest {
        path: string,
    }

    export interface UpdateQuotaRequest {
        path: string,
        quotaInBytes: number /* int64 */
        ,
        additive: boolean,
    }

    export interface Quota {
        quotaInTotal: number /* int64 */
        ,
        remainingQuota: number /* int64 */
        ,
        allocatedToSubProjects: number /* int64 */
        ,
        inProjectUsage?: number /* int64 */
        ,
        allocated: number /* int64 */
        ,
        quotaInBytes: number /* int64 */
        ,
        quotaUsed?: number /* int64 */
        ,
    }

    export interface RetrieveQuotaRequest {
        path: string,
        includeUsage: boolean,
    }

    export interface TransferQuotaRequest {
        path: string,
        quotaInBytes: number /* int64 */
        ,
    }

    export interface LookupFileInDirectoryRequest {
        path: string,
        itemsPerPage: number /* int32 */
        ,
        order: "ASCENDING" | "DESCENDING",
        sortBy: "TYPE" | "PATH" | "CREATED_AT" | "MODIFIED_AT" | "SIZE" | "SENSITIVITY",
        attributes?: string,
    }

    export interface StatRequest {
        path: string,
        attributes?: string,
    }

    export interface FindHomeFolderResponse {
        path: string,
    }

    export interface FindHomeFolderRequest {
        username: string,
    }

    export interface UpdateAclRequest {
        path: string,
        changes: AclEntryRequest[],
    }

    export interface AclEntryRequest {
        entity: ACLEntityNS.User,
        rights: "READ" | "WRITE"[],
        revoke: boolean,
    }

    export interface UpdateProjectAclRequest {
        path: string,
        project: string,
        newAcl: ProjectAclEntryRequest[],
    }

    export interface ProjectAclEntryRequest {
        group: string,
        rights: "READ" | "WRITE"[],
    }

    export interface ReclassifyRequest {
        path: string,
        sensitivity?: "PRIVATE" | "CONFIDENTIAL" | "SENSITIVE",
    }

    export interface VerifyFileKnowledgeResponse {
        responses: boolean[],
    }

    export interface VerifyFileKnowledgeRequest {
        user: string,
        files: string[],
        mode?: KnowledgeMode,
    }

    export type KnowledgeMode = KnowledgeModeNS.List | KnowledgeModeNS.Permission

    export interface DownloadByURI {
        path: string,
        token?: string,
    }

    export interface SimpleUploadRequest {
        location: string,
        file: BinaryStream,
        policy?: "OVERWRITE" | "MERGE" | "RENAME" | "REJECT",
        sensitivity?: "PRIVATE" | "CONFIDENTIAL" | "SENSITIVE",
    }

    export interface BulkUploadErrorMessage {
        message: string,
    }

    export interface SimpleBulkUpload {
        location: string,
        format: string,
        file: BinaryStream,
        name?: string,
        policy?: "OVERWRITE" | "MERGE" | "RENAME" | "REJECT",
        sensitivity?: "PRIVATE" | "CONFIDENTIAL" | "SENSITIVE",
    }

    export interface ExtractRequest {
        path: string,
        removeOriginalArchive?: boolean,
    }

    export interface FindMetadataResponse {
        metadata: MetadataUpdate[],
    }

    export interface MetadataUpdate {
        path: string,
        type: string,
        username?: string,
        jsonPayload: string,
    }

    export interface FindMetadataRequest {
        path?: string,
        type?: string,
        username?: string,
    }

    export interface FindByPrefixRequest {
        pathPrefix: string,
        username?: string,
        type?: string,
    }

    export interface RemoveMetadataRequest {
        updates: FindMetadataRequest[],
    }

    export interface UpdateMetadataRequest {
        updates: MetadataUpdate[],
    }

    export interface CreateMetadataRequest {
        updates: MetadataUpdate[],
    }

    export interface VerifyRequest {
        paths: string[],
    }

    export namespace metadata {
        export function findMetadata(
            request: FindMetadataRequest
        ): APICallParameters<FindMetadataRequest, FindMetadataResponse> {
            return {
                context: "",
                method: "POST",
                path: "/api/files/metadata" + "/find",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function findByPrefix(
            request: FindByPrefixRequest
        ): APICallParameters<FindByPrefixRequest, FindMetadataResponse> {
            return {
                context: "",
                method: "POST",
                path: "/api/files/metadata" + "/by-prefix",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function updateMetadata(
            request: UpdateMetadataRequest
        ): APICallParameters<UpdateMetadataRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/files/metadata",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function removeMetadata(
            request: RemoveMetadataRequest
        ): APICallParameters<RemoveMetadataRequest, any /* unknown */> {
            return {
                context: "",
                method: "DELETE",
                path: "/api/files/metadata",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function createMetadata(
            request: CreateMetadataRequest
        ): APICallParameters<CreateMetadataRequest, any /* unknown */> {
            return {
                context: "",
                method: "PUT",
                path: "/api/files/metadata",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function verify(
            request: VerifyRequest
        ): APICallParameters<VerifyRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/files/metadata" + "/verify",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }
    }
    export namespace stats {
        export function usage(
            request: UsageRequest
        ): APICallParameters<UsageRequest, UsageResponse> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/files/stats" + "/usage", {path: request.path}),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function directorySize(
            request: DirectorySizesRequest
        ): APICallParameters<DirectorySizesRequest, DirectorySizesResponse> {
            return {
                context: "",
                method: "POST",
                path: "/api/files/stats" + "/directory-sizes",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export interface UsageResponse {
            bytes: number /* int64 */
            ,
            path: string,
        }

        export interface UsageRequest {
            path?: string,
        }

        export interface DirectorySizesResponse {
            size: number /* int64 */
            ,
        }

        export interface DirectorySizesRequest {
            paths: string[],
        }
    }
    export namespace favorite {
        export function toggleFavorite(
            request: ToggleFavoriteRequest
        ): APICallParameters<ToggleFavoriteRequest, ToggleFavoriteResponse> {
            return {
                context: "",
                method: "POST",
                path: buildQueryString("/api/files/favorite" + "/toggle", {path: request.path}),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function favoriteStatus(
            request: FavoriteStatusRequest
        ): APICallParameters<FavoriteStatusRequest, FavoriteStatusResponse> {
            return {
                context: "",
                method: "POST",
                path: "/api/files/favorite" + "/status",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function list(
            request: PaginationRequest
        ): APICallParameters<PaginationRequest, Page<StorageFile>> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/files/favorite" + "/list", {
                    itemsPerPage: request.itemsPerPage,
                    page: request.page
                }),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export interface ToggleFavoriteResponse {
            failures: string[],
        }

        export interface ToggleFavoriteRequest {
            path: string,
        }

        export interface FavoriteStatusResponse {
            favorited: Record<string, boolean>,
        }

        export interface FavoriteStatusRequest {
            files: string[],
        }
    }
    export namespace LongRunningResponseNS {

        export interface Timeout<T> extends LongRunningResponse<T> {
            type: "timeout"
        }

        export interface Result<T> extends LongRunningResponse<T> {
            type: "result"
        }
    }
    export namespace KnowledgeModeNS {
        export interface List {
            type: "list",
        }

        export interface Permission {
            requireWrite: boolean,
            type: "permission",
        }
    }
    export namespace upload {
        export function simpleUpload(
            request: SimpleUploadRequest
        ): APICallParameters<SimpleUploadRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/files/upload" + "/file",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function simpleBulkUpload(
            request: SimpleBulkUpload
        ): APICallParameters<SimpleBulkUpload, BulkUploadErrorMessage> {
            return {
                context: "",
                method: "POST",
                path: "/api/files/upload" + "/archive",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }
    }
    export namespace ACLEntityNS {
        export interface User {
            username: string,
        }
    }
    export namespace trash {
        export function trash(
            request: TrashRequest
        ): APICallParameters<TrashRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/files/trash",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function clear(
            request: ClearRequest
        ): APICallParameters<ClearRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/files/trash" + "/clear",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export interface TrashRequest {
            files: string[],
        }

        export interface ClearRequest {
            trashPath?: string,
        }
    }
}
export namespace grant {
    export interface ApproveApplicationRequest {
        requestId: number /* int64 */
        ,
    }

    export interface RejectApplicationRequest {
        requestId: number /* int64 */
        ,
    }

    export interface CloseApplicationRequest {
        requestId: number /* int64 */
        ,
    }

    export interface CommentOnApplicationRequest {
        requestId: number /* int64 */
        ,
        comment: string,
    }

    export interface DeleteCommentRequest {
        commentId: number /* int64 */
        ,
    }

    export interface CreateApplication {
        resourcesOwnedBy: string,
        grantRecipient: GrantRecipient,
        document: string,
        requestedResources: ResourceRequest[],
    }

    export type GrantRecipient =
        GrantRecipientNS.PersonalProject
        | GrantRecipientNS.ExistingProject
        | GrantRecipientNS.NewProject

    export interface ResourceRequest {
        productCategory: string,
        productProvider: string,
        creditsRequested?: number /* int64 */
        ,
        quotaRequested?: number /* int64 */
        ,
    }

    export interface EditApplicationRequest {
        id: number /* int64 */
        ,
        newDocument: string,
        newResources: ResourceRequest[],
    }

    export interface UploadTemplatesRequest {
        personalProject: string,
        newProject: string,
        existingProject: string,
    }

    export interface ProjectApplicationSettings {
        automaticApproval: AutomaticApprovalSettings,
        allowRequestsFrom: UserCriteria[],
        excludeRequestsFrom: UserCriteria[],
    }

    export interface AutomaticApprovalSettings {
        from: UserCriteria[],
        maxResources: ResourceRequest[],
    }

    export type UserCriteria = UserCriteriaNS.Anyone | UserCriteriaNS.EmailDomain | UserCriteriaNS.WayfOrganization

    export interface ReadRequestSettingsRequest {
        projectId: string,
    }

    export interface ReadTemplatesRequest {
        projectId: string,
    }

    export interface Application {
        status: "APPROVED" | "REJECTED" | "CLOSED" | "IN_PROGRESS",
        resourcesOwnedBy: string,
        requestedBy: string,
        grantRecipient: GrantRecipient,
        document: string,
        requestedResources: ResourceRequest[],
        id: number /* int64 */
        ,
        resourcesOwnedByTitle: string,
        grantRecipientPi: string,
        grantRecipientTitle: string,
        createdAt: number /* int64 */
        ,
        updatedAt: number /* int64 */
        ,
        statusChangedBy?: string,
    }

    export interface IngoingApplicationsRequest {
        itemsPerPage?: number /* int32 */
        ,
        page?: number /* int32 */
        ,
        filter: "SHOW_ALL" | "ACTIVE" | "INACTIVE",
    }

    export interface OutgoingApplicationsRequest {
        itemsPerPage?: number /* int32 */
        ,
        page?: number /* int32 */
        ,
        filter: "SHOW_ALL" | "ACTIVE" | "INACTIVE",
    }

    export interface ApplicationWithComments {
        application: Application,
        comments: Comment[],
        approver: boolean,
    }

    export interface Comment {
        id: number /* int64 */
        ,
        postedBy: string,
        postedAt: number /* int64 */
        ,
        comment: string,
    }

    export interface ViewApplicationRequest {
        id: number /* int64 */
        ,
    }

    export interface SetEnabledStatusRequest {
        projectId: string,
        enabledStatus: boolean,
    }

    export interface IsEnabledResponse {
        enabled: boolean,
    }

    export interface IsEnabledRequest {
        projectId: string,
    }

    export interface ProjectWithTitle {
        projectId: string,
        title: string,
    }

    export interface BrowseProjectsRequest {
        itemsPerPage?: number /* int32 */
        ,
        page?: number /* int32 */
        ,
    }

    export interface UploadLogoRequest {
        projectId: string,
        data: BinaryStream,
    }

    export interface FetchLogoRequest {
        projectId: string,
    }

    export interface UploadDescriptionRequest {
        projectId: string,
        description: string,
    }

    export interface FetchDescriptionResponse {
        description: string,
    }

    export interface FetchDescriptionRequest {
        projectId: string,
    }

    export interface GrantsRetrieveProductsResponse {
        availableProducts: accounting.Product[],
    }

    export interface GrantsRetrieveProductsRequest {
        projectId: string,
        recipientType: string,
        recipientId: string,
    }

    export interface AvailableGiftsResponse {
        gifts: GiftWithId[],
    }

    export interface GiftWithId {
        id: number /* int64 */
        ,
        resourcesOwnedBy: string,
        title: string,
        description: string,
        resources: ResourceRequest[],
    }

    export interface ClaimGiftRequest {
        giftId: number /* int64 */
        ,
    }

    export interface GiftWithCriteria {
        id: number /* int64 */
        ,
        resourcesOwnedBy: string,
        title: string,
        description: string,
        resources: ResourceRequest[],
        criteria: UserCriteria[],
    }

    export interface DeleteGiftRequest {
        giftId: number /* int64 */
        ,
    }

    export interface ListGiftsResponse {
        gifts: GiftWithCriteria[],
    }

    export namespace UserCriteriaNS {
        export interface Anyone {
            type: "anyone",
        }

        export interface EmailDomain {
            domain: string,
            type: "email",
        }

        export interface WayfOrganization {
            org: string,
            type: "wayf",
        }
    }
    export namespace grant {
        export function approveApplication(
            request: ApproveApplicationRequest
        ): APICallParameters<ApproveApplicationRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/grant" + "/approve",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function rejectApplication(
            request: RejectApplicationRequest
        ): APICallParameters<RejectApplicationRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/grant" + "/reject",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function closeApplication(
            request: CloseApplicationRequest
        ): APICallParameters<CloseApplicationRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/grant" + "/close",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function commentOnApplication(
            request: CommentOnApplicationRequest
        ): APICallParameters<CommentOnApplicationRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/grant" + "/comment",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function deleteComment(
            request: DeleteCommentRequest
        ): APICallParameters<DeleteCommentRequest, any /* unknown */> {
            return {
                context: "",
                method: "DELETE",
                path: "/api/grant" + "/comment",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function submitApplication(
            request: CreateApplication
        ): APICallParameters<CreateApplication, FindByLongId> {
            return {
                context: "",
                method: "POST",
                path: "/api/grant" + "/submit-application",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function editApplication(
            request: EditApplicationRequest
        ): APICallParameters<EditApplicationRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/grant" + "/edit",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function uploadTemplates(
            request: UploadTemplatesRequest
        ): APICallParameters<UploadTemplatesRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/grant" + "/upload-templates",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function readRequestSettings(
            request: ReadRequestSettingsRequest
        ): APICallParameters<ReadRequestSettingsRequest, ProjectApplicationSettings> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/grant" + "/request-settings", {projectId: request.projectId}),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function uploadRequestSettings(
            request: ProjectApplicationSettings
        ): APICallParameters<ProjectApplicationSettings, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/grant" + "/request-settings",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function readTemplates(
            request: ReadTemplatesRequest
        ): APICallParameters<ReadTemplatesRequest, UploadTemplatesRequest> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/grant" + "/read-templates", {projectId: request.projectId}),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function ingoingApplications(
            request: IngoingApplicationsRequest
        ): APICallParameters<IngoingApplicationsRequest, Page<Application>> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/grant" + "/ingoing", {
                    itemsPerPage: request.itemsPerPage,
                    page: request.page,
                    filter: request.filter
                }),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function outgoingApplications(
            request: OutgoingApplicationsRequest
        ): APICallParameters<OutgoingApplicationsRequest, Page<Application>> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/grant" + "/outgoing", {
                    itemsPerPage: request.itemsPerPage,
                    page: request.page,
                    filter: request.filter
                }),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function viewApplication(
            request: ViewApplicationRequest
        ): APICallParameters<ViewApplicationRequest, ApplicationWithComments> {
            return {
                context: "",
                method: "GET",
                path: "/api/grant" + "/" + request.id,
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function setEnabledStatus(
            request: SetEnabledStatusRequest
        ): APICallParameters<SetEnabledStatusRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/grant" + "/set-enabled",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function isEnabled(
            request: IsEnabledRequest
        ): APICallParameters<IsEnabledRequest, IsEnabledResponse> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/grant" + "/is-enabled", {projectId: request.projectId}),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function browseProjects(
            request: BrowseProjectsRequest
        ): APICallParameters<BrowseProjectsRequest, Page<ProjectWithTitle>> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/grant" + "/browse-projects", {
                    itemsPerPage: request.itemsPerPage,
                    page: request.page
                }),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function uploadLogo(
            request: UploadLogoRequest
        ): APICallParameters<UploadLogoRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/grant" + "/uploadLogo",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function fetchLogo(
            request: FetchLogoRequest
        ): APICallParameters<FetchLogoRequest, BinaryStream> {
            return {
                context: "",
                method: "GET",
                path: "/api/grant" + "/logo" + "/" + request.projectId,
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function uploadDescription(
            request: UploadDescriptionRequest
        ): APICallParameters<UploadDescriptionRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/grant" + "/uploadDescription",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function fetchDescription(
            request: FetchDescriptionRequest
        ): APICallParameters<FetchDescriptionRequest, FetchDescriptionResponse> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/grant" + "/description", {projectId: request.projectId}),
                parameters: request,
                reloadId: Math.random(),
            };
        }

        export function retrieveProducts(
            request: GrantsRetrieveProductsRequest
        ): APICallParameters<GrantsRetrieveProductsRequest, GrantsRetrieveProductsResponse> {
            return {
                context: "",
                method: "GET",
                path: buildQueryString("/api/grant" + "/retrieveProducts", {
                    projectId: request.projectId,
                    recipientId: request.recipientId,
                    recipientType: request.recipientType
                }),
                parameters: request,
                reloadId: Math.random(),
            };
        }
    }
    export namespace GrantRecipientNS {
        export interface PersonalProject {
            username: string,
            type: "personal",
        }

        export interface ExistingProject {
            projectId: string,
            type: "existing_project",
        }

        export interface NewProject {
            projectTitle: string,
            type: "new_project",
        }
    }
    export namespace gifts {
        export function availableGifts(): APICallParameters<{}, AvailableGiftsResponse> {
            return {
                context: "",
                method: "GET",
                path: "/api/gifts" + "/available",
                reloadId: Math.random(),
            };
        }

        export function claimGift(
            request: ClaimGiftRequest
        ): APICallParameters<ClaimGiftRequest, any /* unknown */> {
            return {
                context: "",
                method: "POST",
                path: "/api/gifts" + "/claim",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function listGifts(): APICallParameters<{}, ListGiftsResponse> {
            return {
                context: "",
                method: "GET",
                path: "/api/gifts",
                reloadId: Math.random(),
            };
        }

        export function createGift(
            request: GiftWithCriteria
        ): APICallParameters<GiftWithCriteria, FindByLongId> {
            return {
                context: "",
                method: "POST",
                path: "/api/gifts",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }

        export function deleteGift(
            request: DeleteGiftRequest
        ): APICallParameters<DeleteGiftRequest, any /* unknown */> {
            return {
                context: "",
                method: "DELETE",
                path: "/api/gifts",
                parameters: request,
                reloadId: Math.random(),
                payload: request,
            };
        }
    }
}
export namespace task {
    export function list(
        request: ListRequest
    ): APICallParameters<ListRequest, Page<Task>> {
        return {
            context: "",
            method: "GET",
            path: buildQueryString("/api/tasks", {itemsPerPage: request.itemsPerPage, page: request.page}),
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export function postStatus(
        request: PostStatusRequest
    ): APICallParameters<PostStatusRequest, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/api/tasks",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function create(
        request: CreateRequest
    ): APICallParameters<CreateRequest, Task> {
        return {
            context: "",
            method: "PUT",
            path: "/api/tasks",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function view(
        request: FindByStringId
    ): APICallParameters<FindByStringId, Task> {
        return {
            context: "",
            method: "GET",
            path: "/api/tasks" + "/" + request.id,
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export function markAsComplete(
        request: FindByStringId
    ): APICallParameters<FindByStringId, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/api/tasks" + "/" + request.id,
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export interface PostStatusRequest {
        update: TaskUpdate,
    }

    export interface TaskUpdate {
        jobId: string,
        newTitle?: string,
        speeds: Speed[],
        progress?: Progress,
        complete: boolean,
        messageToAppend?: string,
        newStatus?: string,
    }

    export interface Speed {
        asText: string,
        speed: number /* float64 */
        ,
        title: string,
        unit: string,
    }

    export interface Progress {
        title: string,
        current: number /* int32 */
        ,
        maximum: number /* int32 */
        ,
    }

    export interface Task {
        jobId: string,
        owner: string,
        processor: string,
        title?: string,
        status?: string,
        complete: boolean,
        startedAt: number /* int64 */
        ,
        modifiedAt: number /* int64 */
        ,
    }

    export interface ListRequest {
        itemsPerPage?: number /* int32 */
        ,
        page?: number /* int32 */
        ,
    }

    export interface CreateRequest {
        title: string,
        owner: string,
        initialStatus?: string,
    }
}
export namespace micro {

    export namespace healthcheck {
        export function status(): APICallParameters<{}, any /* unknown */> {
            return {
                context: "",
                method: "GET",
                path: "/status",
                reloadId: Math.random(),
            };
        }
    }
}
export namespace support {
    export function createTicket(
        request: CreateTicketRequest
    ): APICallParameters<CreateTicketRequest, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/api/support" + "/ticket",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export interface CreateTicketRequest {
        subject: string,
        message: string,
    }
}
export namespace news {
    export function newPost(
        request: NewPostRequest
    ): APICallParameters<NewPostRequest, any /* unknown */> {
        return {
            context: "",
            method: "PUT",
            path: "/api/news" + "/post",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function updatePost(
        request: UpdatePostRequest
    ): APICallParameters<UpdatePostRequest, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/api/news" + "/update",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function deletePost(
        request: DeleteNewsPostRequest
    ): APICallParameters<DeleteNewsPostRequest, any /* unknown */> {
        return {
            context: "",
            method: "DELETE",
            path: "/api/news" + "/delete",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function togglePostHidden(
        request: TogglePostHiddenRequest
    ): APICallParameters<TogglePostHiddenRequest, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/api/news" + "/toggleHidden",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function listPosts(
        request: ListPostsRequest
    ): APICallParameters<ListPostsRequest, Page<NewsPost>> {
        return {
            context: "",
            method: "GET",
            path: buildQueryString("/api/news" + "/list", {
                filter: request.filter,
                withHidden: request.withHidden,
                itemsPerPage: request.itemsPerPage,
                page: request.page
            }),
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export function listCategories(): APICallParameters<{}, string[]> {
        return {
            context: "",
            method: "GET",
            path: "/api/news" + "/listCategories",
            reloadId: Math.random(),
        };
    }

    export function listDowntimes(): APICallParameters<{}, Page<NewsPost>> {
        return {
            context: "",
            method: "GET",
            path: "/api/news" + "/listDowntimes",
            reloadId: Math.random(),
        };
    }

    export function getPostBy(
        request: GetPostByIdRequest
    ): APICallParameters<GetPostByIdRequest, NewsPost> {
        return {
            context: "",
            method: "GET",
            path: buildQueryString("/api/news" + "/byId", {id: request.id}),
            parameters: request,
            reloadId: Math.random(),
        };
    }

    export interface NewPostRequest {
        title: string,
        subtitle: string,
        body: string,
        showFrom: number /* int64 */
        ,
        category: string,
        hideFrom?: number /* int64 */
        ,
    }

    export interface UpdatePostRequest {
        id: number /* int64 */
        ,
        title: string,
        subtitle: string,
        body: string,
        showFrom: number /* int64 */
        ,
        hideFrom?: number /* int64 */
        ,
        category: string,
    }

    export interface DeleteNewsPostRequest {
        id: number /* int64 */
        ,
    }

    export interface TogglePostHiddenRequest {
        id: number /* int64 */
        ,
    }

    export interface NewsPost {
        id: number /* int64 */
        ,
        title: string,
        subtitle: string,
        body: string,
        postedBy: string,
        showFrom: number /* int64 */
        ,
        hideFrom?: number /* int64 */
        ,
        hidden: boolean,
        category: string,
    }

    export interface ListPostsRequest {
        filter?: string,
        withHidden: boolean,
        page: number /* int32 */
        ,
        itemsPerPage: number /* int32 */
        ,
    }

    export interface GetPostByIdRequest {
        id: number /* int64 */
        ,
    }
}
export namespace indexing {
    export function query(
        request: QueryRequest
    ): APICallParameters<QueryRequest, Page<file.StorageFile>> {
        return {
            context: "",
            method: "POST",
            path: "/api/indexing/query",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function statistics(
        request: StatisticsRequest
    ): APICallParameters<StatisticsRequest, StatisticsResponse> {
        return {
            context: "",
            method: "POST",
            path: "/api/indexing/query" + "/statistics",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function size(
        request: SizeRequest
    ): APICallParameters<SizeRequest, SizeResponse> {
        return {
            context: "",
            method: "POST",
            path: "/api/indexing/query" + "/size",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export interface QueryRequest {
        query: FileQuery,
        sortBy?: SortRequest,
        itemsPerPage?: number /* int32 */
        ,
        page?: number /* int32 */
        ,
    }

    export interface FileQuery {
        roots: string[],
        fileNameQuery?: string[],
        fileNameExact?: AllOf<string>,
        extensions?: AllOf<string>,
        fileTypes?: AllOf<"FILE" | "DIRECTORY">,
        fileDepth?: AllOf<Comparison<number /* int32 */>>,
        size?: AllOf<Comparison<number /* int64 */>>,
    }

    export interface AllOf<Pred> {
        allOf: AnyOf<Pred>[]
    }

    export interface AnyOf<Pred> {
        anyOf: Pred[],
        negate?: boolean,
    }

    export interface Comparison<Value> {
        value: Value,
        operator: "GREATER_THAN" | "GREATER_THAN_EQUALS" | "LESS_THAN" | "LESS_THAN_EQUALS" | "EQUALS",
    }

    export interface SortRequest {
        field: "FILE_NAME" | "FILE_TYPE" | "SIZE",
        direction: "ASCENDING" | "DESCENDING",
    }

    export interface StatisticsResponse {
        count: number /* int64 */
        ,
        size?: NumericStatistics,
        fileDepth?: NumericStatistics,
    }

    export interface NumericStatistics {
        mean?: number /* float64 */
        ,
        minimum?: number /* float64 */
        ,
        maximum?: number /* float64 */
        ,
        sum?: number /* float64 */
        ,
        percentiles: number /* float64 */[],
    }

    export interface StatisticsRequest {
        query: FileQuery,
        size?: NumericStatisticsRequest,
        fileDepth?: NumericStatisticsRequest,
    }

    export interface NumericStatisticsRequest {
        calculateMean: boolean,
        calculateMinimum: boolean,
        calculateMaximum: boolean,
        calculateSum: boolean,
        percentiles: number /* float64 */[],
    }

    export interface SizeResponse {
        size: number /* int64 */
        ,
    }

    export interface SizeRequest {
        paths: string[],
    }
}
export namespace kotlin {
    export interface Pair<A, B> {
        first: A,
        second: B,
    }
}
export namespace avatar {
    export function update(
        request: SerializedAvatar
    ): APICallParameters<SerializedAvatar, any /* unknown */> {
        return {
            context: "",
            method: "POST",
            path: "/api/avatar" + "/update",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function findAvatar(): APICallParameters<{}, SerializedAvatar> {
        return {
            context: "",
            method: "GET",
            path: "/api/avatar" + "/find",
            reloadId: Math.random(),
        };
    }

    export function findBulk(
        request: FindBulkRequest
    ): APICallParameters<FindBulkRequest, FindBulkResponse> {
        return {
            context: "",
            method: "POST",
            path: "/api/avatar" + "/bulk",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export interface SerializedAvatar {
        top: string,
        topAccessory: string,
        hairColor: string,
        facialHair: string,
        facialHairColor: string,
        clothes: string,
        colorFabric: string,
        eyes: string,
        eyebrows: string,
        mouthTypes: string,
        skinColors: string,
        clothesGraphic: string,
        hatColor: string,
    }

    export interface FindBulkResponse {
        avatars: Record<string, SerializedAvatar>,
    }

    export interface FindBulkRequest {
        usernames: string[],
    }
}
export namespace BulkRequestNS {

}
export namespace contactbook {
    export function queryUserContacts(
        request: QueryContactsRequest
    ): APICallParameters<QueryContactsRequest, QueryContactsResponse> {
        return {
            context: "",
            method: "POST",
            path: "/api/contactbook",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function remove(
        request: DeleteRequest
    ): APICallParameters<DeleteRequest, any /* unknown */> {
        return {
            context: "",
            method: "DELETE",
            path: "/api/contactbook",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function insert(
        request: InsertRequest
    ): APICallParameters<InsertRequest, any /* unknown */> {
        return {
            context: "",
            method: "PUT",
            path: "/api/contactbook",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export function listAllContactsForUser(
        request: AllContactsForUserRequest
    ): APICallParameters<AllContactsForUserRequest, QueryContactsResponse> {
        return {
            context: "",
            method: "POST",
            path: "/api/contactbook" + "/all",
            parameters: request,
            reloadId: Math.random(),
            payload: request,
        };
    }

    export interface InsertRequest {
        fromUser: string,
        toUser: string[],
        serviceOrigin: "SHARE_SERVICE" | "PROJECT_SERVICE",
    }

    export interface DeleteRequest {
        fromUser: string,
        toUser: string,
        serviceOrigin: "SHARE_SERVICE" | "PROJECT_SERVICE",
    }

    export interface QueryContactsResponse {
        contacts: string[],
    }

    export interface QueryContactsRequest {
        query: string,
        serviceOrigin: "SHARE_SERVICE" | "PROJECT_SERVICE",
    }

    export interface AllContactsForUserRequest {
        serviceOrigin: "SHARE_SERVICE" | "PROJECT_SERVICE",
    }
}
