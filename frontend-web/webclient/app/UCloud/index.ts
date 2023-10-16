/* eslint-disable */
/* AUTO GENERATED CODE - DO NOT MODIFY */
/* Generated at: Thu Jun 03 13:40:27 GMT 2021 */

import {ApplicationGroup} from "@/Applications/api";
import {buildQueryString} from "@/Utilities/URIUtilities";

/**
 * A generic error message
 *
 * UCloud uses HTTP status code for all error messages In addition and if possible, UCloud will include a message using a
 * common format Note that this is not guaranteed to be included in case of a failure somewhere else in the network stack
 * For example, UCloud's load balancer might not be able to contact the backend at all In such a case UCloud will
 * _not_ include a more detailed error message
 *
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
export interface Page<T = unknown> {
    itemsInTotal: number /* int32 */,
    itemsPerPage: number /* int32 */,
    pageNumber: number /* int32 */,
    items: T[],
}
/**
 * Represents a single 'page' of results

 * Every page contains the items from the current result set, along with information which allows the client to fetch
 * additional information
 */
export interface PageV2<T = unknown> {
    /**
     * The expected items per page, this is extracted directly from the request
     */
    itemsPerPage: number /* int32 */,
    /**
     * The items returned in this page
     *
     * NOTE: The amount of items might differ from `itemsPerPage`, even if there are more results The only reliable way to
     * check if the end of results has been reached is by checking i `next == null`
     */
    items: T[],
    /**
     * The token used to fetch additional items from this result set
     */
    next?: string,
}
/**
 * A base type for requesting a bulk operation

 * ---
 *
 * __⚠ WARNING:__ All request items listed in the bulk request must be treated as a _single_ transaction This means
 * that either the entire request succeeds, or the entire request fails
 *
 * There are two exceptions to this rule:
 *
 * 1 Certain calls may choose to only guarantee this at the provider level That is if a single call contain request
 * for multiple providers, then in rare occasions (ie crash) changes might not be rolled back immediately on all
 * providers A service _MUST_ attempt to rollback already committed changes at other providers
 *
 * 2 The underlying system does not provide such guarantees In this case the service/provider _MUST_ support the
 * verification API to cleanup these resources later
 *
 * ---

 *
 *
 */

export type BulkRequest<T> = { type: "bulk", items: T[] }
/**
 * The base type for requesting paginated content
 *
 * Paginated content can be requested with one of the following `consistency` guarantees, this greatly changes the
 * semantics of the call:
 *
 * | Consistency | Description |
 * |-------------|-------------|
 * | `PREFER` | Consistency is preferred but not required An inconsistent snapshot might be returned |
 * | `REQUIRE` | Consistency is required A request will fail if consistency is no longer guaranteed |
 *
 * The `consistency` refers to if collecting all the results via the pagination API are _consistent_ We consider the
 * results to be consistent if it contains a complete view at some point in time In practice this means that the results
 * must contain all the items, in the correct order and without duplicates
 *
 * If you use the `PREFER` consistency then you may receive in-complete results that might appear out-of-order and can
 * contain duplicate items UCloud will still attempt to serve a snapshot which appears mostly consistent This is helpful
 * for user-interfaces which do not strictly depend on consistency but would still prefer something which is mostly
 * consistent
 *
 * The results might become inconsistent if the client either takes too long, or a service instance goes down while
 * fetching the results UCloud attempts to keep each `next` token alive for at least one minute before invalidating it
 * This does not mean that a client must collect all results within a minute but rather that they must fetch the next page
 * within a minute of the last page If this is not feasible and consistency is not required then `PREFER` should be used
 *
 * ---
 *
 * __📝 NOTE:__ Services are allowed to ignore extra criteria of the request if the `next` token is supplied This is
 * needed in order to provide a consistent view of the results Clients _should_ provide the same criterion as they
 * paginate through the results
 *
 * ---
 *
 */
export interface PaginationRequestV2 {
    /**
     * Requested number of items per page Supported values: 10, 25, 50, 100, 250
     */
    itemsPerPage: number /* int32 */,
    /**
     * A token requesting the next page of items
     */
    next?: string,
    /**
     * Controls the consistency guarantees provided by the backend
     */
    consistency?: ("PREFER" | "REQUIRE"),
    /**
     * Items to skip ahead
     */
    itemsToSkip?: number /* int64 */,
}
export interface FindByLongId {
    id: number /* int64 */,
}
export interface FindByStringId {
    id: string,
}
export interface BulkResponse<T = unknown> {
    responses: T[],
}
export interface PaginationRequest {
    itemsPerPage?: number /* int32 */,
    page?: number /* int32 */,
}
export namespace file {

export namespace orchestrator {
export interface FileMetadataAddRequestItem {
    path: string,
    metadata: FileMetadataDocumentNS.Spec,
}
export interface FileMetadataDeleteRequestItem {
    path: string,
    templateId: string,
}
export interface FileMetadataRetrieveAllResponse {
    metadata: FileMetadataAttached[],
}
export interface FileMetadataAttached {
    path: string,
    metadata: FileMetadataDocument,
}
/**
 * A metadata document which conforms to a `FileMetadataTemplate`
 */
export interface FileMetadataDocument {
    /**
     * A unique identifier referencing the `Resource`
     *
     * The ID is unique across a provider for a single resource type.
     */
    id: string,
    specification: FileMetadataDocumentNS.Spec,
    createdAt: number /* int64 */,
    status: FileMetadataDocumentNS.Status,
    /**
     * Contains a list of updates from the provider as well as UCloud
     *
     * Updates provide a way for both UCloud, and the provider to communicate to the user what is happening with their
     * resource.
     */
    updates: provider.ResourceUpdate[],
    /**
     * Contains information about the original creator of the `Resource` along with project association
     */
    owner: provider.ResourceOwner,
    acl?: any /* unknown */,
    permissions?: provider.ResourcePermissions,
    providerGeneratedId?: string,
    type: ("metadata"),
}
export interface FileMetadataRetrieveAllRequest {
    parentPath: string,
}
export interface FileMetadataMoveRequestItem {
    oldPath: string,
    newPath: string,
}
/**
 * A `UFile` is a resource for storing, retrieving and organizing data in UCloud

 * A file in UCloud (`UFile`) closely follows the concept of a computer file you might already be familiar with. The
 * functionality of a file is mostly determined by its `type`. The two most important types are the `DIRECTORY` and `FILE`
 * types. A `DIRECTORY` is a container of `UFile`s. A directory can itself contain more directories, which leads to a
 * natural tree-like structure. `FILE`s, also referred to as a regular files, are data records which each contain a series
 * of bytes.
 *
 * All files in UCloud have a name associated with them. This name uniquely identifies them within their directory. All
 * files in UCloud belong to exactly one directory.
 *
 * File operations must be able to reference the files on which they operate. In UCloud, these references are made through
 * the `path` property. Paths use the tree-like structure of files to reference a file, it does so by declaring which
 * directories to go through, starting at the top, to reach the file we are referencing. This information is serialized
 * as a textual string, where each step of the path is separated by forward-slash `/` (`U+002F`). The path must start with
 * a single forward-slash, which signifies the root of the file tree. UCloud never users 'relative' file paths, which some
 * systems use.
 *
 * All files in UCloud additionally have metadata associated with them. For this we differentiate between system-level
 * metadata and user-defined metadata.
 *
 * We have just covered two examples of system-level metadata, the `path` and `type`. UCloud additionally supports
 * metadata such as general `stats` about the files, such as file sizes. All files have a set of `permissions` associated
 * with them, providers may optionally expose this information to UCloud and the users.
 *
 * User-defined metadata describe the contents of a  All metadata is described by a template (`FileMetadataTemplate`),
 * this template defines a document structure for the metadata. User-defined metadata can be used for a variety of
 * purposes, such as: [Datacite metadata](https://schema.datacite.org/), sensitivity levels, and other field specific
 * metadata formats.
 *
 */
export interface UFile {
    /**
     * A unique reference to a file
     *
     * All files in UCloud have a `name` associated with them. This name uniquely identifies them within their directory. All
     * files in UCloud belong to exactly one directory. A `name` can be any textual string, for example: `thesis-42.docx`.
     * However, certain restrictions apply to file `name`s, see below for a concrete list of rules and recommendations.
     *
     * The `extension` of a file is typically used as a hint to clients how to treat a specific  For example, an extension
     * might indicate that the file contains a video of a specific format. In UCloud, the file's `extension` is derived from
     * its `name`. In UCloud, it is simply defined as the text immediately following, and not including, the last
     * period `.` (`U+002E`). The table below shows some examples of how UCloud determines the extension of a file:
     *
     * | File `name` | Derived `extension` | Comment |
     * |-------------|---------------------|---------|
     * | `thesis-42.docx` | `docx` | - |
     * | `thesis-43-final.tar` | `tar` | - |
     * | `thesis-43-FINAL2.tar.gz` | `gz` | Note that UCloud does not recognize `tar` as being part of the extension |
     * | `thesis` |  | Empty string |
     * | `.ssh` | `ssh` | 'Hidden' files also have a surprising extension in UCloud |
     *
     * File operations must be able to reference the files on which they operate. In UCloud, these references are made through
     * the `path` property. Paths use the tree-like structure of files to reference a file, it does so by declaring which
     * directories to go through, starting at the top, to reach the file we are referencing. This information is serialized as
     * a textual string, where each step of the path is separated by forward-slash `/` (`U+002F`). The path must start with a
     * single forward-slash, which signifies the root of the file tree. UCloud never users 'relative' file paths, which some
     * systems use.
     *
     * A path in UCloud is structured in such a way that they are unique across all providers and file systems. The figure
     * below shows how a UCloud path is structured, and how it can be mapped to an internal file-system path.
     *
     * ![](/backend/file-orchestrator-service/wiki/path.png)
     *
     * __Figure:__ At the top, a UCloud path along with the components of it. At the bottom, an example of an internal,
     * provider specific, file-system path.
     *
     * The figure shows how a UCloud path consists of four components:
     *
     * 1. The ['Provider ID'](/backend/provider-service/README.md) references the provider who owns and hosts the file
     * 2. The product reference, this references the product that is hosting the `FileCollection`
     * 3. The `FileCollection` ID references the ID of the internal file collection. These are controlled by the provider and
     *    match the different types of file-systems they have available. A single file collection typically maps to a specific
     *    folder on the provider's file-system.
     * 4. The internal path, which tells the provider how to find the file within the collection. Providers can typically pass
     *    this as a one-to-one mapping.
     *
     * __Rules of a file `name`:__
     *
     * 1. The `name` cannot be equal to `.` (commonly interpreted to mean the current directory)
     * 2. The `name` cannot be equal to `..` (commonly interpreted to mean the parent directory)
     * 3. The `name` cannot contain a forward-slash `/` (`U+002F`)
     * 4. Names are strictly unicode
     *
     * UCloud will normalize a path which contain `.` or `..` in a path's step. It is normalized according to the comments
     * mentioned in rule 1 and 2.
     *
     * Note that all paths in unicode are strictly unicode (rule 4). __This is different from the unix standard.__ Unix file
     * names can contain _arbitrary_ binary data. (TODO determine how providers should handle this edge-case)
     *
     * __Additionally regarding file `name`s, UCloud recommends to users the following:__
     *
     * - Avoid the following file names:
     *     - Containing Windows reserved characters: `<`, `>`, `:`, `"`, `/`, `|`, `?`, `*`, `\`
     *     - Any of the reserved file names in Windows:
     *         - `AUX`
     *         - `COM1`, `COM2`, `COM3`, `COM4`, `COM5`, `COM6`, `COM7`, `COM8`, `COM9`
     *         - `CON`
     *         - `LPT1`, `LPT2`, `LPT3`, `LPT4`, `LPT5`, `LPT6`, `LPT7`, `LPT8`, `LPT9`
     *         - `NUL`
     *         - `PRN`
     *         - Any of the above followed by an extension
     *     - Avoid ASCII control characters (decimal value 0-31 both inclusive)
     *     - Avoid Unicode control characters (e.g. right-to-left override)
     *     - Avoid line breaks, paragraph separators and other unicode separators which is typically interpreted as a
     *       line-break
     *     - Avoid binary names
     *
     * UCloud will attempt to reject these for file operations initiated through the client, but it cannot ensure that these
     * files do not appear regardless. This is due to the fact that the file systems are typically mounted directly by
     * user-controlled jobs.
     *
     * __Rules of a file `path`:__
     *
     * 1. All paths must be absolute, that is they must start with `/`
     * 2. UCloud will normalize all path 'steps' containing either `.` or `..`
     *
     * __Additionally UCloud recommends to users the following regarding `path`s:__
     *
     * - Avoid long paths:
     *     - Older versions of Unixes report `PATH_MAX` as 1024
     *     - Newer versions of Unixes report `PATH_MAX` as 4096
     *     - Older versions of Windows start failing above 256 characters
     *
     */
    path: string,
    /**
     * Which type of file this is, see `FileType` for more information.
     */
    type: ("FILE" | "DIRECTORY" | "SOFT_LINK" | "DANGLING_METADATA"),
    /**
     * A hint to clients about which icon to display next to this  See `FileIconHint` for details.
     */
    icon?: ("DIRECTORY_STAR" | "DIRECTORY_SHARES" | "DIRECTORY_TRASH" | "DIRECTORY_JOBS"),
    /**
     * General system-level stats about the  See `UFile.Stats` for details.
     */
    stats?: UFileNS.Stats,
    /**
     * System-level permissions for this  See `UFile.Permissions` for details.
     */
    permissions?: UFileNS.Permissions,
    /**
     * User-defined metadata for this  See `FileMetadataTemplate` for details.
     */
    metadata?: FileMetadataHistory,
}
export interface FileMetadataHistory {
    templates: Record<string, FileMetadataTemplate>,
    metadata: Record<string, FileMetadataOrDeleted[]>,
}
/**
 * A `FileMetadataTemplate` allows users to attach user-defined metadata to any `UFile`
 */
export interface FileMetadataTemplate {
    /**
     * A unique identifier referencing the `Resource`
     *
     * The ID is unique across a provider for a single resource type.
     */
    id: string,
    specification: FileMetadataTemplateNS.Spec,
    /**
     * Holds the current status of the `Resource`
     */
    status: FileMetadataTemplateNS.Status,
    /**
     * Contains a list of updates from the provider as well as UCloud
     *
     * Updates provide a way for both UCloud, and the provider to communicate to the user what is happening with their
     * resource.
     */
    updates: FileMetadataTemplateNS.Update[],
    /**
     * Contains information about the original creator of the `Resource` along with project association
     */
    owner: provider.ResourceOwner,
    /**
     * An ACL for this `Resource`
     * @deprecated
     */
    acl: provider.ResourceAclEntry<("READ" | "WRITE")>[],
    /**
     * Timestamp referencing when the request for creation was received by UCloud
     */
    createdAt: number /* int64 */,
    public: boolean,
    /**
     * Permissions assigned to this resource
     *
     * A null value indicates that permissions are not supported by this resource type.
     */
    permissions?: provider.ResourcePermissions,
    billing: provider.ResourceBillingNS.Free,
    providerGeneratedId?: string,
}
export type FileMetadataOrDeleted = FileMetadataDocument | FileMetadataOrDeletedNS.Deleted
/**
 * The base type for requesting paginated content.
 *
 * Paginated content can be requested with one of the following `consistency` guarantees, this greatly changes the
 * semantics of the call:
 *
 * | Consistency | Description |
 * |-------------|-------------|
 * | `PREFER` | Consistency is preferred but not required. An inconsistent snapshot might be returned. |
 * | `REQUIRE` | Consistency is required. A request will fail if consistency is no longer guaranteed. |
 *
 * The `consistency` refers to if collecting all the results via the pagination API are _consistent_. We consider the
 * results to be consistent if it contains a complete view at some point in time. In practice this means that the results
 * must contain all the items, in the correct order and without duplicates.
 *
 * If you use the `PREFER` consistency then you may receive in-complete results that might appear out-of-order and can
 * contain duplicate items. UCloud will still attempt to serve a snapshot which appears mostly consistent. This is helpful
 * for user-interfaces which do not strictly depend on consistency but would still prefer something which is mostly
 * consistent.
 *
 * The results might become inconsistent if the client either takes too long, or a service instance goes down while
 * fetching the results. UCloud attempts to keep each `next` token alive for at least one minute before invalidating it.
 * This does not mean that a client must collect all results within a minute but rather that they must fetch the next page
 * within a minute of the last page. If this is not feasible and consistency is not required then `PREFER` should be used.
 *
 * ---
 *
 * __📝 NOTE:__ Services are allowed to ignore extra criteria of the request if the `next` token is supplied. This is
 * needed in order to provide a consistent view of the results. Clients _should_ provide the same criterion as they
 * paginate through the results.
 *
 * ---
 *
 */
export interface FilesBrowseRequest {
    path: string,
    includePermissions?: boolean,
    includeTimestamps?: boolean,
    includeSizes?: boolean,
    includeUnixInfo?: boolean,
    includeMetadata?: boolean,
    /**
     * Determines if the request should succeed if the underlying system does not support this data.
     *
     * This value is `true` by default
     */
    allowUnsupportedInclude?: boolean,
    /**
     * Requested number of items per page. Supported values: 10, 25, 50, 100, 250.
     */
    itemsPerPage?: number /* int32 */,
    /**
     * A token requesting the next page of items
     */
    next?: string,
    /**
     * Controls the consistency guarantees provided by the backend
     */
    consistency?: ("PREFER" | "REQUIRE"),
    /**
     * Items to skip ahead
     */
    itemsToSkip?: number /* int64 */,
    sortBy: FilesSortBy;
    sortOrder: SortOrder;
}

export enum SortOrder {
    ASCENDING = "ASCENDING",
    DESCENDING = "DESCENDING"
}

export enum FilesSortBy {
    PATH = "PATH",
    SIZE = "SIZE",
    CREATED_AT = "CREATED_AT",
    MODIFIED_AT = "MODIFIED_AT"
}

export type LongRunningTask = LongRunningTaskNS.Complete | LongRunningTaskNS.ContinuesInBackground
export interface FilesCopyRequestItem {
    oldPath: string,
    newPath: string,
    conflictPolicy: ("RENAME" | "REJECT" | "REPLACE" | "MERGE_RENAME"),
}
export interface FilesCreateDownloadResponseItem {
    endpoint: string,
}
export interface FilesCreateDownloadRequestItem {
    path: string,
}
export interface FilesCreateUploadResponseItem {
    endpoint: string,
    protocol: ("CHUNKED"),
    token: string,
}
export interface FilesCreateUploadRequestItem {
    path: string,
    supportedProtocols: ("CHUNKED")[],
    conflictPolicy: ("RENAME" | "REJECT" | "REPLACE" | "MERGE_RENAME"),
}
export interface FilesCreateFolderRequestItem {
    path: string,
    conflictPolicy: ("RENAME" | "REJECT" | "REPLACE" | "MERGE_RENAME"),
}
export interface FindByPath {
    path: string,
}
export interface FilesMoveRequestItem {
    oldPath: string,
    newPath: string,
    conflictPolicy: ("RENAME" | "REJECT" | "REPLACE" | "MERGE_RENAME"),
}
export interface FilesRetrieveRequest {
    path: string,
    includePermissions?: boolean,
    includeTimestamps?: boolean,
    includeSizes?: boolean,
    includeUnixInfo?: boolean,
    includeMetadata?: boolean,
    /**
     * Determines if the request should succeed if the underlying system does not support this data.
     *
     * This value is `true` by default
     */
    allowUnsupportedInclude?: boolean,
}
export interface FilesUpdateAclRequestItem {
    path: string,
    newAcl: provider.ResourceAclEntry<("READ" | "WRITE" | "ADMINISTRATOR")>[],
}
/**
 * The base type for requesting paginated content.
 *
 * Paginated content can be requested with one of the following `consistency` guarantees, this greatly changes the
 * semantics of the call:
 *
 * | Consistency | Description |
 * |-------------|-------------|
 * | `PREFER` | Consistency is preferred but not required. An inconsistent snapshot might be returned. |
 * | `REQUIRE` | Consistency is required. A request will fail if consistency is no longer guaranteed. |
 *
 * The `consistency` refers to if collecting all the results via the pagination API are _consistent_. We consider the
 * results to be consistent if it contains a complete view at some point in time. In practice this means that the results
 * must contain all the items, in the correct order and without duplicates.
 *
 * If you use the `PREFER` consistency then you may receive in-complete results that might appear out-of-order and can
 * contain duplicate items. UCloud will still attempt to serve a snapshot which appears mostly consistent. This is helpful
 * for user-interfaces which do not strictly depend on consistency but would still prefer something which is mostly
 * consistent.
 *
 * The results might become inconsistent if the client either takes too long, or a service instance goes down while
 * fetching the results. UCloud attempts to keep each `next` token alive for at least one minute before invalidating it.
 * This does not mean that a client must collect all results within a minute but rather that they must fetch the next page
 * within a minute of the last page. If this is not feasible and consistency is not required then `PREFER` should be used.
 *
 * ---
 *
 * __📝 NOTE:__ Services are allowed to ignore extra criteria of the request if the `next` token is supplied. This is
 * needed in order to provide a consistent view of the results. Clients _should_ provide the same criterion as they
 * paginate through the results.
 *
 * ---
 *
 */
export interface FileMetadataTemplatesBrowseRequest {
    /**
     * Requested number of items per page. Supported values: 10, 25, 50, 100, 250.
     */
    itemsPerPage?: number /* int32 */,
    /**
     * A token requesting the next page of items
     */
    next?: string,
    /**
     * Controls the consistency guarantees provided by the backend
     */
    consistency?: ("PREFER" | "REQUIRE"),
    /**
     * Items to skip ahead
     */
    itemsToSkip?: number /* int64 */,
}
export interface FileMetadataTemplatesRetrieveRequest {
    id: string,
    version?: string,
}
export interface Share {
    path: string,
    sharedBy: string,
    sharedWith: string,
    approved: boolean,
}
export interface SharesRetrieveRequest {
    path: string,
}
/**
 * The base type for requesting paginated content.
 *
 * Paginated content can be requested with one of the following `consistency` guarantees, this greatly changes the
 * semantics of the call:
 *
 * | Consistency | Description |
 * |-------------|-------------|
 * | `PREFER` | Consistency is preferred but not required. An inconsistent snapshot might be returned. |
 * | `REQUIRE` | Consistency is required. A request will fail if consistency is no longer guaranteed. |
 *
 * The `consistency` refers to if collecting all the results via the pagination API are _consistent_. We consider the
 * results to be consistent if it contains a complete view at some point in time. In practice this means that the results
 * must contain all the items, in the correct order and without duplicates.
 *
 * If you use the `PREFER` consistency then you may receive in-complete results that might appear out-of-order and can
 * contain duplicate items. UCloud will still attempt to serve a snapshot which appears mostly consistent. This is helpful
 * for user-interfaces which do not strictly depend on consistency but would still prefer something which is mostly
 * consistent.
 *
 * The results might become inconsistent if the client either takes too long, or a service instance goes down while
 * fetching the results. UCloud attempts to keep each `next` token alive for at least one minute before invalidating it.
 * This does not mean that a client must collect all results within a minute but rather that they must fetch the next page
 * within a minute of the last page. If this is not feasible and consistency is not required then `PREFER` should be used.
 *
 * ---
 *
 * __📝 NOTE:__ Services are allowed to ignore extra criteria of the request if the `next` token is supplied. This is
 * needed in order to provide a consistent view of the results. Clients _should_ provide the same criterion as they
 * paginate through the results.
 *
 * ---
 *
 */
export interface SharesBrowseRequest {
    sharedByMe: boolean,
    filterPath?: string,
    /**
     * Requested number of items per page. Supported values: 10, 25, 50, 100, 250.
     */
    itemsPerPage?: number /* int32 */,
    /**
     * A token requesting the next page of items
     */
    next?: string,
    /**
     * Controls the consistency guarantees provided by the backend
     */
    consistency?: ("PREFER" | "REQUIRE"),
    /**
     * Items to skip ahead
     */
    itemsToSkip?: number /* int64 */,
}
export interface SharesCreateRequestItem {
    path: string,
    sharedWith: string,
}
export interface SharesDeleteRequestItem {
    path: string,
    sharedWith?: string,
}
export interface SharesApproveRequestItem {
    path: string,
}
export interface ProxiedRequest<T = unknown> {
    username: string,
    project?: string,
    request: T,
}
export namespace ucloud {
/**
 * Uploads a new chunk to the file at a given offset (uploadChunk)
 *
 * ![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
 * ![Auth: Public](https://img.shields.io/static/v1?label=Auth&message=Public&color=informational&style=flat-square)
 *
 * Uploads a new chunk to a file, specified by an upload session token. An upload session token can be
 * created using the [`files.createUpload`](#operation/files.createUpload) call.
 *
 * A session MUST be live for at least 30 minutes after the last `uploadChunk`
 * call was active. That is, since the last byte was transferred to this session or processed by the
 * provider. It is recommended that a provider keep a session for up to 48 hours. A session SHOULD NOT be
 * kept alive for longer than 48 hours.
 *
 * This call MUST add the HTTP request body to the file, backed by the session, at the specified offset.
 * Clients may use the special offset '-1' to indicate that the payload SHOULD be appended to the
 * Providers MUST NOT interpret the request body in any way, the payload is binary and SHOULD be written
 * to the file as is. Providers SHOULD reject offset values that don't fulfill one of the following
 * criteria:
 *
 * - Is equal to -1
 * - Is a valid offset in the file
 * - Is equal to the file size + 1
 *
 * Clients MUST send a chunk which is at most 32MB large (32,000,000 bytes). Clients MUST declare the size
 * of chunk by specifying the `Content-Length` header. Providers MUST reject values that are not valid or
 * are too large. Providers SHOULD assume that the `Content-Length` header is valid.
 * However, the providers MUST NOT wait indefinitely for all bytes to be delivered. A provider SHOULD
 * terminate a connection which has been idle for too long to avoid trivial DoS by specifying a large
 * `Content-Length` without sending any bytes.
 *
 * If a chunk upload is terminated before it is finished then a provider SHOULD NOT delete the data
 * already written to the  Clients SHOULD assume that the entire chunk has failed and SHOULD re-upload
 * the entire chunk.
 *
 * Providers SHOULD NOT cache a chunk before writing the data to the FS. Data SHOULD be streamed
 * directly into the
 *
 * Providers MUST NOT respond to this call before the data has been written to disk.
 *
 * Clients SHOULD avoid sending multiple chunks at the same time. Providers are allowed to reject parallel
 * calls to this endpoint.
 */
export function uploadChunk(): APICallParameters<{}, any /* unknown */> {
    return {
        context: "",
        method: "POST",
        path: "/ucloud/ucloud/chunked",
        reloadId: Math.random(),
    };
}
}
export namespace metadata_template {
export function create(
    request: BulkRequest<FileMetadataTemplateNS.Spec>
): APICallParameters<BulkRequest<FileMetadataTemplateNS.Spec>, BulkResponse<FindByStringId>> {
    return {
        context: "",
        method: "POST",
        path: "/api/files/metadataTemplate",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function browse(
    request: FileMetadataTemplatesBrowseRequest
): APICallParameters<FileMetadataTemplatesBrowseRequest, PageV2<FileMetadataTemplate>> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/api/files/metadataTemplate" + "/browse", {itemsPerPage: request.itemsPerPage, next: request.next, consistency: request.consistency, itemsToSkip: request.itemsToSkip}),
        parameters: request,
        reloadId: Math.random(),
    };
}
export function retrieve(
    request: FileMetadataTemplatesRetrieveRequest
): APICallParameters<FileMetadataTemplatesRetrieveRequest, FileMetadataTemplate> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/api/files/metadataTemplate" + "/retrieve", {id: request.id, version: request.version}),
        parameters: request,
        reloadId: Math.random(),
    };
}
export function deprecate(
    request: BulkRequest<FindByStringId>
): APICallParameters<BulkRequest<FindByStringId>, any /* unknown */> {
    return {
        context: "",
        method: "POST",
        path: "/api/files/metadataTemplate" + "/deprecate",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
}
export namespace FileMetadataDocumentNS {
export interface Spec {
    /**
     * The ID of the `FileMetadataTemplate` that this document conforms to
     */
    templateId: string,
    /**
     * The document which fills out the template
     */
    document: Record<string, any /* unknown */>,
    /**
     * Reason for this change
     */
    changeLog: string,
    product: accounting.ProductReference,
}
/**
 * Describes the current state of the `Resource`
 *
 * The contents of this field depends almost entirely on the specific `Resource` that this field is managing. Typically,
 * this will contain information such as:
 *
 * - A state value. For example, a compute `Job` might be `RUNNING`
 * - Key metrics about the resource.
 * - Related resources. For example, certain `Resource`s are bound to another `Resource` in a mutually exclusive way, this
 *   should be listed in the `status` section.
 *
 */
export interface Status {
    approval: ApprovalStatus,
    support?: accounting.providers.ResolvedSupport<any /* unknown */, any /* unknown */>,
}
export type ApprovalStatus = ApprovalStatusNS.Approved | ApprovalStatusNS.Pending | ApprovalStatusNS.Rejected | ApprovalStatusNS.NotRequired
export namespace ApprovalStatusNS {
export interface Approved {
    approvedBy: string,
    type: ("approved"),
}
export interface Pending {
    type: ("pending"),
}
export interface Rejected {
    rejectedBy: string,
    type: ("rejected"),
}
export interface NotRequired {
    type: ("not_required"),
}
}
}
export namespace LongRunningTaskNS {
export interface Complete {
    type: ("complete"),
}
export interface ContinuesInBackground {
    taskId: string,
    type: ("continues_in_background"),
}
}
export namespace FileMetadataTemplateNS {
export interface Spec {
    /**
     * The unique ID for this template
     */
    id: string,
    /**
     * The title of this template. It does not have to be unique.
     */
    title: string,
    /**
     * Version identifier for this version. It must be unique within a single template group.
     */
    version: string,
    /**
     * JSON-Schema for this document
     */
    schema: Record<string, any /* unknown */>,
    /**
     * Makes this template inheritable by descendants of the file that the template is attached to
     */
    inheritable: boolean,
    /**
     * If `true` then a user with `ADMINISTRATOR` rights must approve all changes to metadata
     */
    requireApproval: boolean,
    /**
     * Description of this template. Markdown is supported.
     */
    description: string,
    /**
     * A description of the change since last version. Markdown is supported.
     */
    changeLog: string,
    /**
     * Determines how this metadata template is namespaces
     *
     * NOTE: This is required to not change between versions
     */
    namespaceType: ("COLLABORATORS" | "PER_USER"),
    uiSchema?: Record<string, any /* unknown */>,
    product: accounting.ProductReference,
}
/**
 * Describes the current state of the `Resource`
 *
 * The contents of this field depends almost entirely on the specific `Resource` that this field is managing. Typically,
 * this will contain information such as:
 *
 * - A state value. For example, a compute `Job` might be `RUNNING`
 * - Key metrics about the resource.
 * - Related resources. For example, certain `Resource`s are bound to another `Resource` in a mutually exclusive way, this
 *   should be listed in the `status` section.
 *
 */
export interface Status {
    oldVersions: string[],
    support?: accounting.providers.ResolvedSupport<any /* unknown */, any /* unknown */>,
}
/**
 * Describes an update to the `Resource`
 *
 * Updates can optionally be fetched for a `Resource`. The updates describe how the `Resource` changes state over time.
 * The current state of a `Resource` can typically be read from its `status` field. Thus, it is typically not needed to
 * use the full update history if you only wish to know the _current_ state of a `Resource`.
 *
 * An update will typically contain information similar to the `status` field, for example:
 *
 * - A state value. For example, a compute `Job` might be `RUNNING`.
 * - Change in key metrics.
 * - Bindings to related `Resource`s.
 *
 */
export interface Update {
    /**
     * A timestamp referencing when UCloud received this update
     */
    timestamp: number /* int64 */,
    /**
     * A generic text message describing the current status of the `Resource`
     */
    status?: string,
}
}
export namespace FileMetadataOrDeletedNS {
/**
 * Indicates that the metadata document has been deleted is no longer in use
 */
export interface Deleted {
    /**
     * Reason for this change
     */
    changeLog: string,
    /**
     * Timestamp indicating when this change was made
     */
    createdAt: number /* int64 */,
    /**
     * A reference to the user who made this change
     */
    createdBy: string,
    status: FileMetadataDocumentNS.Status,
    type: ("deleted"),
}
}
export namespace files {
/**
 * Browse files of a directory (browse)
 *
 * ![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
 * ![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)
 *
 * Browses the contents of a directory.
 *
 * The results will be returned using the standard pagination API of UCloud. Consistency is slightly
 * relaxed for this endpoint as it is typically hard to enforce for filesystems. Provider's are heavily
 * encouraged to try and find all files on the first request and return information about them in
 * subsequent requests. For example, a client might list all file names in the initial request and use
 * this list for all subsequent requests and retrieve additional information about the  If the files
 * no longer exist then the provider should simply not include these results.
 */
export function browse(
    request: FilesBrowseRequest
): APICallParameters<FilesBrowseRequest, PageV2<UFile>> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/api/files" + "/browse", request),
        parameters: request,
        reloadId: Math.random(),
    };
}
/**
 * Copies a file from one path to another (copy)
 *
 * ![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
 * ![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)
 *
 * Copies a file from one path to another.
 *
 * The file can be of any type. If a directory is chosen then this will recursively copy all of its
 * children. This request might fail half-way through. This can potentially lead to a situation where
 * a partial file is left on the file-system. It is left to the user to clean up this
 *
 * This operation handles conflicts depending on the supplied `WriteConflictPolicy`.
 *
 * This is a long running task. As a result, this operation might respond with a status code which indicate
 * that it will continue in the background. Progress of this job can be followed using the task API.
 *
 * TODO What happens with metadata, acls and extended attributes?
 */
export function copy(
    request: BulkRequest<FilesCopyRequestItem>
): APICallParameters<BulkRequest<FilesCopyRequestItem>, BulkResponse<LongRunningTask>> {
    return {
        context: "",
        method: "POST",
        path: "/api/files" + "/copy",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
/**
 * Creates a download session between the user and the provider (createDownload)
 *
 * ![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
 * ![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)
 *
 * Creates a download session between the user and the provider.
 *
 * The returned endpoint will respond with a download to the user.
 */
export function createDownload(
    request: BulkRequest<FilesCreateDownloadRequestItem>
): APICallParameters<BulkRequest<FilesCreateDownloadRequestItem>, BulkResponse<FilesCreateDownloadResponseItem>> {
    return {
        context: "",
        method: "POST",
        path: "/api/files" + "/download",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
/**
 * Creates an upload session between the user and the provider (createUpload)
 *
 * ![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
 * ![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)
 *
 * Creates an upload session between the user and the provider.
 *
 * The returned endpoint will accept an upload from the user which will create a file at a location
 * specified in this request.
 */
export function createUpload(
    request: BulkRequest<FilesCreateUploadRequestItem>
): APICallParameters<BulkRequest<FilesCreateUploadRequestItem>, BulkResponse<FilesCreateUploadResponseItem>> {
    return {
        context: "",
        method: "POST",
        path: "/api/files" + "/upload",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
/**
 * Creates a folder (createFolder)
 *
 * ![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
 * ![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)
 *
 * Creates a folder at a specified location.
 *
 * This folder will automatically create parent directories if needed. This request may fail half-way
 * through and leave the file-system in an inconsistent state. It is up to the user to clean this up.
 */
export function createFolder(
    request: BulkRequest<FilesCreateFolderRequestItem>
): APICallParameters<BulkRequest<FilesCreateFolderRequestItem>, BulkResponse<LongRunningTask>> {
    return {
        context: "",
        method: "POST",
        path: "/api/files" + "/folder",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
/**
 * Deletes a file permanently from the file-system (delete)
 *
 * ![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
 * ![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)
 *
 * Deletes a file permanently from the file-system.
 *
 * This operation is permanent and cannot be undone. User interfaces should prefer using
 * [`trash`](#operation/trash) if it is supported by the provider.
 *
 * If the referenced file is a directory then this will delete all files recursively. This operation may
 * fail half-way through which will leave the file-system in an inconsistent state. It is the user's
 * responsibility to clean up this state.
 *
 * This is a long running task. As a result, this operation might respond with a status code which indicate
 * that it will continue in the background. Progress of this job can be followed using the task API.
 */
export function remove(
    request: BulkRequest<FindByPath>
): APICallParameters<BulkRequest<FindByPath>, BulkResponse<LongRunningTask>> {
    return {
        context: "",
        method: "DELETE",
        path: "/api/files",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
/**
 * Move a file from one path to another (move)
 *
 * ![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
 * ![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)
 *
 * Moves a file from one path to another.
 *
 * The file can be of any type. This request is also used for 'renames' of a  This is simply
 * considered a move within a single directory. This operation handles conflicts depending on the supplied
 * `WriteConflictPolicy`.
 *
 * This is a long running task. As a result, this operation might respond with a status code which indicate
 * that it will continue in the background. Progress of this job can be followed using the task API.
 */
export function move(
    request: BulkRequest<FilesMoveRequestItem>
): APICallParameters<BulkRequest<FilesMoveRequestItem>, BulkResponse<LongRunningTask>> {
    return {
        context: "",
        method: "POST",
        path: "/api/files" + "/move",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
/**
 * Retrieves information about a single file (retrieve)
 *
 * ![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
 * ![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)
 *
 * Retrieves information about a single
 *
 * This file can be of any type. Clients can request additional information about the file using the
 * `include*` flags of the request. Note that not all providers support all information. Clients can query
 * this information using [`collections.browse`](#operation/collections.browse) or
 * [`collections.retrieve`](#operation/collections.retrieve) with the `includeSupport` flag.
 */
export function retrieve(
    request: FilesRetrieveRequest
): APICallParameters<FilesRetrieveRequest, UFile> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/api/files" + "/retrieve", {path: request.path, includePermissions: request.includePermissions, includeTimestamps: request.includeTimestamps, includeSizes: request.includeSizes, includeUnixInfo: request.includeUnixInfo, includeMetadata: request.includeMetadata, allowUnsupportedInclude: request.allowUnsupportedInclude}),
        parameters: request,
        reloadId: Math.random(),
    };
}
/**
 * Moves a file to the trash (trash)
 *
 * ![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
 * ![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)
 *
 * Moves a file to the trash.
 *
 * This operation acts as a non-permanent delete for users. Users will be able to restore the file from
 * trash later, if needed. It is up to the provider to determine if the trash should be automatically
 * deleted and where this trash should be stored.
 *
 * Note that not all providers supports this endpoint. You can query [`collections.browse`](#operation/collections.browse)
 * or [`collections.retrieve`](#operation/collections.retrieve) with the `includeSupport` flag.
 *
 * This is a long running task. As a result, this operation might respond with a status code which indicate
 * that it will continue in the background. Progress of this job can be followed using the task API.
 */
export function trash(
    request: BulkRequest<FindByPath>
): APICallParameters<BulkRequest<FindByPath>, BulkResponse<LongRunningTask>> {
    return {
        context: "",
        method: "POST",
        path: "/api/files" + "/trash",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
/**
 * Updates the permissions of a file (updateAcl)
 *
 * ![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
 * ![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)
 *
 * Updates the permissions of a
 *
 * Note that not all providers supports this endpoint. You can query [`collections.browse`](#operation/collections.browse)
 * or [`collections.retrieve`](#operation/collections.retrieve) with the `includeSupport` flag.
 */
export function updateAcl(
    request: BulkRequest<FilesUpdateAclRequestItem>
): APICallParameters<BulkRequest<FilesUpdateAclRequestItem>, any /* unknown */> {
    return {
        context: "",
        method: "POST",
        path: "/api/files" + "/updateAcl",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
}
export namespace metadata {
export function create(
    request: BulkRequest<FileMetadataAddRequestItem>
): APICallParameters<BulkRequest<FileMetadataAddRequestItem>, any /* unknown */> {
    return {
        context: "",
        method: "POST",
        path: "/api/files/metadata",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function remove(
    request: BulkRequest<FileMetadataDeleteRequestItem>
): APICallParameters<BulkRequest<FileMetadataDeleteRequestItem>, any /* unknown */> {
    return {
        context: "",
        method: "DELETE",
        path: "/api/files/metadata",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function retrieveAll(
    request: FileMetadataRetrieveAllRequest
): APICallParameters<FileMetadataRetrieveAllRequest, FileMetadataRetrieveAllResponse> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/api/files/metadata" + "/retrieveAll", {parentPath: request.parentPath}),
        parameters: request,
        reloadId: Math.random(),
    };
}
export function moveMetadata(
    request: BulkRequest<FileMetadataMoveRequestItem>
): APICallParameters<BulkRequest<FileMetadataMoveRequestItem>, any /* unknown */> {
    return {
        context: "",
        method: "POST",
        path: "/api/files/metadata" + "/move",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
}
export namespace shares {
export function retrieve(
    request: SharesRetrieveRequest
): APICallParameters<SharesRetrieveRequest, Share> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/api/files/shares" + "/retrieve", {path: request.path}),
        parameters: request,
        reloadId: Math.random(),
    };
}
export function browse(
    request: SharesBrowseRequest
): APICallParameters<SharesBrowseRequest, PageV2<Share>> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/api/files/shares" + "/browse", {sharedByMe: request.sharedByMe, filterPath: request.filterPath, itemsPerPage: request.itemsPerPage, next: request.next, consistency: request.consistency, itemsToSkip: request.itemsToSkip}),
        parameters: request,
        reloadId: Math.random(),
    };
}
export function create(
    request: BulkRequest<SharesCreateRequestItem>
): APICallParameters<BulkRequest<SharesCreateRequestItem>, any /* unknown */> {
    return {
        context: "",
        method: "POST",
        path: "/api/files/shares",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function remove(
    request: BulkRequest<SharesDeleteRequestItem>
): APICallParameters<BulkRequest<SharesDeleteRequestItem>, any /* unknown */> {
    return {
        context: "",
        method: "DELETE",
        path: "/api/files/shares",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function approve(
    request: BulkRequest<SharesApproveRequestItem>
): APICallParameters<BulkRequest<SharesApproveRequestItem>, any /* unknown */> {
    return {
        context: "",
        method: "POST",
        path: "/api/files/shares" + "/approve",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
}
export namespace UFileNS {
/**
 * General system-level stats about a file
 */
export interface Stats {
    /**
     * The size of this file in bytes (Requires `includeSizes`)
     */
    sizeInBytes?: number /* int64 */,
    /**
     * The size of this file and any child (Requires `includeSizes`)
     */
    sizeIncludingChildrenInBytes?: number /* int64 */,
    /**
     * The modified at timestamp (Requires `includeTimestamps`)
     */
    modifiedAt?: number /* int64 */,
    /**
     * The created at timestamp (Requires `includeTimestamps`)
     */
    createdAt?: number /* int64 */,
    /**
     * The accessed at timestamp (Requires `includeTimestamps`)
     */
    accessedAt?: number /* int64 */,
    /**
     * The unix mode of a file (Requires `includeUnixInfo`
     */
    unixMode?: number /* int32 */,
    /**
     * The unix owner of a file as a UID (Requires `includeUnixInfo`)
     */
    unixOwner?: number /* int32 */,
    /**
     * The unix group of a file as a GID (Requires `includeUnixInfo`)
     */
    unixGroup?: number /* int32 */,
}
export interface Permissions {
    /**
     * What can the user, who requested this data, do with this file?
     */
    myself?: ("READ" | "WRITE" | "ADMINISTRATOR")[],
    /**
     * Information about what other users and entities can do with this file
     */
    others?: provider.ResourceAclEntry<("READ" | "WRITE" | "ADMINISTRATOR")>[],
}
}
}
export namespace ucloud {

export namespace files {
export function retrieve(
    request: orchestrator.ProxiedRequest<orchestrator.FilesRetrieveRequest>
): APICallParameters<orchestrator.ProxiedRequest<orchestrator.FilesRetrieveRequest>, orchestrator.UFile> {
    return {
        context: "",
        method: "POST",
        path: "/ucloud/ucloud/files" + "/retrieve",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function browse(
    request: orchestrator.ProxiedRequest<orchestrator.FilesBrowseRequest>
): APICallParameters<orchestrator.ProxiedRequest<orchestrator.FilesBrowseRequest>, PageV2<orchestrator.UFile>> {
    return {
        context: "",
        method: "POST",
        path: "/ucloud/ucloud/files" + "/browse",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function copy(
    request: orchestrator.ProxiedRequest<BulkRequest<orchestrator.FilesCopyRequestItem>>
): APICallParameters<orchestrator.ProxiedRequest<BulkRequest<orchestrator.FilesCopyRequestItem>>, BulkResponse<orchestrator.LongRunningTask>> {
    return {
        context: "",
        method: "POST",
        path: "/ucloud/ucloud/files" + "/copy",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function createUpload(
    request: orchestrator.ProxiedRequest<BulkRequest<orchestrator.FilesCreateUploadRequestItem>>
): APICallParameters<orchestrator.ProxiedRequest<BulkRequest<orchestrator.FilesCreateUploadRequestItem>>, BulkResponse<orchestrator.FilesCreateUploadResponseItem>> {
    return {
        context: "",
        method: "POST",
        path: "/ucloud/ucloud/files" + "/upload",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function move(
    request: orchestrator.ProxiedRequest<BulkRequest<orchestrator.FilesMoveRequestItem>>
): APICallParameters<orchestrator.ProxiedRequest<BulkRequest<orchestrator.FilesMoveRequestItem>>, BulkResponse<orchestrator.LongRunningTask>> {
    return {
        context: "",
        method: "POST",
        path: "/ucloud/ucloud/files" + "/move",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function remove(
    request: orchestrator.ProxiedRequest<BulkRequest<orchestrator.FindByPath>>
): APICallParameters<orchestrator.ProxiedRequest<BulkRequest<orchestrator.FindByPath>>, BulkResponse<orchestrator.LongRunningTask>> {
    return {
        context: "",
        method: "DELETE",
        path: "/ucloud/ucloud/files",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function trash(
    request: orchestrator.ProxiedRequest<BulkRequest<orchestrator.FindByPath>>
): APICallParameters<orchestrator.ProxiedRequest<BulkRequest<orchestrator.FindByPath>>, BulkResponse<orchestrator.LongRunningTask>> {
    return {
        context: "",
        method: "POST",
        path: "/ucloud/ucloud/files" + "/trash",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function createFolder(
    request: orchestrator.ProxiedRequest<BulkRequest<orchestrator.FilesCreateFolderRequestItem>>
): APICallParameters<orchestrator.ProxiedRequest<BulkRequest<orchestrator.FilesCreateFolderRequestItem>>, BulkResponse<orchestrator.LongRunningTask>> {
    return {
        context: "",
        method: "POST",
        path: "/ucloud/ucloud/files" + "/folder",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function updateAcl(
    request: orchestrator.ProxiedRequest<BulkRequest<orchestrator.FilesUpdateAclRequestItem>>
): APICallParameters<orchestrator.ProxiedRequest<BulkRequest<orchestrator.FilesUpdateAclRequestItem>>, any /* unknown */> {
    return {
        context: "",
        method: "POST",
        path: "/ucloud/ucloud/files" + "/updateAcl",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
}
}
}
export namespace compute {
export interface JobsCreateResponse {
    ids: string[],
}
export interface JobSpecification {
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
    replicas: number /* int32 */,
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
    /**
     * The resolved compute suport by the provider.
     *
     * This attribute is not included by default unless `includeSupport` is defined.
     */
    resolvedSupport?: ComputeSupport,
}
export interface NameAndVersion {
    name: string,
    version: string,
}
/**
 * An `AppParameterValue` is value which is supplied to a parameter of an `Application`.

 * Each value type can is type-compatible with one or more `ApplicationParameter`s. The effect of a specific value depends
 * on its use-site, and the type of its associated parameter.
 *
 * `ApplicationParameter`s have the following usage sites (see [here](/backend/app-store-service/wiki/apps.md) for a
 * comprehensive guide):
 *
 * - Invocation: This affects the command line arguments passed to the software.
 * - Environment variables: This affects the environment variables passed to the software.
 * - Resources: This only affects the resources which are imported into the software environment. Not all values can be
 *   used as a resource.
 *
 */
export type AppParameterValue = AppParameterValueNS.File | AppParameterValueNS.Bool | AppParameterValueNS.Text | AppParameterValueNS.TextArea | AppParameterValueNS.Integer | AppParameterValueNS.FloatingPoint | AppParameterValueNS.Peer | AppParameterValueNS.License | AppParameterValueNS.BlockStorage | AppParameterValueNS.Network | AppParameterValueNS.Ingress
export interface SimpleDuration {
    hours: number /* int32 */,
    minutes: number /* int32 */,
    seconds: number /* int32 */,
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
    flavorName?: string,
    group?: ApplicationGroup
}
export interface ApplicationInvocationDescription {
    tool: ToolReference,
    invocation: InvocationParameter[],
    parameters: ApplicationParameter[],
    outputFileGlobs: string[],
    applicationType: ("BATCH" | "VNC" | "WEB"),
    vnc?: VncDescription,
    web?: WebDescription,
    ssh?: SshDescription,
    container?: ContainerDescription,
    environment?: Record<string, InvocationParameter>,
    allowAdditionalMounts?: boolean,
    allowAdditionalPeers?: boolean,
    allowPublicLink?: boolean,
    allowMultiNode: boolean,
    allowPublicIp: boolean,
    fileExtensions: string[],
    licenseServers: string[],
    // shouldAllowAdditionalMounts: boolean,
    // shouldAllowAdditionalPeers: boolean,
}
export interface ToolReference {
    name: string,
    version: string,
    tool?: Tool,
}
export interface Tool {
    owner: string,
    createdAt: number /* int64 */,
    modifiedAt: number /* int64 */,
    description: NormalizedToolDescription,
}
export interface NormalizedToolDescription {
    info: NameAndVersion,
    container?: string,
    defaultNumberOfNodes: number /* int32 */,
    defaultTimeAllocation: SimpleDuration,
    requiredModules: string[],
    authors: string[],
    title: string,
    description: string,
    backend: ("SINGULARITY" | "DOCKER" | "VIRTUAL_MACHINE" | "NATIVE"),
    license: string,
    image?: string,
    supportedProviders?: string[],
}
export type InvocationParameter = EnvironmentVariableParameter | WordInvocationParameter | VariableInvocationParameter | BooleanFlagParameter
export interface EnvironmentVariableParameter {
    variable: string,
    type: ("env"),
}
export interface WordInvocationParameter {
    word: string,
    type: ("word"),
}
export interface VariableInvocationParameter {
    variableNames: string[],
    prefixGlobal: string,
    suffixGlobal: string,
    prefixVariable: string,
    suffixVariable: string,
    isPrefixVariablePartOfArg: boolean,
    isSuffixVariablePartOfArg: boolean,
    type: ("var"),
}
export interface BooleanFlagParameter {
    variableName: string,
    flag: string,
    type: ("bool_flag"),
}
export type ApplicationParameter = ApplicationParameterNS.InputFile | ApplicationParameterNS.InputDirectory | ApplicationParameterNS.Text | ApplicationParameterNS.TextArea | ApplicationParameterNS.Integer | ApplicationParameterNS.FloatingPoint | ApplicationParameterNS.Bool | ApplicationParameterNS.Enumeration | ApplicationParameterNS.Peer | ApplicationParameterNS.Ingress | ApplicationParameterNS.LicenseServer | ApplicationParameterNS.NetworkIP
export interface VncDescription {
    password?: string,
    port: number /* int32 */,
}
export interface WebDescription {
    port: number /* int32 */,
}

export interface SshDescription {
    mode: "DISABLED" | "OPTIONAL" | "MANDATORY";
}

export interface ContainerDescription {
    changeWorkingDirectory: boolean,
    runAsRoot: boolean,
    runAsRealUser: boolean,
}
export interface ComputeSupport {
    /**
     * Support for `Tool`s using the `DOCKER` backend
     */
    docker: ComputeSupportNS.Docker,
    native: ComputeSupportNS.Docker,
    /**
     * Support for `Tool`s using the `VIRTUAL_MACHINE` backend
     */
    virtualMachine: ComputeSupportNS.VirtualMachine,
}
/**
 * A `Job` in UCloud is the core abstraction used to describe a unit of computation.
 *
 * They provide users a way to run their computations through a workflow similar to their own workstations but scaling to
 * much bigger and more machines. In a simplified view, a `Job` describes the following information:
 *
 * - The `Application` which the provider should/is/has run (see [app-store](/backend/app-store-service/README.md))
 * - The [input parameters](/backend/app-orchestrator-service/wiki/parameters.md),
 *   [files and other resources](/backend/app-orchestrator-service/wiki/resources.md) required by a `Job`
 * - A reference to the appropriate [compute infrastructure](/backend/app-orchestrator-service/wiki/products.md), this
 *   includes a reference to the _provider_
 * - The user who launched the `Job` and in which [`Project`](/backend/project-service/README.md)
 *
 * A `Job` is started by a user request containing the `specification` of a `Job`. This information is verified by the UCloud
 * orchestrator and passed to the provider referenced by the `Job` itself. Assuming that the provider accepts this
 * information, the `Job` is placed in its initial state, `IN_QUEUE`. You can read more about the requirements of the
 * compute environment and how to launch the software
 * correctly [here](/backend/app-orchestrator-service/wiki/job_launch.md).
 *
 * At this point, the provider has acted on this information by placing the `Job` in its own equivalent of
 * a [job queue](/backend/app-orchestrator-service/wiki/provider.md#job-scheduler). Once the provider realizes that
 * the `Job`
 * is running, it will contact UCloud and place the `Job` in the `RUNNING` state. This indicates to UCloud that log files
 * can be retrieved and that [interactive interfaces](/backend/app-orchestrator-service/wiki/interactive.md) (`VNC`/`WEB`)
 * are available.
 *
 * Once the `Application` terminates at the provider, the provider will update the state to `SUCCESS`. A `Job` has
 * terminated successfully if no internal error occurred in UCloud and in the provider. This means that a `Job` whose
 * software returns with a non-zero exit code is still considered successful. A `Job` might, for example, be placed
 * in `FAILURE` if the `Application` crashed due to a hardware/scheduler failure. Both `SUCCESS` or `FAILURE` are terminal
 * state. Any `Job` which is in a terminal state can no longer receive any updates or change its state.
 *
 * At any point after the user submits the `Job`, they may request cancellation of the `Job`. This will stop the `Job`,
 * delete any [ephemeral resources](/backend/app-orchestrator-service/wiki/job_launch.md#ephemeral-resources) and release
 * any [bound resources](/backend/app-orchestrator-service/wiki/parameters.md#resources).
 */
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
    owner: provider.ResourceOwner,
    /**
     * A list of status updates from the compute backend.
     *
     * The status updates tell a story of what happened with the job. This list is ordered by the timestamp in ascending order. The current state of the job will always be the last element. `updates` is guaranteed to always contain at least one element.
     */
    updates: JobUpdate[],
    /**
     * Contains information related to billing information for this `Resource`
     * @deprecated
     */
    billing: JobBilling,
    /**
     * The specification used to launch this job.
     *
     * This property is always available but must be explicitly requested.
     */
    specification: JobSpecification,
    /**
     * A summary of the `Job`'s current status
     */
    status: JobStatus,
    /**
     * Timestamp referencing when the request for creation was received by UCloud
     */
    createdAt: number /* int64 */,
    /**
     * Information regarding the output of this job.
     */
    output?: JobOutput,
    /**
     * An ACL for this `Resource`
     * @deprecated
     */
    acl?: provider.ResourceAclEntry[],
    /**
     * Permissions assigned to this resource
     *
     * A null value indicates that permissions are not supported by this resource type.
     */
    permissions?: provider.ResourcePermissions,
    providerGeneratedId?: string,
}
/**
 * Describes an update to the `Resource`
 *
 * Updates can optionally be fetched for a `Resource`. The updates describe how the `Resource` changes state over time.
 * The current state of a `Resource` can typically be read from its `status` field. Thus, it is typically not needed to
 * use the full update history if you only wish to know the _current_ state of a `Resource`.
 *
 * An update will typically contain information similar to the `status` field, for example:
 *
 * - A state value. For example, a compute `Job` might be `RUNNING`.
 * - Change in key metrics.
 * - Bindings to related `Resource`s.
 *
 */
export interface JobUpdate {
    /**
     * A timestamp referencing when UCloud received this update
     */
    timestamp: number /* int64 */,
    state?: ("IN_QUEUE" | "RUNNING" | "CANCELING" | "SUCCESS" | "FAILURE" | "EXPIRED" | "SUSPENDED"),
    /**
     * A generic text message describing the current status of the `Resource`
     */
    status?: string,
}
/**
 * Contains information related to the accounting/billing of a `Resource`
 *
 * Note that this object contains the price of the `Product`. This price may differ, over-time, from the actual price of
 * the `Product`. This allows providers to provide a gradual change of price for products. By allowing existing `Resource`s
 * to be charged a different price than newly launched products.
 */
export interface JobBilling {
    /**
     * The amount of credits charged to the `owner` of this job
     */
    creditsCharged: number /* int64 */,
    /**
     * The unit price of this job
     */
    pricePerUnit: number /* int64 */,
    __creditsAllocatedToWalletDoNotDependOn__: number /* int64 */,
}
/**
 * Describes the current state of the `Resource`
 *
 * The contents of this field depends almost entirely on the specific `Resource` that this field is managing. Typically,
 * this will contain information such as:
 *
 * - A state value. For example, a compute `Job` might be `RUNNING`
 * - Key metrics about the resource.
 * - Related resources. For example, certain `Resource`s are bound to another `Resource` in a mutually exclusive way, this
 *   should be listed in the `status` section.
 *
 */
export interface JobStatus {
    /**
     * The current of state of the `Job`.
     *
     * This will match the latest state set in the `updates`
     */
    state: ("IN_QUEUE" | "RUNNING" | "CANCELING" | "SUCCESS" | "FAILURE" | "EXPIRED" | "SUSPENDED"),
    /**
     * Timestamp matching when the `Job` most recently transitioned to the `RUNNING` state.
     *
     * For `Job`s which suspend this might occur multiple times. This will always point to the latest pointin time it started running.
     */
    startedAt?: number /* int64 */,
    /**
     * Timestamp matching when the `Job` is set to expire.
     *
     * This is generally equal to `startedAt + timeAllocation`. Note that this field might be `null` if the `Job` has no associated deadline. For `Job`s that suspend however, this is more likely to beequal to the initial `RUNNING` state + `timeAllocation`.
     */
    expiresAt?: number /* int64 */,
    support?: accounting.providers.ResolvedSupport<any /* unknown */, any /* unknown */>,
}
export interface JobOutput {
    outputFolder: string,
}
export interface JobsRetrieveRequest {
    id: string,
    /**
     * Includes `specification.parameters` and `specification.resources`
     */
    includeParameters?: boolean,
    /**
     * Includes `updates`
     */
    includeUpdates?: boolean,
    /**
     * Includes `specification.resolvedApplication`
     */
    includeApplication?: boolean,
    /**
     * Includes `specification.resolvedProduct`
     */
    includeProduct?: boolean,
    /**
     * Includes `specification.resolvedSupport`
     */
    includeSupport?: boolean,
}
export interface JobsRetrieveUtilizationResponse {
    capacity: CpuAndMemory,
    usedCapacity: CpuAndMemory,
    queueStatus: QueueStatus,
}
export interface CpuAndMemory {
    cpu: number /* float64 */,
    memory: number /* int64 */,
}
export interface QueueStatus {
    running: number /* int32 */,
    pending: number /* int32 */,
}
export interface JobsRetrieveUtilizationRequest {
    jobId: string,
}
/**
 * The base type for requesting paginated content.
 *
 * Paginated content can be requested with one of the following `consistency` guarantees, this greatly changes the
 * semantics of the call:
 *
 * | Consistency | Description |
 * |-------------|-------------|
 * | `PREFER` | Consistency is preferred but not required. An inconsistent snapshot might be returned. |
 * | `REQUIRE` | Consistency is required. A request will fail if consistency is no longer guaranteed. |
 *
 * The `consistency` refers to if collecting all the results via the pagination API are _consistent_. We consider the
 * results to be consistent if it contains a complete view at some point in time. In practice this means that the results
 * must contain all the items, in the correct order and without duplicates.
 *
 * If you use the `PREFER` consistency then you may receive in-complete results that might appear out-of-order and can
 * contain duplicate items. UCloud will still attempt to serve a snapshot which appears mostly consistent. This is helpful
 * for user-interfaces which do not strictly depend on consistency but would still prefer something which is mostly
 * consistent.
 *
 * The results might become inconsistent if the client either takes too long, or a service instance goes down while
 * fetching the results. UCloud attempts to keep each `next` token alive for at least one minute before invalidating it.
 * This does not mean that a client must collect all results within a minute but rather that they must fetch the next page
 * within a minute of the last page. If this is not feasible and consistency is not required then `PREFER` should be used.
 *
 * ---
 *
 * __📝 NOTE:__ Services are allowed to ignore extra criteria of the request if the `next` token is supplied. This is
 * needed in order to provide a consistent view of the results. Clients _should_ provide the same criterion as they
 * paginate through the results.
 *
 * ---
 *
 */
export interface JobsBrowseRequest {
    /**
     * Requested number of items per page. Supported values: 10, 25, 50, 100, 250.
     */
    itemsPerPage: number /* int32 */,
    /**
     * A token requesting the next page of items
     */
    next?: string,
    /**
     * Controls the consistency guarantees provided by the backend
     */
    consistency?: ("PREFER" | "REQUIRE"),
    /**
     * Items to skip ahead
     */
    itemsToSkip?: number /* int64 */,
    /**
     * Includes `specification.parameters` and `specification.resources`
     */
    includeParameters?: boolean,
    /**
     * Includes `updates`
     */
    includeUpdates?: boolean,
    /**
     * Includes `specification.resolvedApplication`
     */
    includeApplication?: boolean,
    /**
     * Includes `specification.resolvedProduct`
     */
    includeProduct?: boolean,
    /**
     * Includes `specification.resolvedSupport`
     */
    includeSupport?: boolean,
    sortBy?: ("CREATED_AT" | "STATE" | "APPLICATION"),
    filterApplication?: string,
    filterLaunchedBy?: string,
    filterState?: ("IN_QUEUE" | "RUNNING" | "CANCELING" | "SUCCESS" | "FAILURE" | "EXPIRED" | "SUSPENDED"),
    filterTitle?: string,
    filterBefore?: number /* int64 */,
    filterAfter?: number /* int64 */,
}
export interface JobsExtendRequestItem {
    jobId: string,
    requestedTime: SimpleDuration,
}
export interface JobsOpenInteractiveSessionResponse {
    responses: OpenSessionWithProvider[],
}
export interface OpenSessionWithProvider {
    providerDomain: string,
    providerId: string,
    session: OpenSession,
}
export type OpenSession = OpenSessionNS.Shell | OpenSessionNS.Web | OpenSessionNS.Vnc
export interface JobsOpenInteractiveSessionRequestItem {
    id: string,
    rank: number /* int32 */,
    sessionType: ("WEB" | "VNC" | "SHELL"),
}
export interface JobsRetrieveProductsResponse {
    productsByProvider: Record<string, ComputeProductSupportResolved[]>,
}
export interface ComputeProductSupportResolved {
    product: accounting.ProductNS.Compute,
    support: ComputeSupport,
}
export interface JobsRetrieveProductsRequest {
    providers: string,
}
export interface JobsControlUpdateRequestItem {
    jobId: string,
    state?: ("IN_QUEUE" | "RUNNING" | "CANCELING" | "SUCCESS" | "FAILURE" | "EXPIRED" | "SUSPENDED"),
    status?: string,
    /**
     * Indicates that this request should be ignored if the current state does not match the expected state
     */
    expectedState?: ("IN_QUEUE" | "RUNNING" | "CANCELING" | "SUCCESS" | "FAILURE" | "EXPIRED" | "SUSPENDED"),
    /**
     * Indicates that this request should be ignored if the current state equals `state`
     */
    expectedDifferentState?: boolean,
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
    /**
     * Includes `specification.parameters` and `specification.resources`
     */
    includeParameters?: boolean,
    /**
     * Includes `updates`
     */
    includeUpdates?: boolean,
    /**
     * Includes `specification.resolvedApplication`
     */
    includeApplication?: boolean,
    /**
     * Includes `specification.resolvedProduct`
     */
    includeProduct?: boolean,
    /**
     * Includes `specification.resolvedSupport`
     */
    includeSupport?: boolean,
}
/**
 * A `License` for use in `Job`s
 */
export interface License {
    /**
     * A unique identifier referencing the `Resource`
     *
     * The ID is unique across a provider for a single resource type.
     */
    id: string,
    specification: LicenseSpecification,
    /**
     * Information about the owner of this resource
     */
    owner: provider.ResourceOwner,
    /**
     * Information about when this resource was created
     */
    createdAt: number /* int64 */,
    /**
     * The current status of this resource
     */
    status: LicenseStatus,
    /**
     * Billing information associated with this `License`
     * @deprecated
     */
    billing: LicenseBilling,
    /**
     * A list of updates for this `License`
     */
    updates: LicenseUpdate[],
    resolvedProduct?: accounting.ProductNS.License,
    /**
     * An ACL for this `Resource`
     * @deprecated
     */
    acl?: provider.ResourceAclEntry<("USE")>[],
    /**
     * Permissions assigned to this resource
     *
     * A null value indicates that permissions are not supported by this resource type.
     */
    permissions?: provider.ResourcePermissions,
    providerGeneratedId?: string,
}
export interface LicenseSpecification {
    /**
     * The product used for the `License`
     */
    product: accounting.ProductReference,
}
/**
 * The status of an `License`
 */
export interface LicenseStatus {
    state: ("PREPARING" | "READY" | "UNAVAILABLE"),
    support?: accounting.providers.ResolvedSupport<any /* unknown */, any /* unknown */>,
}
/**
 * Contains information related to the accounting/billing of a `Resource`
 *
 * Note that this object contains the price of the `Product`. This price may differ, over-time, from the actual price of
 * the `Product`. This allows providers to provide a gradual change of price for products. By allowing existing `Resource`s
 * to be charged a different price than newly launched products.
 */
export interface LicenseBilling {
    /**
     * The price per unit. This can differ from current price of `Product`
     */
    pricePerUnit: number /* int64 */,
    /**
     * Amount of credits charged in total for this `Resource`
     */
    creditsCharged: number /* int64 */,
}
/**
 * Describes an update to the `Resource`
 *
 * Updates can optionally be fetched for a `Resource`. The updates describe how the `Resource` changes state over time.
 * The current state of a `Resource` can typically be read from its `status` field. Thus, it is typically not needed to
 * use the full update history if you only wish to know the _current_ state of a `Resource`.
 *
 * An update will typically contain information similar to the `status` field, for example:
 *
 * - A state value. For example, a compute `Job` might be `RUNNING`.
 * - Change in key metrics.
 * - Bindings to related `Resource`s.
 *
 */
export interface LicenseUpdate {
    /**
     * A timestamp for when this update was registered by UCloud
     */
    timestamp: number /* int64 */,
    /**
     * The new state that the `License` transitioned to (if any)
     */
    state?: ("PREPARING" | "READY" | "UNAVAILABLE"),
    /**
     * A new status message for the `License` (if any)
     */
    status?: string,
}
/**
 * The base type for requesting paginated content.
 *
 * Paginated content can be requested with one of the following `consistency` guarantees, this greatly changes the
 * semantics of the call:
 *
 * | Consistency | Description |
 * |-------------|-------------|
 * | `PREFER` | Consistency is preferred but not required. An inconsistent snapshot might be returned. |
 * | `REQUIRE` | Consistency is required. A request will fail if consistency is no longer guaranteed. |
 *
 * The `consistency` refers to if collecting all the results via the pagination API are _consistent_. We consider the
 * results to be consistent if it contains a complete view at some point in time. In practice this means that the results
 * must contain all the items, in the correct order and without duplicates.
 *
 * If you use the `PREFER` consistency then you may receive in-complete results that might appear out-of-order and can
 * contain duplicate items. UCloud will still attempt to serve a snapshot which appears mostly consistent. This is helpful
 * for user-interfaces which do not strictly depend on consistency but would still prefer something which is mostly
 * consistent.
 *
 * The results might become inconsistent if the client either takes too long, or a service instance goes down while
 * fetching the results. UCloud attempts to keep each `next` token alive for at least one minute before invalidating it.
 * This does not mean that a client must collect all results within a minute but rather that they must fetch the next page
 * within a minute of the last page. If this is not feasible and consistency is not required then `PREFER` should be used.
 *
 * ---
 *
 * __📝 NOTE:__ Services are allowed to ignore extra criteria of the request if the `next` token is supplied. This is
 * needed in order to provide a consistent view of the results. Clients _should_ provide the same criterion as they
 * paginate through the results.
 *
 * ---
 *
 */
export interface LicensesBrowseRequest {
    /**
     * Includes `updates`
     */
    includeUpdates?: boolean,
    /**
     * Includes `resolvedProduct`
     */
    includeProduct?: boolean,
    /**
     * Includes `acl`
     */
    includeAcl?: boolean,
    /**
     * Requested number of items per page. Supported values: 10, 25, 50, 100, 250.
     */
    itemsPerPage?: number /* int32 */,
    /**
     * A token requesting the next page of items
     */
    next?: string,
    /**
     * Controls the consistency guarantees provided by the backend
     */
    consistency?: ("PREFER" | "REQUIRE"),
    /**
     * Items to skip ahead
     */
    itemsToSkip?: number /* int64 */,
    provider?: string,
    tag?: string,
}
export interface LicensesCreateResponse {
    ids: string[],
}
export interface LicenseRetrieve {
    id: string,
}
export interface LicenseRetrieveWithFlags {
    id: string,
    /**
     * Includes `updates`
     */
    includeUpdates?: boolean,
    /**
     * Includes `resolvedProduct`
     */
    includeProduct?: boolean,
    /**
     * Includes `acl`
     */
    includeAcl?: boolean,
}
export interface LicensesUpdateAclRequestItem {
    id: string,
    acl: provider.ResourceAclEntry<("USE")>[],
}
export interface LicenseControlUpdateRequestItem {
    id: string,
    state?: ("PREPARING" | "READY" | "UNAVAILABLE"),
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
    units: number /* int64 */,
}
/**
 * A `NetworkIP` for use in `Job`s
 */
export interface NetworkIP {
    /**
     * A unique identifier referencing the `Resource`
     *
     * The ID is unique across a provider for a single resource type.
     */
    id: string,
    specification: NetworkIPSpecification,
    /**
     * Information about the owner of this resource
     */
    owner: provider.ResourceOwner,
    /**
     * Information about when this resource was created
     */
    createdAt: number /* int64 */,
    /**
     * The current status of this resource
     */
    status: NetworkIPStatus,
    /**
     * Billing information associated with this `NetworkIP`
     * @deprecated
     */
    billing: NetworkIPBilling,
    /**
     * A list of updates for this `NetworkIP`
     */
    updates: NetworkIPUpdate[],
    resolvedProduct?: accounting.ProductNS.NetworkIP,
    /**
     * An ACL for this `Resource`
     * @deprecated
     */
    acl?: provider.ResourceAclEntry<("USE")>[],
    /**
     * Permissions assigned to this resource
     *
     * A null value indicates that permissions are not supported by this resource type.
     */
    permissions?: provider.ResourcePermissions,
    providerGeneratedId?: string,
}
export interface NetworkIPSpecification {
    /**
     * The product used for the `NetworkIP`
     */
    product: accounting.ProductReference,
    firewall?: NetworkIPSpecificationNS.Firewall,
}
export interface PortRangeAndProto {
    start: number /* int32 */,
    end: number /* int32 */,
    protocol: ("TCP" | "UDP"),
}
/**
 * The status of an `NetworkIP`
 */
export interface NetworkIPStatus {
    state: ("PREPARING" | "READY" | "UNAVAILABLE"),
    /**
     * The ID of the `Job` that this `NetworkIP` is currently bound to
     */
    boundTo?: string,
    /**
     * The externally accessible IP address allocated to this `NetworkIP`
     */
    ipAddress?: string,
    support?: accounting.providers.ResolvedSupport<any /* unknown */, any /* unknown */>,
}
/**
 * Contains information related to the accounting/billing of a `Resource`
 *
 * Note that this object contains the price of the `Product`. This price may differ, over-time, from the actual price of
 * the `Product`. This allows providers to provide a gradual change of price for products. By allowing existing `Resource`s
 * to be charged a different price than newly launched products.
 */
export interface NetworkIPBilling {
    /**
     * The price per unit. This can differ from current price of `Product`
     */
    pricePerUnit: number /* int64 */,
    /**
     * Amount of credits charged in total for this `Resource`
     */
    creditsCharged: number /* int64 */,
}
/**
 * Describes an update to the `Resource`
 *
 * Updates can optionally be fetched for a `Resource`. The updates describe how the `Resource` changes state over time.
 * The current state of a `Resource` can typically be read from its `status` field. Thus, it is typically not needed to
 * use the full update history if you only wish to know the _current_ state of a `Resource`.
 *
 * An update will typically contain information similar to the `status` field, for example:
 *
 * - A state value. For example, a compute `Job` might be `RUNNING`.
 * - Change in key metrics.
 * - Bindings to related `Resource`s.
 *
 */
export interface NetworkIPUpdate {
    /**
     * A timestamp for when this update was registered by UCloud
     */
    timestamp: number /* int64 */,
    /**
     * The new state that the `NetworkIP` transitioned to (if any)
     */
    state?: ("PREPARING" | "READY" | "UNAVAILABLE"),
    /**
     * A new status message for the `NetworkIP` (if any)
     */
    status?: string,
    didBind: boolean,
    newBinding?: string,
    changeIpAddress?: boolean,
    newIpAddress?: string,
}
/**
 * The base type for requesting paginated content.
 *
 * Paginated content can be requested with one of the following `consistency` guarantees, this greatly changes the
 * semantics of the call:
 *
 * | Consistency | Description |
 * |-------------|-------------|
 * | `PREFER` | Consistency is preferred but not required. An inconsistent snapshot might be returned. |
 * | `REQUIRE` | Consistency is required. A request will fail if consistency is no longer guaranteed. |
 *
 * The `consistency` refers to if collecting all the results via the pagination API are _consistent_. We consider the
 * results to be consistent if it contains a complete view at some point in time. In practice this means that the results
 * must contain all the items, in the correct order and without duplicates.
 *
 * If you use the `PREFER` consistency then you may receive in-complete results that might appear out-of-order and can
 * contain duplicate items. UCloud will still attempt to serve a snapshot which appears mostly consistent. This is helpful
 * for user-interfaces which do not strictly depend on consistency but would still prefer something which is mostly
 * consistent.
 *
 * The results might become inconsistent if the client either takes too long, or a service instance goes down while
 * fetching the results. UCloud attempts to keep each `next` token alive for at least one minute before invalidating it.
 * This does not mean that a client must collect all results within a minute but rather that they must fetch the next page
 * within a minute of the last page. If this is not feasible and consistency is not required then `PREFER` should be used.
 *
 * ---
 *
 * __📝 NOTE:__ Services are allowed to ignore extra criteria of the request if the `next` token is supplied. This is
 * needed in order to provide a consistent view of the results. Clients _should_ provide the same criterion as they
 * paginate through the results.
 *
 * ---
 *
 */
export interface NetworkIPsBrowseRequest {
    /**
     * Includes `updates`
     */
    includeUpdates?: boolean,
    /**
     * Includes `resolvedProduct`
     */
    includeProduct?: boolean,
    /**
     * Includes `acl`
     */
    includeAcl?: boolean,
    /**
     * Requested number of items per page. Supported values: 10, 25, 50, 100, 250.
     */
    itemsPerPage?: number /* int32 */,
    /**
     * A token requesting the next page of items
     */
    next?: string,
    /**
     * Controls the consistency guarantees provided by the backend
     */
    consistency?: ("PREFER" | "REQUIRE"),
    /**
     * Items to skip ahead
     */
    itemsToSkip?: number /* int64 */,
    provider?: string,
}
export interface NetworkIPsCreateResponse {
    ids: string[],
}
export interface NetworkIPRetrieve {
    id: string,
}
export interface NetworkIPRetrieveWithFlags {
    id: string,
    /**
     * Includes `updates`
     */
    includeUpdates?: boolean,
    /**
     * Includes `resolvedProduct`
     */
    includeProduct?: boolean,
    /**
     * Includes `acl`
     */
    includeAcl?: boolean,
}
export interface NetworkIPsUpdateAclRequestItem {
    id: string,
    acl: provider.ResourceAclEntry<("USE")>[],
}
export interface FirewallAndId {
    id: string,
    firewall: NetworkIPSpecificationNS.Firewall,
}
export interface NetworkIPControlUpdateRequestItem {
    id: string,
    state?: ("PREPARING" | "READY" | "UNAVAILABLE"),
    status?: string,
    clearBindingToJob?: boolean,
    changeIpAddress?: boolean,
    newIpAddress?: string,
}
export interface NetworkIPControlChargeCreditsResponse {
    /**
     * A list of jobs which could not be charged due to lack of funds. If all jobs were charged successfully then this will empty.
     */
    insufficientFunds: NetworkIPId[],
    /**
     * A list of ingresses which could not be charged due to it being a duplicate charge. If all ingresses were charged successfully this will be empty.
     */
    duplicateCharges: NetworkIPId[],
}
export interface NetworkIPId {
    id: string,
}
export interface NetworkIPControlChargeCreditsRequestItem {
    /**
     * The ID of the `NetworkIP`
     */
    id: string,
    /**
     * The ID of the charge
     *
     * This charge ID must be unique for the `NetworkIP`, UCloud will reject charges which are not unique.
     */
    chargeId: string,
    /**
     * Amount of units to charge the user
     */
    units: number /* int64 */,
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
    permission: ("LAUNCH")[],
}
export interface DetailedEntityWithPermission {
    entity: DetailedAccessEntity,
    permission: ("LAUNCH"),
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
    rights: ("LAUNCH"),
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
    itemsPerPage?: number /* int32 */,
    page?: number /* int32 */,
}
export interface DeleteAppRequest {
    appName: string,
    appVersion: string,
}
export interface FindLatestByToolRequest {
    tool: string,
    itemsPerPage?: number /* int32 */,
    page?: number /* int32 */,
}
export interface FindByNameAndVersion {
    name: string,
    version: string,
}
export interface ClearLogoRequest {
    name: string,
}
export interface FetchLogoRequest {
    name: string,
}
export interface CreateTagsRequest {
    tags: string[],
    groupId: number,
}
export interface TagSearchRequest {
    query: string,
    excludeTools?: string,
    itemsPerPage?: number /* int32 */,
    page?: number /* int32 */,
}
export interface ListTagsRequest {}
export interface AppStoreSectionsRequest {
    page: string,
}
export interface AppStoreSections {
    sections: AppStoreSection[],
}
export interface AppStoreSection {
    id: number,
    name: string,
    featured: ApplicationGroup[],
    items: ApplicationGroup[],
}
export enum AppStoreSectionType {
    TAG = "tag",
    TOOL = "tool"
}
export interface AppSearchRequest {
    query: string,
    itemsPerPage?: number /* int32 */,
    page?: number /* int32 */,
}
export interface AdvancedSearchRequest {
    query?: string,
    tags?: string[],
    showAllVersions: boolean,
    itemsPerPage?: number /* int32 */,
    page?: number /* int32 */,
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
export interface JobsProviderExtendRequestItem {
    job: Job,
    requestedTime: SimpleDuration,
}
export interface JobsProviderOpenInteractiveSessionResponse {
    sessions: OpenSession[],
}
export interface JobsProviderOpenInteractiveSessionRequestItem {
    job: Job,
    rank: number /* int32 */,
    sessionType: ("WEB" | "VNC" | "SHELL"),
}
export interface JobsProviderRetrieveProductsResponse {
    products: ComputeProductSupport[],
}
export interface ComputeProductSupport {
    product: accounting.ProductReference,
    support: ComputeSupport,
}
export interface JobsProviderUtilizationResponse {
    capacity: CpuAndMemory,
    usedCapacity: CpuAndMemory,
    queueStatus: QueueStatus,
}
export namespace ComputeSupportNS {
export interface Docker {
    /**
     * Flag to enable/disable this feature
     *
     * All other flags are ignored if this is `false`.
     */
    enabled?: boolean,
    /**
     * Flag to enable/disable the interactive interface of `WEB` `Application`s
     */
    web?: boolean,
    /**
     * Flag to enable/disable the interactive interface of `VNC` `Application`s
     */
    vnc?: boolean,
    /**
     * Flag to enable/disable the log API
     */
    logs?: boolean,
    /**
     * Flag to enable/disable the interactive terminal API
     */
    terminal?: boolean,
    /**
     * Flag to enable/disable connection between peering `Job`s
     */
    peers?: boolean,
    /**
     * Flag to enable/disable extension of jobs
     */
    timeExtension?: boolean,
    /**
     * Flag to enable/disable the retrieveUtilization of jobs
     */
    utilization?: boolean,
}
export interface VirtualMachine {
    /**
     * Flag to enable/disable this feature
     *
     * All other flags are ignored if this is `false`.
     */
    enabled?: boolean,
    /**
     * Flag to enable/disable the log API
     */
    logs?: boolean,
    /**
     * Flag to enable/disable the VNC API
     */
    vnc?: boolean,
    /**
     * Flag to enable/disable the interactive terminal API
     */
    terminal?: boolean,
    /**
     * Flag to enable/disable extension of jobs
     */
    timeExtension?: boolean,
    /**
     * Flag to enable/disable suspension of jobs
     */
    suspension?: boolean,
    /**
     * Flag to enable/disable the retrieveUtilization of jobs
     */
    utilization?: boolean,
}
}
export namespace AppParameterValueNS {
/**
 * A reference to a UCloud file

 * - __Compatible with:__ `ApplicationParameter.InputFile` and `ApplicationParameter.InputDirectory`
 * - __Mountable as a resource:__ ✅ Yes
 * - __Expands to:__ The absolute path to the file or directory in the software's environment
 * - __Side effects:__ Includes the file or directory in the `Job`'s temporary work directory

 * The path of the file must be absolute and refers to either a UCloud directory or file.
 *
 */
export interface File {
    /**
     * The absolute path to the file or directory in UCloud
     */
    path: string,
    /**
     * Indicates if this file or directory should be mounted as read-only
     *
     * A provider must reject the request if it does not support read-only mounts when `readOnly = true`.
     *
     */
    readOnly: boolean,
    type: ("file"),
}
/**
 * A boolean value (true or false)

 * - __Compatible with:__ `ApplicationParameter.Bool`
 * - __Mountable as a resource:__ ❌ No
 * - __Expands to:__ `trueValue` of `ApplicationParameter.Bool` if value is `true` otherwise `falseValue`
 * - __Side effects:__ None
 *
 */
export interface Bool {
    value: boolean,
    type: ("boolean"),
}
/**
 * A textual value

 * - __Compatible with:__ `ApplicationParameter.Text` and `ApplicationParameter.Enumeration`
 * - __Mountable as a resource:__ ❌ No
 * - __Expands to:__ The text, when used in an invocation this will be passed as a single argument.
 * - __Side effects:__ None
 *
 * When this is used with an `Enumeration` it must match the value of one of the associated `options`.
 *
 */
export interface Text {
    value: string,
    type: ("text"),
}
/**
 * A textarea value

 * - __Compatible with:__ `ApplicationParameter.TextArea` and `ApplicationParameter.Enumeration`
 * - __Mountable as a resource:__ ❌ No
 * - __Expands to:__ The text, when used in an invocation this will be passed as a single argument.
 * - __Side effects:__ None
 *
 * When this is used with an `Enumeration` it must match the value of one of the associated `options`.
 *
 */
 export interface TextArea {
    value: string,
    type: ("textarea"),
}
/**
 * An integral value
 *
 * - __Compatible with:__ `ApplicationParameter.Integer`
 * - __Mountable as a resource:__ ❌ No
 * - __Expands to:__ The number
 * - __Side effects:__ None
 *
 * Internally this uses a big integer type and there are no defined limits.
 *
 */
export interface Integer {
    value: number /* int64 */,
    type: ("integer"),
}
/**
 * A floating point value

 * - __Compatible with:__ `ApplicationParameter.FloatingPoint`
 * - __Mountable as a resource:__ ❌ No
 * - __Expands to:__ The number
 * - __Side effects:__ None
 *
 * Internally this uses a big decimal type and there are no defined limits.
 *
 */
export interface FloatingPoint {
    value: number /* float64 */,
    type: ("floating_point"),
}
/**
 * A reference to a separate UCloud `Job`

 * - __Compatible with:__ `ApplicationParameter.Peer`
 * - __Mountable as a resource:__ ✅ Yes
 * - __Expands to:__ The `hostname`
 * - __Side effects:__ Configures the firewall to allow bidirectional communication between this `Job` and the peering
 *   `Job`
 *
 */
export interface Peer {
    hostname: string,
    jobId: string,
    type: ("peer"),
}
/**
 * A reference to a software license, registered locally at the provider

 * - __Compatible with:__ `ApplicationParameter.LicenseServer`
 * - __Mountable as a resource:__ ❌ No
 * - __Expands to:__ `${license.address}:${license.port}/${license.key}` or
 *   `${license.address}:${license.port}` if no key is provided
 * - __Side effects:__ None
 *
 */
export interface License {
    id: string,
    type: ("license_server"),
}
/**
 * A reference to block storage (Not yet implemented)
 */
export interface BlockStorage {
    id: string,
    type: ("block_storage"),
}
/**
 * A reference to block storage (Not yet implemented)
 */
export interface Network {
    id: string,
    type: ("network"),
}
/**
 * A reference to an HTTP ingress, registered locally at the provider

 * - __Compatible with:__ `ApplicationParameter.Ingress`
 * - __Mountable as a resource:__ ❌ No
 * - __Expands to:__ `${id}`
 * - __Side effects:__ Configures an HTTP ingress for the application's interactive web interface. This interface should
 *   not perform any validation, that is, the application should be publicly accessible.
 *
 */
export interface Ingress {
    id: string,
    type: ("ingress"),
}
}
export namespace tools {
export function findByName(
    request: FindByNameAndPagination
): APICallParameters<FindByNameAndPagination, Page<Tool>> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/api/hpc/tools" + "/byName", {appName: request.appName, itemsPerPage: request.itemsPerPage, page: request.page}),
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
        path: buildQueryString("/api/hpc/tools" + "/byNameAndVersion", {name: request.name, version: request.version}),
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
export function create(): APICallParameters<{}, any /* unknown */> {
    return {
        context: "",
        method: "PUT",
        path: "/api/hpc/tools",
        reloadId: Math.random(),
    };
}
export function uploadLogo(): APICallParameters<{}, any /* unknown */> {
    return {
        context: "",
        method: "POST",
        path: "/api/hpc/tools" + "/uploadLogo",
        reloadId: Math.random(),
    };
}
export function clearLogo(
    request: ClearLogoRequest
): APICallParameters<ClearLogoRequest, any /* unknown */> {
    return {
        context: "",
        method: "DELETE",
        path: "/api/hpc/tools" + "/clearLogo",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function fetchLogo(
    request: FetchLogoRequest
): APICallParameters<FetchLogoRequest, any /* unknown */> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/api/hpc/tools" + "/logo", {name: request.name}),
        parameters: request,
        reloadId: Math.random(),
    };
}
}
export namespace NetworkIPSpecificationNS {
export interface Firewall {
    openPorts: PortRangeAndProto[],
}
}
export namespace apps {
export function findByNameAndVersion(
    request: FindApplicationAndOptionalDependencies
): APICallParameters<FindApplicationAndOptionalDependencies, ApplicationWithFavoriteAndTags> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/api/hpc/apps" + "/byNameAndVersion", {appName: request.appName, appVersion: request.appVersion}),
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
        path: buildQueryString("/api/hpc/apps" + "/permission", {appName: request.appName, appVersion: request.appVersion, permission: request.permission}),
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
        path: buildQueryString("/api/hpc/apps" + "/list-acl", {appName: request.appName}),
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
        path: buildQueryString("/api/hpc/apps" + "/byName", {appName: request.appName, itemsPerPage: request.itemsPerPage, page: request.page}),
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
export function create(): APICallParameters<{}, any /* unknown */> {
    return {
        context: "",
        method: "PUT",
        path: "/api/hpc/apps",
        reloadId: Math.random(),
    };
}
export function findLatestByTool(
    request: FindLatestByToolRequest
): APICallParameters<FindLatestByToolRequest, Page<Application>> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/api/hpc/apps" + "/byTool", {tool: request.tool, itemsPerPage: request.itemsPerPage, page: request.page}),
        parameters: request,
        reloadId: Math.random(),
    };
}
export function uploadLogo(): APICallParameters<{}, any /* unknown */> {
    return {
        context: "",
        method: "POST",
        path: "/api/hpc/apps" + "/uploadLogo",
        reloadId: Math.random(),
    };
}
export function clearLogo(
    request: ClearLogoRequest
): APICallParameters<ClearLogoRequest, any /* unknown */> {
    return {
        context: "",
        method: "DELETE",
        path: "/api/hpc/apps" + "/clearLogo",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function fetchLogo(
    request: FetchLogoRequest
): APICallParameters<FetchLogoRequest, any /* unknown */> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/api/hpc/apps" + "/logo", {name: request.name}),
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
        path: buildQueryString("/api/hpc/apps" + "/searchTags", {query: request.query, excludeTools: request.excludeTools, itemsPerPage: request.itemsPerPage, page: request.page}),
        parameters: request,
        reloadId: Math.random(),
    };
}
export function listTags(
    request: ListTagsRequest
): APICallParameters<ListTagsRequest, string[]> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/api/hpc/apps" + "/listTags", {}),
        parameters: request,
        reloadId: Math.random(),
    }
}
export function appStoreSections(
    request: AppStoreSectionsRequest
): APICallParameters<AppStoreSectionsRequest, AppStoreSections> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/api/hpc/apps/store", {page: request.page}),
        parameters: request,
        reloadId: Math.random(),
    }
}
export function searchApps(
    request: AppSearchRequest
): APICallParameters<AppSearchRequest, Page<ApplicationSummaryWithFavorite>> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/api/hpc/apps" + "/search", {query: request.query, itemsPerPage: request.itemsPerPage, page: request.page}),
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
export function retrieveFavorites(
    request: PaginationRequest
): APICallParameters<PaginationRequest, Page<ApplicationSummaryWithFavorite>> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/api/hpc/apps" + "/favorites", {itemsPerPage: request.itemsPerPage, page: request.page}),
        parameters: request,
        reloadId: Math.random(),
    };
}
export function toggleFavorite(
    request: FavoriteRequest
): APICallParameters<FavoriteRequest, any /* unknown */> {
    return {
        context: "",
        method: "POST",
        path: "/api/hpc/apps" + "/favorites",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
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
        path: buildQueryString("/api/jobs/control" + "/retrieve", {id: request.id, includeParameters: request.includeParameters, includeUpdates: request.includeUpdates, includeApplication: request.includeApplication, includeProduct: request.includeProduct, includeSupport: request.includeSupport}),
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
export function submitFile(): APICallParameters<{}, any /* unknown */> {
    return {
        context: "",
        method: "POST",
        path: "/api/jobs/control" + "/submitFile",
        reloadId: Math.random(),
    };
}
}
export namespace ucloud {
export interface AauComputeRetrieveRequest {
    id: string,
}
export interface AauComputeSendUpdateRequest {
    id: string,
    update: string,
    newState?: ("IN_QUEUE" | "RUNNING" | "CANCELING" | "SUCCESS" | "FAILURE" | "EXPIRED" | "SUSPENDED"),
}
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
    port: number /* int32 */,
    tags: string[],
    license?: string,
    category: accounting.ProductCategoryId,
    pricePerUnit: number /* int64 */,
    description: string,
    hiddenInGrantApplications: boolean,
    availability: accounting.ProductAvailability,
    priority: number /* int32 */,
    paymentModel: ("FREE_BUT_REQUIRE_BALANCE" | "PER_ACTIVATION"),
}
/**
 * The base type for requesting paginated content.
 *
 * Paginated content can be requested with one of the following `consistency` guarantees, this greatly changes the
 * semantics of the call:
 *
 * | Consistency | Description |
 * |-------------|-------------|
 * | `PREFER` | Consistency is preferred but not required. An inconsistent snapshot might be returned. |
 * | `REQUIRE` | Consistency is required. A request will fail if consistency is no longer guaranteed. |
 *
 * The `consistency` refers to if collecting all the results via the pagination API are _consistent_. We consider the
 * results to be consistent if it contains a complete view at some point in time. In practice this means that the results
 * must contain all the items, in the correct order and without duplicates.
 *
 * If you use the `PREFER` consistency then you may receive in-complete results that might appear out-of-order and can
 * contain duplicate items. UCloud will still attempt to serve a snapshot which appears mostly consistent. This is helpful
 * for user-interfaces which do not strictly depend on consistency but would still prefer something which is mostly
 * consistent.
 *
 * The results might become inconsistent if the client either takes too long, or a service instance goes down while
 * fetching the results. UCloud attempts to keep each `next` token alive for at least one minute before invalidating it.
 * This does not mean that a client must collect all results within a minute but rather that they must fetch the next page
 * within a minute of the last page. If this is not feasible and consistency is not required then `PREFER` should be used.
 *
 * ---
 *
 * __📝 NOTE:__ Services are allowed to ignore extra criteria of the request if the `next` token is supplied. This is
 * needed in order to provide a consistent view of the results. Clients _should_ provide the same criterion as they
 * paginate through the results.
 *
 * ---
 *
 */
export interface KubernetesLicenseBrowseRequest {
    tag?: string,
    /**
     * Requested number of items per page. Supported values: 10, 25, 50, 100, 250.
     */
    itemsPerPage?: number /* int32 */,
    /**
     * A token requesting the next page of items
     */
    next?: string,
    /**
     * Controls the consistency guarantees provided by the backend
     */
    consistency?: ("PREFER" | "REQUIRE"),
    /**
     * Items to skip ahead
     */
    itemsToSkip?: number /* int64 */,
}
export interface K8Subnet {
    externalCidr: string,
    internalCidr: string,
}
/**
 * The base type for requesting paginated content.
 *
 * Paginated content can be requested with one of the following `consistency` guarantees, this greatly changes the
 * semantics of the call:
 *
 * | Consistency | Description |
 * |-------------|-------------|
 * | `PREFER` | Consistency is preferred but not required. An inconsistent snapshot might be returned. |
 * | `REQUIRE` | Consistency is required. A request will fail if consistency is no longer guaranteed. |
 *
 * The `consistency` refers to if collecting all the results via the pagination API are _consistent_. We consider the
 * results to be consistent if it contains a complete view at some point in time. In practice this means that the results
 * must contain all the items, in the correct order and without duplicates.
 *
 * If you use the `PREFER` consistency then you may receive in-complete results that might appear out-of-order and can
 * contain duplicate items. UCloud will still attempt to serve a snapshot which appears mostly consistent. This is helpful
 * for user-interfaces which do not strictly depend on consistency but would still prefer something which is mostly
 * consistent.
 *
 * The results might become inconsistent if the client either takes too long, or a service instance goes down while
 * fetching the results. UCloud attempts to keep each `next` token alive for at least one minute before invalidating it.
 * This does not mean that a client must collect all results within a minute but rather that they must fetch the next page
 * within a minute of the last page. If this is not feasible and consistency is not required then `PREFER` should be used.
 *
 * ---
 *
 * __📝 NOTE:__ Services are allowed to ignore extra criteria of the request if the `next` token is supplied. This is
 * needed in order to provide a consistent view of the results. Clients _should_ provide the same criterion as they
 * paginate through the results.
 *
 * ---
 *
 */
export interface KubernetesIPMaintenanceBrowseRequest {
    /**
     * Requested number of items per page. Supported values: 10, 25, 50, 100, 250.
     */
    itemsPerPage?: number /* int32 */,
    /**
     * A token requesting the next page of items
     */
    next?: string,
    /**
     * Controls the consistency guarantees provided by the backend
     */
    consistency?: ("PREFER" | "REQUIRE"),
    /**
     * Items to skip ahead
     */
    itemsToSkip?: number /* int64 */,
}
export interface K8NetworkStatus {
    capacity: number /* int64 */,
    used: number /* int64 */,
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
        path: buildQueryString("/ucloud/ucloud/licenses/maintenance" + "/browse", {tag: request.tag, itemsPerPage: request.itemsPerPage, next: request.next, consistency: request.consistency, itemsToSkip: request.itemsToSkip}),
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
export namespace networkip {
export function create(
    request: BulkRequest<NetworkIP>
): APICallParameters<BulkRequest<NetworkIP>, any /* unknown */> {
    return {
        context: "",
        method: "POST",
        path: "/ucloud/ucloud/networkips",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function remove(
    request: BulkRequest<NetworkIP>
): APICallParameters<BulkRequest<NetworkIP>, any /* unknown */> {
    return {
        context: "",
        method: "DELETE",
        path: "/ucloud/ucloud/networkips",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function verify(
    request: BulkRequest<NetworkIP>
): APICallParameters<BulkRequest<NetworkIP>, any /* unknown */> {
    return {
        context: "",
        method: "POST",
        path: "/ucloud/ucloud/networkips" + "/verify",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function updateFirewall(
    request: BulkRequest<FirewallAndId>
): APICallParameters<BulkRequest<FirewallAndId>, any /* unknown */> {
    return {
        context: "",
        method: "POST",
        path: "/ucloud/ucloud/networkips" + "/firewall",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export namespace maintenance {
export function create(
    request: BulkRequest<K8Subnet>
): APICallParameters<BulkRequest<K8Subnet>, any /* unknown */> {
    return {
        context: "",
        method: "POST",
        path: "/ucloud/ucloud/networkips/maintenance",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function browse(
    request: KubernetesIPMaintenanceBrowseRequest
): APICallParameters<KubernetesIPMaintenanceBrowseRequest, PageV2<K8Subnet>> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/ucloud/ucloud/networkips/maintenance" + "/browse", {itemsPerPage: request.itemsPerPage, next: request.next, consistency: request.consistency, itemsToSkip: request.itemsToSkip}),
        parameters: request,
        reloadId: Math.random(),
    };
}
export function retrieveStatus(): APICallParameters<{}, K8NetworkStatus> {
    return {
        context: "",
        method: "GET",
        path: "/ucloud/ucloud/networkips/maintenance" + "/retrieve",
        reloadId: Math.random(),
    };
}
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
        path: "/ucloud/ucloud/jobs",
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
        path: "/ucloud/ucloud/jobs",
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
    request: BulkRequest<JobsProviderExtendRequestItem>
): APICallParameters<BulkRequest<JobsProviderExtendRequestItem>, any /* unknown */> {
    return {
        context: "",
        method: "POST",
        path: "/ucloud/ucloud/jobs" + "/extend",
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
        path: "/ucloud/ucloud/jobs" + "/suspend",
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
        path: "/ucloud/ucloud/jobs" + "/verify",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function openInteractiveSession(
    request: BulkRequest<JobsProviderOpenInteractiveSessionRequestItem>
): APICallParameters<BulkRequest<JobsProviderOpenInteractiveSessionRequestItem>, JobsProviderOpenInteractiveSessionResponse> {
    return {
        context: "",
        method: "POST",
        path: "/ucloud/ucloud/jobs" + "/interactiveSession",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
/**
 * Retrieve products (retrieveProducts)
 *
 * ![API: Internal/Beta](https://img.shields.io/static/v1?label=API&message=Internal/Beta&color=red&style=flat-square)
 * ![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)
 *
 * An API for retrieving the products and the support from a provider.
 */
export function retrieveProducts(): APICallParameters<{}, JobsProviderRetrieveProductsResponse> {
    return {
        context: "",
        method: "GET",
        path: "/ucloud/ucloud/jobs" + "/retrieveProducts",
        reloadId: Math.random(),
    };
}
export function retrieveUtilization(): APICallParameters<{}, JobsProviderUtilizationResponse> {
    return {
        context: "",
        method: "GET",
        path: "/ucloud/ucloud/jobs" + "/retrieveUtilization",
        reloadId: Math.random(),
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
        path: buildQueryString("/api/licenses" + "/browse", {includeUpdates: request.includeUpdates, includeProduct: request.includeProduct, includeAcl: request.includeAcl, itemsPerPage: request.itemsPerPage, next: request.next, consistency: request.consistency, itemsToSkip: request.itemsToSkip, provider: request.provider, tag: request.tag}),
        parameters: request,
        reloadId: Math.random(),
    };
}
export function create(
    request: BulkRequest<LicenseSpecification>
): APICallParameters<BulkRequest<LicenseSpecification>, LicensesCreateResponse> {
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
        path: buildQueryString("/api/licenses" + "/retrieve", {id: request.id, includeUpdates: request.includeUpdates, includeProduct: request.includeProduct, includeAcl: request.includeAcl}),
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
        path: buildQueryString("/api/licenses/control" + "/retrieve", {id: request.id, includeUpdates: request.includeUpdates, includeProduct: request.includeProduct, includeAcl: request.includeAcl}),
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
export namespace networkips {
export function browse(
    request: NetworkIPsBrowseRequest
): APICallParameters<NetworkIPsBrowseRequest, PageV2<NetworkIP>> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/api/networkips" + "/browse", {includeUpdates: request.includeUpdates, includeProduct: request.includeProduct, includeAcl: request.includeAcl, itemsPerPage: request.itemsPerPage, next: request.next, consistency: request.consistency, itemsToSkip: request.itemsToSkip, provider: request.provider}),
        parameters: request,
        reloadId: Math.random(),
    };
}
export function create(
    request: BulkRequest<NetworkIPSpecification>
): APICallParameters<BulkRequest<NetworkIPSpecification>, NetworkIPsCreateResponse> {
    return {
        context: "",
        method: "POST",
        path: "/api/networkips",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function remove(
    request: BulkRequest<NetworkIPRetrieve>
): APICallParameters<BulkRequest<NetworkIPRetrieve>, any /* unknown */> {
    return {
        context: "",
        method: "DELETE",
        path: "/api/networkips",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function retrieve(
    request: NetworkIPRetrieveWithFlags
): APICallParameters<NetworkIPRetrieveWithFlags, NetworkIP> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/api/networkips" + "/retrieve", {id: request.id, includeUpdates: request.includeUpdates, includeProduct: request.includeProduct, includeAcl: request.includeAcl}),
        parameters: request,
        reloadId: Math.random(),
    };
}
export function updateAcl(
    request: BulkRequest<NetworkIPsUpdateAclRequestItem>
): APICallParameters<BulkRequest<NetworkIPsUpdateAclRequestItem>, any /* unknown */> {
    return {
        context: "",
        method: "POST",
        path: "/api/networkips" + "/acl",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function updateFirewall(
    request: BulkRequest<FirewallAndId>
): APICallParameters<BulkRequest<FirewallAndId>, any /* unknown */> {
    return {
        context: "",
        method: "POST",
        path: "/api/networkips" + "/firewall",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export namespace control {
export function update(
    request: BulkRequest<NetworkIPControlUpdateRequestItem>
): APICallParameters<BulkRequest<NetworkIPControlUpdateRequestItem>, any /* unknown */> {
    return {
        context: "",
        method: "POST",
        path: "/api/networkips/control" + "/update",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function retrieve(
    request: NetworkIPRetrieveWithFlags
): APICallParameters<NetworkIPRetrieveWithFlags, NetworkIP> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/api/networkips/control" + "/retrieve", {id: request.id, includeUpdates: request.includeUpdates, includeProduct: request.includeProduct, includeAcl: request.includeAcl}),
        parameters: request,
        reloadId: Math.random(),
    };
}
export function chargeCredits(
    request: BulkRequest<NetworkIPControlChargeCreditsRequestItem>
): APICallParameters<BulkRequest<NetworkIPControlChargeCreditsRequestItem>, NetworkIPControlChargeCreditsResponse> {
    return {
        context: "",
        method: "POST",
        path: "/api/networkips/control" + "/chargeCredits",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
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
    request: BulkRequest<JobSpecification>
): APICallParameters<BulkRequest<JobSpecification>, JobsCreateResponse> {
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
        path: buildQueryString("/api/jobs" + "/retrieve", {id: request.id, includeParameters: request.includeParameters, includeUpdates: request.includeUpdates, includeApplication: request.includeApplication, includeProduct: request.includeProduct, includeSupport: request.includeSupport}),
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
export function retrieveUtilization(
    request: JobsRetrieveUtilizationRequest
): APICallParameters<JobsRetrieveUtilizationRequest, JobsRetrieveUtilizationResponse> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/api/jobs" + "/retrieveUtilization", {jobId: request.jobId}),
        parameters: request,
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
        path: buildQueryString("/api/jobs" + "/browse", {itemsPerPage: request.itemsPerPage, next: request.next, consistency: request.consistency, itemsToSkip: request.itemsToSkip, includeParameters: request.includeParameters, includeUpdates: request.includeUpdates, includeApplication: request.includeApplication, includeProduct: request.includeProduct, includeSupport: request.includeSupport, sortBy: request.sortBy, filterApplication: request.filterApplication, filterLaunchedBy: request.filterLaunchedBy, filterState: request.filterState, filterTitle: request.filterTitle, filterBefore: request.filterBefore, filterAfter: request.filterAfter}),
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
/**
 * Retrieve products (retrieveProducts)
 *
 * ![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
 * ![Auth: Users](https://img.shields.io/static/v1?label=Auth&message=Users&color=informational&style=flat-square)
 *
 * A temporary API for retrieving the products and the support from a provider.
 */
export function retrieveProducts(
    request: JobsRetrieveProductsRequest
): APICallParameters<JobsRetrieveProductsRequest, JobsRetrieveProductsResponse> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/api/jobs" + "/retrieveProducts", {providers: request.providers}),
        parameters: request,
        reloadId: Math.random(),
    };
}
}
export namespace OpenSessionNS {
export interface Shell {
    jobId: string,
    rank: number /* int32 */,
    sessionIdentifier: string,
    type: ("shell"),
}
export interface Web {
    jobId: string,
    rank: number /* int32 */,
    redirectClientTo: string,
    type: ("web"),
}
export interface Vnc {
    jobId: string,
    rank: number /* int32 */,
    url: string,
    password?: string,
    type: ("vnc"),
}
}
export namespace aau {

export namespace maintenance {
export function retrieve(
    request: ucloud.AauComputeRetrieveRequest
): APICallParameters<ucloud.AauComputeRetrieveRequest, Job> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/ucloud/aau/compute/jobs/maintenance" + "/retrieve", {id: request.id}),
        parameters: request,
        reloadId: Math.random(),
    };
}
export function sendUpdate(
    request: BulkRequest<ucloud.AauComputeSendUpdateRequest>
): APICallParameters<BulkRequest<ucloud.AauComputeSendUpdateRequest>, any /* unknown */> {
    return {
        context: "",
        method: "POST",
        path: "/ucloud/aau/compute/jobs/maintenance" + "/update",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
}
}
export namespace ApplicationParameterNS {
export interface InputFile {
    name: string,
    optional: boolean,
    defaultValue?: kotlinx.serialization.json.JsonElement,
    title: string,
    description: string,
    type: ("input_file"),
}
export interface InputDirectory {
    name: string,
    optional: boolean,
    defaultValue?: kotlinx.serialization.json.JsonElement,
    title: string,
    description: string,
    type: ("input_directory"),
}
export interface Text {
    name: string,
    optional: boolean,
    defaultValue?: kotlinx.serialization.json.JsonElement,
    title: string,
    description: string,
    type: ("text"),
}
export interface TextArea {
    name: string,
    optional: boolean,
    defaultValue?: kotlinx.serialization.json.JsonElement,
    title: string,
    description: string,
    type: ("textarea"),
}
export interface Integer {
    name: string,
    optional: boolean,
    defaultValue?: kotlinx.serialization.json.JsonElement,
    title: string,
    description: string,
    min?: number /* int64 */,
    max?: number /* int64 */,
    step?: number /* int64 */,
    unitName?: string,
    type: ("integer"),
}
export interface FloatingPoint {
    name: string,
    optional: boolean,
    defaultValue?: kotlinx.serialization.json.JsonElement,
    title: string,
    description: string,
    min?: number /* float64 */,
    max?: number /* float64 */,
    step?: number /* float64 */,
    unitName?: string,
    type: ("floating_point"),
}
export interface Bool {
    name: string,
    optional: boolean,
    defaultValue?: kotlinx.serialization.json.JsonElement,
    title: string,
    description: string,
    trueValue: string,
    falseValue: string,
    type: ("boolean"),
}
export interface Enumeration {
    name: string,
    optional: boolean,
    defaultValue?: kotlinx.serialization.json.JsonElement,
    title: string,
    description: string,
    options: EnumOption[],
    type: ("enumeration"),
}
export interface EnumOption {
    name: string,
    value: string,
}
export interface Peer {
    name: string,
    title: string,
    description: string,
    suggestedApplication?: string,
    defaultValue?: kotlinx.serialization.json.JsonElement,
    optional: boolean,
    type: ("peer"),
}
export interface Ingress {
    name: string,
    title: string,
    description: string,
    defaultValue?: kotlinx.serialization.json.JsonElement,
    optional: boolean,
    type: ("ingress"),
}
export interface LicenseServer {
    name: string,
    title: string,
    optional: boolean,
    description: string,
    tagged: string[],
    defaultValue?: kotlinx.serialization.json.JsonElement,
    type: ("license_server"),
}
export interface NetworkIP {
    name: string,
    title: string,
    description: string,
    defaultValue?: kotlinx.serialization.json.JsonElement,
    optional: boolean,
    type: ("network_ip"),
}
}
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
export function retrieveEmailSettings(
    request: RetrieveEmailSettingsRequest
): APICallParameters<RetrieveEmailSettingsRequest, RetrieveEmailSettingsResponse> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/api/mail" + "/retrieveEmailSettings", {username: request.username}),
        parameters: request,
        reloadId: Math.random(),
    };
}
export function toggleEmailSettings(
    request: BulkRequest<EmailSettingsItem>
): APICallParameters<BulkRequest<EmailSettingsItem>, any /* unknown */> {
    return {
        context: "",
        method: "POST",
        path: "/api/mail" + "/toggleEmailSettings",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export interface SendRequest {
    receiver: string,
    mail: Mail,
    mandatory: boolean,
}
export type Mail = MailNS.TransferApplicationMail | MailNS.LowFundsMail | MailNS.StillLowFundsMail | MailNS.UserRoleChangeMail | MailNS.UserLeftMail | MailNS.UserRemovedMail | MailNS.UserRemovedMailToUser | MailNS.ProjectInviteMail | MailNS.NewGrantApplicationMail | MailNS.GrantApplicationUpdatedMail | MailNS.GrantApplicationUpdatedMailToAdmins | MailNS.GrantApplicationStatusChangedToAdmin | MailNS.GrantApplicationApproveMail | MailNS.GrantApplicationApproveMailToAdmins | MailNS.GrantApplicationRejectedMail | MailNS.GrantApplicationWithdrawnMail | MailNS.NewCommentOnApplicationMail | MailNS.ResetPasswordMail | MailNS.VerificationReminderMail
export interface SendSupportEmailRequest {
    fromEmail: string,
    subject: string,
    message: string,
}
export interface SendBulkRequest {
    messages: SendRequest[],
}
export interface RetrieveEmailSettingsResponse {
    settings: EmailSettings,
}
export interface EmailSettings {
    newGrantApplication: boolean,
    grantApplicationUpdated: boolean,
    grantApplicationApproved: boolean,
    grantApplicationRejected: boolean,
    grantApplicationWithdrawn: boolean,
    newCommentOnApplication: boolean,
    applicationTransfer: boolean,
    applicationStatusChange: boolean,
    projectUserInvite: boolean,
    projectUserRemoved: boolean,
    verificationReminder: boolean,
    userRoleChange: boolean,
    userLeft: boolean,
    lowFunds: boolean,
}
export interface RetrieveEmailSettingsRequest {
    username?: string,
}
export interface EmailSettingsItem {
    username?: string,
    settings: EmailSettings,
}
export namespace MailNS {
export interface TransferApplicationMail {
    senderProject: string,
    receiverProject: string,
    applicationProjectTitle: string,
    subject: string,
    type: ("transferApplication"),
}
export interface LowFundsMail {
    category: string,
    provider: string,
    projectTitle: string,
    subject: string,
    type: ("lowFunds"),
}
export interface StillLowFundsMail {
    category: string,
    provider: string,
    projectTitle: string,
    subject: string,
    type: ("stillLowFunds"),
}
export interface UserRoleChangeMail {
    subjectToChange: string,
    roleChange: string,
    projectTitle: string,
    subject: string,
    type: ("userRoleChange"),
}
export interface UserLeftMail {
    leavingUser: string,
    projectTitle: string,
    subject: string,
    type: ("userLeft"),
}
export interface UserRemovedMail {
    leavingUser: string,
    projectTitle: string,
    subject: string,
    type: ("userRemoved"),
}
export interface UserRemovedMailToUser {
    projectTitle: string,
    subject: string,
    type: ("userRemovedToUser"),
}
export interface ProjectInviteMail {
    projectTitle: string,
    subject: string,
    type: ("invitedToProject"),
}
export interface NewGrantApplicationMail {
    sender: string,
    projectTitle: string,
    subject: string,
    type: ("newGrantApplication"),
}
export interface GrantApplicationUpdatedMail {
    projectTitle: string,
    sender: string,
    subject: string,
    type: ("applicationUpdated"),
}
export interface GrantApplicationUpdatedMailToAdmins {
    projectTitle: string,
    sender: string,
    receivingProjectTitle: string,
    subject: string,
    type: ("applicationUpdatedToAdmins"),
}
export interface GrantApplicationStatusChangedToAdmin {
    status: string,
    projectTitle: string,
    sender: string,
    receivingProjectTitle: string,
    subject: string,
    type: ("applicationStatusChangedToAdmins"),
}
export interface GrantApplicationApproveMail {
    projectTitle: string,
    subject: string,
    type: ("applicationApproved"),
}
export interface GrantApplicationApproveMailToAdmins {
    sender: string,
    projectTitle: string,
    subject: string,
    type: ("applicationApprovedToAdmins"),
}
export interface GrantApplicationRejectedMail {
    projectTitle: string,
    subject: string,
    type: ("applicationRejected"),
}
export interface GrantApplicationWithdrawnMail {
    projectTitle: string,
    sender: string,
    subject: string,
    type: ("applicationWithdrawn"),
}
export interface NewCommentOnApplicationMail {
    sender: string,
    projectTitle: string,
    receivingProjectTitle: string,
    subject: string,
    type: ("newComment"),
}
export interface ResetPasswordMail {
    token: string,
    subject: string,
    type: ("resetPassword"),
}
export interface VerificationReminderMail {
    projectTitle: string,
    role: string,
    subject: string,
    type: ("verificationReminder"),
}
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
        path: buildQueryString("/auth" + "/claim", {jti: request.jti}),
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
    expiresIn: number /* int64 */,
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
    role?: ("GUEST" | "USER" | "ADMIN" | "SERVICE" | "THIRD_PARTY_APP" | "PROVIDER" | "UNKNOWN"),
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
export type Principal = Person | ServicePrincipal | ProviderPrincipal
export type Person = PersonNS.ByWAYF | PersonNS.ByPassword
export interface ServicePrincipal {
    id: string,
    role: ("GUEST" | "USER" | "ADMIN" | "SERVICE" | "THIRD_PARTY_APP" | "PROVIDER" | "UNKNOWN"),
    type: ("service"),
}
export interface ProviderPrincipal {
    id: string,
    role: ("GUEST" | "USER" | "ADMIN" | "SERVICE" | "THIRD_PARTY_APP" | "PROVIDER" | "UNKNOWN"),
    type: ("provider"),
}
export interface GetPrincipalRequest {
    username: string,
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
    role: ("GUEST" | "USER" | "ADMIN" | "SERVICE" | "THIRD_PARTY_APP" | "PROVIDER" | "UNKNOWN"),
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
    lastName: string,
}
export interface LookupUserWithEmailRequest {
    email: string,
}

export interface Create2FACredentialsResponse {
    otpAuthUri: string,
    qrCodeB64Data: string,
    secret: string,
    challengeId: string,
}
export interface AnswerChallengeRequest {
    challengeId: string,
    verificationCode: number /* int32 */,
}
export interface TwoFactorStatusResponse {
    connected: boolean,
}
export interface Session {
    ipAddress: string,
    userAgent: string,
    createdAt: number /* int64 */,
}
export interface ListUserSessionsRequest {
    itemsPerPage?: number /* int32 */,
    page?: number /* int32 */,
}
export interface ServiceAgreementText {
    version: number /* int32 */,
    text: string,
}
export interface AcceptSLARequest {
    version: number /* int32 */,
}
export interface PublicKeyAndRefreshToken {
    providerId: string,
    publicKey: string,
    refreshToken: string,
}
export interface AuthProvidersRegisterResponseItem {
    claimToken: string,
}
export interface RefreshToken {
    refreshToken: string,
}
export interface AuthProvidersRefreshAsProviderRequestItem {
    providerId: string,
}
export interface AuthProvidersRegisterRequestItem {
    id: string,
}
export interface AuthProvidersRetrievePublicKeyResponse {
    publicKey: string,
}
export interface AuthProvidersGenerateKeyPairResponse {
    publicKey: string,
    privateKey: string,
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
export function retrievePrincipal(
    request: GetPrincipalRequest
): APICallParameters<GetPrincipalRequest, Principal> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/auth/users" + "/retrievePrincipal", {username: request.username}),
        parameters: request,
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
export namespace providers {
export function claim(
    request: BulkRequest<AuthProvidersRegisterResponseItem>
): APICallParameters<BulkRequest<AuthProvidersRegisterResponseItem>, BulkResponse<PublicKeyAndRefreshToken>> {
    return {
        context: "",
        method: "POST",
        path: "/auth/providers" + "/claim",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function refresh(
    request: BulkRequest<RefreshToken>
): APICallParameters<BulkRequest<RefreshToken>, BulkResponse<AccessToken>> {
    return {
        context: "",
        method: "POST",
        path: "/auth/providers" + "/refresh",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
/**
 * Signs an access-token to be used by a UCloud service (refreshAsOrchestrator)
 *
 * ![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
 * ![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)
 *
 * This RPC signs an access-token which will be used by authorized UCloud services to act as an
 * orchestrator of resources.
 */
export function refreshAsOrchestrator(
    request: BulkRequest<AuthProvidersRefreshAsProviderRequestItem>
): APICallParameters<BulkRequest<AuthProvidersRefreshAsProviderRequestItem>, BulkResponse<AccessToken>> {
    return {
        context: "",
        method: "POST",
        path: "/auth/providers" + "/refreshAsOrchestrator",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function register(
    request: BulkRequest<AuthProvidersRegisterRequestItem>
): APICallParameters<BulkRequest<AuthProvidersRegisterRequestItem>, BulkResponse<AuthProvidersRegisterResponseItem>> {
    return {
        context: "",
        method: "POST",
        path: "/auth/providers",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function renew(
    request: BulkRequest<FindByStringId>
): APICallParameters<BulkRequest<FindByStringId>, BulkResponse<PublicKeyAndRefreshToken>> {
    return {
        context: "",
        method: "POST",
        path: "/auth/providers" + "/renew",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function retrievePublicKey(
    request: FindByStringId
): APICallParameters<FindByStringId, AuthProvidersRetrievePublicKeyResponse> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/auth/providers" + "/retrieveKey", {id: request.id}),
        parameters: request,
        reloadId: Math.random(),
    };
}
/**
 * Generates an RSA key pair useful for JWT signatures (generateKeyPair)
 *
 * ![API: Experimental/Alpha](https://img.shields.io/static/v1?label=API&message=Experimental/Alpha&color=orange&style=flat-square)
 * ![Auth: Services](https://img.shields.io/static/v1?label=Auth&message=Services&color=informational&style=flat-square)
 *
 * Generates an RSA key pair and returns it to the client. The key pair is not stored or registered in any
 * way by the authentication service.
 */
export function generateKeyPair(): APICallParameters<{}, AuthProvidersGenerateKeyPairResponse> {
    return {
        context: "",
        method: "POST",
        path: "/auth/providers" + "/generateKeyPair",
        reloadId: Math.random(),
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
    role: ("GUEST" | "USER" | "ADMIN" | "SERVICE" | "THIRD_PARTY_APP" | "PROVIDER" | "UNKNOWN"),
    title?: string,
    firstNames: string,
    lastName: string,
    phoneNumber?: string,
    orcId?: string,
    email?: string,
    serviceLicenseAgreement: number /* int32 */,
    organizationId: string,
    wayfId: string,
    displayName: string,
    twoFactorAuthentication: boolean,
    type: ("wayf"),
}
export interface ByPassword {
    id: string,
    role: ("GUEST" | "USER" | "ADMIN" | "SERVICE" | "THIRD_PARTY_APP" | "PROVIDER" | "UNKNOWN"),
    title?: string,
    firstNames: string,
    lastName: string,
    phoneNumber?: string,
    orcId?: string,
    email?: string,
    twoFactorAuthentication: boolean,
    serviceLicenseAgreement: number /* int32 */,
    displayName: string,
    type: ("password"),
}
}
}
export namespace provider {
/**
 * A `Resource` is the core data model used to synchronize tasks between UCloud and a [provider](/backend/provider-service/README.md).
 *
 * `Resource`s provide instructions to providers on how they should complete a given task. Examples of a `Resource`
 * include: [Compute jobs](/backend/app-orchestrator-service/README.md), HTTP ingress points and license servers. For
 * example, a (compute) `Job` provides instructions to the provider on how to start a software computation. It also gives
 * the provider APIs for communicating the status of the `Job`.
 *
 * All `Resource` share a common interface and data model. The data model contains a specification of the `Resource`, along
 * with metadata, such as: ownership, billing and status.
 *
 * `Resource`s are created in UCloud when a user requests it. This request is verified by UCloud and forwarded to the
 *  It is then up to the provider to implement the functionality of the `Resource`.
 *
 * ![](/backend/provider-service/wiki/resource_create.svg)
 *
 * __Figure:__ UCloud orchestrates with the provider to create a `Resource`
 *
 */
export interface ResourceDoc {
    /**
     * A unique identifier referencing the `Resource`
     *
     * The ID is unique across a provider for a single resource type.
     */
    id: string,
    /**
     * Timestamp referencing when the request for creation was received by UCloud
     */
    createdAt: number /* int64 */,
    /**
     * Holds the current status of the `Resource`
     */
    status: ResourceStatus,
    /**
     * Contains a list of updates from the provider as well as UCloud
     *
     * Updates provide a way for both UCloud, and the provider to communicate to the user what is happening with their
     * resource.
     */
    updates: ResourceUpdate[],
    specification: ResourceSpecification,
    /**
     * Contains information related to billing information for this `Resource`
     * @deprecated
     */
    billing: ResourceBilling,
    /**
     * Contains information about the original creator of the `Resource` along with project association
     */
    owner: ResourceOwner,
    /**
     * An ACL for this `Resource`
     * @deprecated
     */
    acl?: ResourceAclEntry[],
    /**
     * Permissions assigned to this resource
     *
     * A null value indicates that permissions are not supported by this resource type.
     */
    permissions?: ResourcePermissions,
    providerGeneratedId?: string,
}
/**
 * Describes the current state of the `Resource`
 *
 * The contents of this field depends almost entirely on the specific `Resource` that this field is managing. Typically,
 * this will contain information such as:
 *
 * - A state value. For example, a compute `Job` might be `RUNNING`
 * - Key metrics about the resource.
 * - Related resources. For example, certain `Resource`s are bound to another `Resource` in a mutually exclusive way, this
 *   should be listed in the `status` section.
 *
 */
export interface ResourceStatus {
    resolvedSupport?: accounting.providers.ResolvedSupport<any /* unknown */, any /* unknown */>,
    resolvedProduct?: accounting.Product;
}
/**
 * Describes an update to the `Resource`
 *
 * Updates can optionally be fetched for a `Resource`. The updates describe how the `Resource` changes state over time.
 * The current state of a `Resource` can typically be read from its `status` field. Thus, it is typically not needed to
 * use the full update history if you only wish to know the _current_ state of a `Resource`.
 *
 * An update will typically contain information similar to the `status` field, for example:
 *
 * - A state value. For example, a compute `Job` might be `RUNNING`.
 * - Change in key metrics.
 * - Bindings to related `Resource`s.
 *
 */
export interface ResourceUpdate {
    /**
     * A generic text message describing the current status of the `Resource`
     */
    status?: string,
    /**
     * A timestamp referencing when UCloud received this update
     */
    timestamp: number /* int64 */,
}
export interface ResourceSpecification {
    /**
     * A reference to the product which backs this `Resource`
     */
    product: accounting.ProductReference,
}
/**
 * Contains information related to the accounting/billing of a `Resource`
 *
 * Note that this object contains the price of the `Product`. This price may differ, over-time, from the actual price of
 * the `Product`. This allows providers to provide a gradual change of price for products. By allowing existing `Resource`s
 * to be charged a different price than newly launched products.
 */
export interface ResourceBilling {
    /**
     * Amount of credits charged in total for this `Resource`
     */
    creditsCharged: number /* int64 */,
    /**
     * The price per unit. This can differ from current price of `Product`
     */
    pricePerUnit: number /* int64 */,
}
/**
 * The owner of a `Resource`
 */
export interface ResourceOwner {
    createdBy: string,
    project?: string,
}
export interface ResourceAclEntry<Permission = unknown> {
    entity: AclEntity,
    permissions: Permission[],
}
export type AclEntity = AclEntityNS.ProjectGroup | AclEntityNS.User
export interface ResourcePermissions {
    /**
     * The permissions that the requesting user has access to
     */
    myself: Permission[],
    /**
     * The permissions that other users might have access to
     *
     * This value typically needs to be included through the `includeFullPermissions` flag
     */
    others?: ResourceAclEntry<Permission>[],
}
/**
 * Base type for all permissions of the UCloud authorization model

 * This type covers the permission part of UCloud's RBAC based authorization model. UCloud defines a set of standard
 * permissions that can be applied to a resource and its associated operations.
 *
 * 1. `READ`: Grants an entity access to all read-based operations. Read-based operations must not alter the state of a
 * resource. Typical examples include the `browse` and `retrieve*` endpoints.
 * 2. `EDIT`: Grants an entity access to all write-based operations. Write-based operations are allowed to alter the state
 * of a resource. This permission is required for most `update*` endpoints.
 * 3. `ADMIN`: Grants an entity access to special privileged operations. This permission will allow the entity to perform
 * any action on the resource, unless the operation specifies otherwise. This operation is, for example, used for updating
 * the permissions attached to a resource.
 *
 * Apart from the standard permissions, a resource may define additional permissions. These are documented along with
 * the resource and related operations.
 *
 */
export type Permission = PermissionNS.Read | PermissionNS.Edit | PermissionNS.Admin | PermissionNS.Provider
export interface IntegrationControlApproveConnectionRequest {
    username: string,
}
export interface IntegrationBrowseResponseItem {
    provider: string;
    connected: boolean;
    providerTitle: string;
    requiresMessageSigning?: boolean;
}
/**
 * The base type for requesting paginated content.
 *
 * Paginated content can be requested with one of the following `consistency` guarantees, this greatly changes the
 * semantics of the call:
 *
 * | Consistency | Description |
 * |-------------|-------------|
 * | `PREFER` | Consistency is preferred but not required. An inconsistent snapshot might be returned. |
 * | `REQUIRE` | Consistency is required. A request will fail if consistency is no longer guaranteed. |
 *
 * The `consistency` refers to if collecting all the results via the pagination API are _consistent_. We consider the
 * results to be consistent if it contains a complete view at some point in time. In practice this means that the results
 * must contain all the items, in the correct order and without duplicates.
 *
 * If you use the `PREFER` consistency then you may receive in-complete results that might appear out-of-order and can
 * contain duplicate items. UCloud will still attempt to serve a snapshot which appears mostly consistent. This is helpful
 * for user-interfaces which do not strictly depend on consistency but would still prefer something which is mostly
 * consistent.
 *
 * The results might become inconsistent if the client either takes too long, or a service instance goes down while
 * fetching the results. UCloud attempts to keep each `next` token alive for at least one minute before invalidating it.
 * This does not mean that a client must collect all results within a minute but rather that they must fetch the next page
 * within a minute of the last page. If this is not feasible and consistency is not required then `PREFER` should be used.
 *
 * ---
 *
 * __📝 NOTE:__ Services are allowed to ignore extra criteria of the request if the `next` token is supplied. This is
 * needed in order to provide a consistent view of the results. Clients _should_ provide the same criterion as they
 * paginate through the results.
 *
 * ---
 *
 */
export interface IntegrationBrowseRequest {
    /**
     * Requested number of items per page. Supported values: 10, 25, 50, 100, 250.
     */
    itemsPerPage?: number /* int32 */,
    /**
     * A token requesting the next page of items
     */
    next?: string,
    /**
     * Controls the consistency guarantees provided by the backend
     */
    consistency?: ("PREFER" | "REQUIRE"),
    /**
     * Items to skip ahead
     */
    itemsToSkip?: number /* int64 */,
}
export interface IntegrationClearConnectionRequest {
    username: string,
    provider: string,
}
export interface IntegrationConnectResponse {
    redirectTo: string,
}
export interface IntegrationConnectRequest {
    provider: string,
}
export interface ProviderSpecification {
    id: string,
    domain: string,
    https: boolean,
    port?: number /* int32 */,
    product: accounting.ProductReference,
}
export interface ProvidersUpdateAclRequestItem {
    id: string,
    acl: ResourceAclEntry<("EDIT")>[],
}
export interface ProvidersRenewRefreshTokenRequestItem {
    id: string,
}
/**
 * A `Resource` is the core data model used to synchronize tasks between UCloud and a [provider](/backend/provider-service/README.md).
 *
 * `Resource`s provide instructions to providers on how they should complete a given task. Examples of a `Resource`
 * include: [Compute jobs](/backend/app-orchestrator-service/README.md), HTTP ingress points and license servers. For
 * example, a (compute) `Job` provides instructions to the provider on how to start a software computation. It also gives
 * the provider APIs for communicating the status of the `Job`.
 *
 * All `Resource` share a common interface and data model. The data model contains a specification of the `Resource`, along
 * with metadata, such as: ownership, billing and status.
 *
 * `Resource`s are created in UCloud when a user requests it. This request is verified by UCloud and forwarded to the
 *  It is then up to the provider to implement the functionality of the `Resource`.
 *
 * ![](/backend/provider-service/wiki/resource_create.svg)
 *
 * __Figure:__ UCloud orchestrates with the provider to create a `Resource`
 *
 */
export interface Provider {
    /**
     * A unique identifier referencing the `Resource`
     *
     * The ID is unique across a provider for a single resource type.
     */
    id: string,
    specification: ProviderSpecification,
    refreshToken: string,
    publicKey: string,
    /**
     * Timestamp referencing when the request for creation was received by UCloud
     */
    createdAt: number /* int64 */,
    /**
     * Holds the current status of the `Resource`
     */
    status: ProviderStatus,
    /**
     * Contains a list of updates from the provider as well as UCloud
     *
     * Updates provide a way for both UCloud, and the provider to communicate to the user what is happening with their
     * resource.
     */
    updates: ProviderUpdate[],
    /**
     * Contains information related to billing information for this `Resource`
     * @deprecated
     */
    billing: ProviderBilling,
    /**
     * Contains information about the original creator of the `Resource` along with project association
     */
    owner: ResourceOwner,
    /**
     * An ACL for this `Resource`
     * @deprecated
     */
    acl: ResourceAclEntry<("EDIT")>[],
    /**
     * Permissions assigned to this resource
     *
     * A null value indicates that permissions are not supported by this resource type.
     */
    permissions?: ResourcePermissions,
    providerGeneratedId?: string,
}
/**
 * Describes the current state of the `Resource`
 *
 * The contents of this field depends almost entirely on the specific `Resource` that this field is managing. Typically,
 * this will contain information such as:
 *
 * - A state value. For example, a compute `Job` might be `RUNNING`
 * - Key metrics about the resource.
 * - Related resources. For example, certain `Resource`s are bound to another `Resource` in a mutually exclusive way, this
 *   should be listed in the `status` section.
 *
 */
export interface ProviderStatus {
    support?: accounting.providers.ResolvedSupport<any /* unknown */, any /* unknown */>,
}
/**
 * Describes an update to the `Resource`
 *
 * Updates can optionally be fetched for a `Resource`. The updates describe how the `Resource` changes state over time.
 * The current state of a `Resource` can typically be read from its `status` field. Thus, it is typically not needed to
 * use the full update history if you only wish to know the _current_ state of a `Resource`.
 *
 * An update will typically contain information similar to the `status` field, for example:
 *
 * - A state value. For example, a compute `Job` might be `RUNNING`.
 * - Change in key metrics.
 * - Bindings to related `Resource`s.
 *
 */
export interface ProviderUpdate {
    /**
     * A timestamp referencing when UCloud received this update
     */
    timestamp: number /* int64 */,
    /**
     * A generic text message describing the current status of the `Resource`
     */
    status?: string,
}
/**
 * Contains information related to the accounting/billing of a `Resource`
 *
 * Note that this object contains the price of the `Product`. This price may differ, over-time, from the actual price of
 * the `Product`. This allows providers to provide a gradual change of price for products. By allowing existing `Resource`s
 * to be charged a different price than newly launched products.
 */
export interface ProviderBilling {
    /**
     * The price per unit. This can differ from current price of `Product`
     */
    pricePerUnit: number /* int64 */,
    /**
     * Amount of credits charged in total for this `Resource`
     */
    creditsCharged: number /* int64 */,
}
/**
 * The base type for requesting paginated content.
 *
 * Paginated content can be requested with one of the following `consistency` guarantees, this greatly changes the
 * semantics of the call:
 *
 * | Consistency | Description |
 * |-------------|-------------|
 * | `PREFER` | Consistency is preferred but not required. An inconsistent snapshot might be returned. |
 * | `REQUIRE` | Consistency is required. A request will fail if consistency is no longer guaranteed. |
 *
 * The `consistency` refers to if collecting all the results via the pagination API are _consistent_. We consider the
 * results to be consistent if it contains a complete view at some point in time. In practice this means that the results
 * must contain all the items, in the correct order and without duplicates.
 *
 * If you use the `PREFER` consistency then you may receive in-complete results that might appear out-of-order and can
 * contain duplicate items. UCloud will still attempt to serve a snapshot which appears mostly consistent. This is helpful
 * for user-interfaces which do not strictly depend on consistency but would still prefer something which is mostly
 * consistent.
 *
 * The results might become inconsistent if the client either takes too long, or a service instance goes down while
 * fetching the results. UCloud attempts to keep each `next` token alive for at least one minute before invalidating it.
 * This does not mean that a client must collect all results within a minute but rather that they must fetch the next page
 * within a minute of the last page. If this is not feasible and consistency is not required then `PREFER` should be used.
 *
 * ---
 *
 * __📝 NOTE:__ Services are allowed to ignore extra criteria of the request if the `next` token is supplied. This is
 * needed in order to provide a consistent view of the results. Clients _should_ provide the same criterion as they
 * paginate through the results.
 *
 * ---
 *
 */
export interface ProvidersBrowseRequest {
    /**
     * Requested number of items per page. Supported values: 10, 25, 50, 100, 250.
     */
    itemsPerPage?: number /* int32 */,
    /**
     * A token requesting the next page of items
     */
    next?: string,
    /**
     * Controls the consistency guarantees provided by the backend
     */
    consistency?: ("PREFER" | "REQUIRE"),
    /**
     * Items to skip ahead
     */
    itemsToSkip?: number /* int64 */,
}
export type ProvidersRequestApprovalResponse = ProvidersRequestApprovalResponseNS.RequiresSignature | ProvidersRequestApprovalResponseNS.AwaitingAdministratorApproval
export type ProvidersRequestApprovalRequest = ProvidersRequestApprovalRequestNS.Information | ProvidersRequestApprovalRequestNS.Sign
export interface ProvidersApproveRequest {
    token: string,
}
export namespace resources {
export function create(
    request: BulkRequest<ResourceDoc>
): APICallParameters<BulkRequest<ResourceDoc>, any /* unknown */> {
    return {
        context: "",
        method: "POST",
        path: "/doc/resources",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function browse(
    request: PaginationRequestV2
): APICallParameters<PaginationRequestV2, PageV2<ResourceDoc>> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/doc/resources" + "/browse", {itemsPerPage: request.itemsPerPage, next: request.next, consistency: request.consistency, itemsToSkip: request.itemsToSkip}),
        parameters: request,
        reloadId: Math.random(),
    };
}
}
export namespace control {
export function approveConnection(
    request: IntegrationControlApproveConnectionRequest
): APICallParameters<IntegrationControlApproveConnectionRequest, any /* unknown */> {
    return {
        context: "",
        method: "POST",
        path: "/api/providers/integration/control" + "/approveConnection",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
}
export namespace ProvidersRequestApprovalResponseNS {
export interface RequiresSignature {
    token: string,
    type: ("requires_signature"),
}
export interface AwaitingAdministratorApproval {
    token: string,
    type: ("awaiting_admin_approval"),
}
}
export namespace providers {
export function create(
    request: BulkRequest<ProviderSpecification>
): APICallParameters<BulkRequest<ProviderSpecification>, BulkResponse<FindByStringId>> {
    return {
        context: "",
        method: "POST",
        path: "/api/providers",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function updateAcl(
    request: BulkRequest<ProvidersUpdateAclRequestItem>
): APICallParameters<BulkRequest<ProvidersUpdateAclRequestItem>, any /* unknown */> {
    return {
        context: "",
        method: "POST",
        path: "/api/providers" + "/updateAcl",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function renewToken(
    request: BulkRequest<ProvidersRenewRefreshTokenRequestItem>
): APICallParameters<BulkRequest<ProvidersRenewRefreshTokenRequestItem>, any /* unknown */> {
    return {
        context: "",
        method: "POST",
        path: "/api/providers" + "/renewToken",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function retrieve(
    request: FindByStringId
): APICallParameters<FindByStringId, Provider> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/api/providers" + "/retrieve", {id: request.id}),
        parameters: request,
        reloadId: Math.random(),
    };
}
export function retrieveSpecification(
    request: FindByStringId
): APICallParameters<FindByStringId, ProviderSpecification> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/api/providers" + "/retrieveSpecification", {id: request.id}),
        parameters: request,
        reloadId: Math.random(),
    };
}
export function browse(
    request: ProvidersBrowseRequest
): APICallParameters<ProvidersBrowseRequest, PageV2<Provider>> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/api/providers" + "/browse", {itemsPerPage: request.itemsPerPage, next: request.next, consistency: request.consistency, itemsToSkip: request.itemsToSkip}),
        parameters: request,
        reloadId: Math.random(),
    };
}
export function requestApproval(
    request: ProvidersRequestApprovalRequest
): APICallParameters<ProvidersRequestApprovalRequest, ProvidersRequestApprovalResponse> {
    return {
        context: "",
        method: "POST",
        path: "/api/providers" + "/requestApproval",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function approve(
    request: ProvidersApproveRequest
): APICallParameters<ProvidersApproveRequest, FindByStringId> {
    return {
        context: "",
        method: "POST",
        path: "/api/providers" + "/approve",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
}
export namespace AclEntityNS {
export interface ProjectGroup {
    projectId: string,
    group: string,
    type: ("project_group"),
}
export interface User {
    username: string,
    type: ("user"),
}
}
export namespace ResourceBillingNS {
/**
 * Contains information related to the accounting/billing of a `Resource`
 *
 * Note that this object contains the price of the `Product`. This price may differ, over-time, from the actual price of
 * the `Product`. This allows providers to provide a gradual change of price for products. By allowing existing `Resource`s
 * to be charged a different price than newly launched products.
 */
export interface Free {
    creditsCharged: number /* int64 */,
    pricePerUnit: number /* int64 */,
}
}
export namespace im {
export function browse(
    request: IntegrationBrowseRequest
): APICallParameters<IntegrationBrowseRequest, PageV2<IntegrationBrowseResponseItem>> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/api/providers/integration" + "/browse", {itemsPerPage: request.itemsPerPage, next: request.next, consistency: request.consistency, itemsToSkip: request.itemsToSkip}),
        parameters: request,
        reloadId: Math.random(),
    };
}
export function clearConnection(
    request: IntegrationClearConnectionRequest
): APICallParameters<IntegrationClearConnectionRequest, any /* unknown */> {
    return {
        context: "",
        method: "POST",
        path: "/api/providers/integration" + "/clearConnection",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function connect(
    request: IntegrationConnectRequest
): APICallParameters<IntegrationConnectRequest, IntegrationConnectResponse> {
    return {
        context: "",
        method: "POST",
        path: "/api/providers/integration" + "/connect",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
}
export namespace PermissionNS {
/**
 * Base type for all permissions of the UCloud authorization model

 * This type covers the permission part of UCloud's RBAC based authorization model. UCloud defines a set of standard
 * permissions that can be applied to a resource and its associated operations.
 *
 * 1. `READ`: Grants an entity access to all read-based operations. Read-based operations must not alter the state of a
 * resource. Typical examples include the `browse` and `retrieve*` endpoints.
 * 2. `EDIT`: Grants an entity access to all write-based operations. Write-based operations are allowed to alter the state
 * of a resource. This permission is required for most `update*` endpoints.
 * 3. `ADMIN`: Grants an entity access to special privileged operations. This permission will allow the entity to perform
 * any action on the resource, unless the operation specifies otherwise. This operation is, for example, used for updating
 * the permissions attached to a resource.
 *
 * Apart from the standard permissions, a resource may define additional permissions. These are documented along with
 * the resource and related operations.
 *
 */
export interface Read {
    canBeGranted: boolean,
    name: string,
    type: ("dk.sdu.cloud.api.Permission.Read"),
}
/**
 * Base type for all permissions of the UCloud authorization model

 * This type covers the permission part of UCloud's RBAC based authorization model. UCloud defines a set of standard
 * permissions that can be applied to a resource and its associated operations.
 *
 * 1. `READ`: Grants an entity access to all read-based operations. Read-based operations must not alter the state of a
 * resource. Typical examples include the `browse` and `retrieve*` endpoints.
 * 2. `EDIT`: Grants an entity access to all write-based operations. Write-based operations are allowed to alter the state
 * of a resource. This permission is required for most `update*` endpoints.
 * 3. `ADMIN`: Grants an entity access to special privileged operations. This permission will allow the entity to perform
 * any action on the resource, unless the operation specifies otherwise. This operation is, for example, used for updating
 * the permissions attached to a resource.
 *
 * Apart from the standard permissions, a resource may define additional permissions. These are documented along with
 * the resource and related operations.
 *
 */
export interface Edit {
    canBeGranted: boolean,
    name: string,
    type: ("dk.sdu.cloud.api.Permission.Edit"),
}
/**
 * Base type for all permissions of the UCloud authorization model

 * This type covers the permission part of UCloud's RBAC based authorization model. UCloud defines a set of standard
 * permissions that can be applied to a resource and its associated operations.
 *
 * 1. `READ`: Grants an entity access to all read-based operations. Read-based operations must not alter the state of a
 * resource. Typical examples include the `browse` and `retrieve*` endpoints.
 * 2. `EDIT`: Grants an entity access to all write-based operations. Write-based operations are allowed to alter the state
 * of a resource. This permission is required for most `update*` endpoints.
 * 3. `ADMIN`: Grants an entity access to special privileged operations. This permission will allow the entity to perform
 * any action on the resource, unless the operation specifies otherwise. This operation is, for example, used for updating
 * the permissions attached to a resource.
 *
 * Apart from the standard permissions, a resource may define additional permissions. These are documented along with
 * the resource and related operations.
 *
 */
export interface Admin {
    canBeGranted: boolean,
    name: string,
    type: ("dk.sdu.cloud.api.Permission.Admin"),
}
/**
 * Base type for all permissions of the UCloud authorization model

 * This type covers the permission part of UCloud's RBAC based authorization model. UCloud defines a set of standard
 * permissions that can be applied to a resource and its associated operations.
 *
 * 1. `READ`: Grants an entity access to all read-based operations. Read-based operations must not alter the state of a
 * resource. Typical examples include the `browse` and `retrieve*` endpoints.
 * 2. `EDIT`: Grants an entity access to all write-based operations. Write-based operations are allowed to alter the state
 * of a resource. This permission is required for most `update*` endpoints.
 * 3. `ADMIN`: Grants an entity access to special privileged operations. This permission will allow the entity to perform
 * any action on the resource, unless the operation specifies otherwise. This operation is, for example, used for updating
 * the permissions attached to a resource.
 *
 * Apart from the standard permissions, a resource may define additional permissions. These are documented along with
 * the resource and related operations.
 *
 */
export interface Provider {
    canBeGranted: boolean,
    name: string,
    type: ("dk.sdu.cloud.api.Permission.Provider"),
}
}
export namespace ProvidersRequestApprovalRequestNS {
export interface Information {
    specification: ProviderSpecification,
    type: ("information"),
}
export interface Sign {
    token: string,
    type: ("sign"),
}
}
}
export namespace accounting {
export interface AddToBalanceRequest {
    wallet: Wallet,
    credits: number /* int64 */,
}
export interface Wallet {
    id: string,
    type: ("USER" | "PROJECT"),
    paysFor: ProductCategoryId,
}
export interface ProductCategoryId {
    name: string,
    provider: string,
}
export interface AddToBalanceBulkRequest {
    requests: AddToBalanceRequest[],
}
export interface ReserveCreditsRequest {
    jobId: string,
    amount: number /* int64 */,
    expiresAt: number /* int64 */,
    account: Wallet,
    jobInitiatedBy: string,
    productId: string,
    productUnits: number /* int64 */,
    discardAfterLimitCheck: boolean,
    chargeImmediately: boolean,
    skipIfExists: boolean,
    skipLimitCheck: boolean,
    transactionType: ("GIFTED" | "TRANSFERRED_TO_PERSONAL" | "TRANSFERRED_TO_PROJECT" | "PAYMENT"),
}
export interface ReserveCreditsBulkRequest {
    reservations: ReserveCreditsRequest[],
}
export interface ChargeReservationRequest {
    name: string,
    amount: number /* int64 */,
    productUnits: number /* int64 */,
}
export interface TransferToPersonalRequest {
    transfers: SingleTransferRequest[],
}
export interface SingleTransferRequest {
    initiatedBy: string,
    amount: number /* int64 */,
    sourceAccount: Wallet,
    destinationAccount: Wallet,
}
export interface RetrieveBalanceResponse {
    wallets: WalletBalance[],
}
export interface WalletBalance {
    wallet: Wallet,
    balance: number /* int64 */,
    allocated: number /* int64 */,
    used: number /* int64 */,
    area: ("STORAGE" | "COMPUTE" | "INGRESS" | "LICENSE" | "NETWORK_IP"),
}
export interface RetrieveBalanceRequest {
    id?: string,
    type?: ("USER" | "PROJECT"),
    includeChildren?: boolean,
    showHidden?: boolean,
}
export interface SetBalanceRequest {
    wallet: Wallet,
    lastKnownBalance: number /* int64 */,
    newBalance: number /* int64 */,
}
export interface RetrieveWalletsForProjectsRequest {
    projectIds: string[],
}
export interface WalletsGrantProviderCreditsRequest {
    provider: string,
}
export type Product = ProductNS.Storage | ProductNS.Compute | ProductNS.Ingress | ProductNS.License | ProductNS.NetworkIP
export type ProductAvailability = ProductAvailabilityNS.Available | ProductAvailabilityNS.Unavailable
export interface FindProductRequest {
    provider: string,
    productCategory: string,
    product: string,
}
export interface ListProductsRequest {
    provider: string,
    itemsPerPage?: number /* int32 */,
    page?: number /* int32 */,
}
export interface ListProductsByAreaRequest {
    provider: string,
    area: ("STORAGE" | "COMPUTE" | "INGRESS" | "LICENSE" | "NETWORK_IP"),
    showHidden: boolean,
    itemsPerPage?: number /* int32 */,
    page?: number /* int32 */,
}
export interface RetrieveAllFromProviderRequest {
    provider: string,
    showHidden: boolean,
}
/**
 * The base type for requesting paginated content.
 *
 * Paginated content can be requested with one of the following `consistency` guarantees, this greatly changes the
 * semantics of the call:
 *
 * | Consistency | Description |
 * |-------------|-------------|
 * | `PREFER` | Consistency is preferred but not required. An inconsistent snapshot might be returned. |
 * | `REQUIRE` | Consistency is required. A request will fail if consistency is no longer guaranteed. |
 *
 * The `consistency` refers to if collecting all the results via the pagination API are _consistent_. We consider the
 * results to be consistent if it contains a complete view at some point in time. In practice this means that the results
 * must contain all the items, in the correct order and without duplicates.
 *
 * If you use the `PREFER` consistency then you may receive in-complete results that might appear out-of-order and can
 * contain duplicate items. UCloud will still attempt to serve a snapshot which appears mostly consistent. This is helpful
 * for user-interfaces which do not strictly depend on consistency but would still prefer something which is mostly
 * consistent.
 *
 * The results might become inconsistent if the client either takes too long, or a service instance goes down while
 * fetching the results. UCloud attempts to keep each `next` token alive for at least one minute before invalidating it.
 * This does not mean that a client must collect all results within a minute but rather that they must fetch the next page
 * within a minute of the last page. If this is not feasible and consistency is not required then `PREFER` should be used.
 *
 * ---
 *
 * __📝 NOTE:__ Services are allowed to ignore extra criteria of the request if the `next` token is supplied. This is
 * needed in order to provide a consistent view of the results. Clients _should_ provide the same criterion as they
 * paginate through the results.
 *
 * ---
 *
 */
export interface ProductsBrowseRequest {
    /**
     * Requested number of items per page. Supported values: 10, 25, 50, 100, 250.
     */
    itemsPerPage?: number /* int32 */,
    /**
     * A token requesting the next page of items
     */
    next?: string,
    /**
     * Controls the consistency guarantees provided by the backend
     */
    consistency?: ("PREFER" | "REQUIRE"),
    /**
     * Items to skip ahead
     */
    itemsToSkip?: number /* int64 */,
    filterProvider?: string,
    filterArea?: ("STORAGE" | "COMPUTE" | "INGRESS" | "LICENSE" | "NETWORK_IP"),
    filterUsable?: boolean,
    filterCategory?: string,
    includeBalance?: boolean,
    includeMaxBalance?: boolean,
}
export interface UsageResponse {
    charts: UsageChart[],
}
export interface UsageChart {
    provider: string,
    lines: UsageLine[],
}
export interface UsageLine {
    area: ("STORAGE" | "COMPUTE" | "INGRESS" | "LICENSE" | "NETWORK_IP"),
    category: string,
    projectPath?: string,
    projectId?: string,
    points: UsagePoint[],
}
export interface UsagePoint {
    timestamp: number /* int64 */,
    creditsUsed: number /* int64 */,
}
export interface UsageRequest {
    bucketSize: number /* int64 */,
    periodStart: number /* int64 */,
    periodEnd: number /* int64 */,
}
/**
 * Contains a unique reference to a [Product](/backend/accounting-service/README.md)
 */
export interface ProductReference {
    /**
     * The `Product` ID
     */
    id: string,
    /**
     * The ID of the `Product`'s category
     */
    category: string,
    /**
     * The provider of the `Product`
     */
    provider: string,
}
export namespace products {
export function findProduct(
    request: FindProductRequest
): APICallParameters<FindProductRequest, Product> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/api/products", {provider: request.provider, productCategory: request.productCategory, product: request.product}),
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
    request: BulkRequest<Product>
): APICallParameters<BulkRequest<Product>, any /* unknown */> {
    return {
        context: "",
        method: "POST",
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
        path: buildQueryString("/api/products" + "/list", {provider: request.provider, itemsPerPage: request.itemsPerPage, page: request.page}),
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
        path: buildQueryString("/api/products" + "/listByArea", {provider: request.provider, area: request.area, itemsPerPage: request.itemsPerPage, page: request.page, showHidden: request.showHidden}),
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
        path: buildQueryString("/api/products" + "/retrieve", {provider: request.provider, showHidden: request.showHidden}),
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
        path: buildQueryString("/api/products" + "/browse", {itemsPerPage: request.itemsPerPage, next: request.next, consistency: request.consistency, itemsToSkip: request.itemsToSkip, filterProvider: request.filterProvider, filterArea: request.filterArea, filterUsable: request.filterUsable, filterCategory: request.filterCategory, includeBalance: request.includeBalance, includeMaxBalance: request.includeMaxBalance}),
        parameters: request,
        reloadId: Math.random(),
    };
}
}
export namespace ProductAvailabilityNS {
export interface Available {
    type: ("available"),
}
export interface Unavailable {
    reason: string,
    type: ("unavailable"),
}
}
export namespace visualization {
export function usage(
    request: UsageRequest
): APICallParameters<UsageRequest, UsageResponse> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/api/accounting/visualization" + "/usage", {bucketSize: request.bucketSize, periodEnd: request.periodEnd, periodStart: request.periodStart}),
        parameters: request,
        reloadId: Math.random(),
    };
}
}
export namespace providers {
export interface ResolvedSupport<P = unknown, Support = unknown> {
    product: P,
    support: Support,
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
        path: buildQueryString("/api/accounting/wallets" + "/balance", {id: request.id, type: request.type, includeChildren: request.includeChildren, showHidden: request.showHidden}),
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
export function retrieveWalletsFromProjects(
    request: RetrieveWalletsForProjectsRequest
): APICallParameters<RetrieveWalletsForProjectsRequest, Wallet[]> {
    return {
        context: "",
        method: "POST",
        path: "/api/accounting/wallets" + "/retrieveWallets",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
export function grantProviderCredits(
    request: WalletsGrantProviderCreditsRequest
): APICallParameters<WalletsGrantProviderCreditsRequest, any /* unknown */> {
    return {
        context: "",
        method: "POST",
        path: "/api/accounting/wallets" + "/grantProviderCredits",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
    };
}
}
export namespace ProductNS {
export interface Storage {
    id: string,
    pricePerUnit: number /* int64 */,
    category: ProductCategoryId,
    description: string,
    hiddenInGrantApplications: boolean,
    availability: ProductAvailability,
    priority: number /* int32 */,
    area: ("STORAGE" | "COMPUTE" | "INGRESS" | "LICENSE" | "NETWORK_IP"),
    /**
     * Included only with certain endpoints which support `includeBalance`
     */
    balance?: number /* int64 */,
    type: ("storage"),
}
export interface Compute {
    name: string,
    pricePerUnit: number /* int64 */,
    category: ProductCategoryId,
    description: string,
    hiddenInGrantApplications: boolean,
    availability: ProductAvailability,
    priority: number /* int32 */,
    cpu?: number /* int32 */,
    memoryInGigs?: number /* int32 */,
    gpu?: number /* int32 */,
    area: ("STORAGE" | "COMPUTE" | "INGRESS" | "LICENSE" | "NETWORK_IP"),
    /**
     * Included only with certain endpoints which support `includeBalance`
     */
    balance?: number /* int64 */,
    type: ("compute"),
}
export interface Ingress {
    id: string,
    pricePerUnit: number /* int64 */,
    category: ProductCategoryId,
    description: string,
    hiddenInGrantApplications: boolean,
    availability: ProductAvailability,
    priority: number /* int32 */,
    paymentModel: ("FREE_BUT_REQUIRE_BALANCE" | "PER_ACTIVATION"),
    area: ("STORAGE" | "COMPUTE" | "INGRESS" | "LICENSE" | "NETWORK_IP"),
    /**
     * Included only with certain endpoints which support `includeBalance`
     */
    balance?: number /* int64 */,
    type: ("ingress"),
}
export interface License {
    id: string,
    pricePerUnit: number /* int64 */,
    category: ProductCategoryId,
    description: string,
    hiddenInGrantApplications: boolean,
    availability: ProductAvailability,
    priority: number /* int32 */,
    tags: string[],
    paymentModel: ("FREE_BUT_REQUIRE_BALANCE" | "PER_ACTIVATION"),
    area: ("STORAGE" | "COMPUTE" | "INGRESS" | "LICENSE" | "NETWORK_IP"),
    /**
     * Included only with certain endpoints which support `includeBalance`
     */
    balance?: number /* int64 */,
    type: ("license"),
}
export interface NetworkIP {
    id: string,
    pricePerUnit: number /* int64 */,
    category: ProductCategoryId,
    description: string,
    hiddenInGrantApplications: boolean,
    availability: ProductAvailability,
    priority: number /* int32 */,
    paymentModel: ("FREE_BUT_REQUIRE_BALANCE" | "PER_ACTIVATION"),
    area: ("STORAGE" | "COMPUTE" | "INGRESS" | "LICENSE" | "NETWORK_IP"),
    /**
     * Included only with certain endpoints which support `includeBalance`
     */
    balance?: number /* int64 */,
    type: ("network_ip"),
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
        path: buildQueryString("/api/activity" + "/by-path", {itemsPerPage: request.itemsPerPage, page: request.page, path: request.path}),
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
        path: buildQueryString("/api/activity" + "/browse", {user: request.user, offset: request.offset, scrollSize: request.scrollSize, type: request.type, minTimestamp: request.minTimestamp, maxTimestamp: request.maxTimestamp}),
        parameters: request,
        reloadId: Math.random(),
    };
}
export interface ActivityForFrontend {
    type: ("download" | "deleted" | "favorite" | "moved" | "copy" | "usedInApp" | "directoryCreated" | "reclassify" | "upload" | "updatedACL" | "sharedWith" | "allUsedInApp"),
    timestamp: number /* int64 */,
    activityEvent: ActivityEvent,
}
export type ActivityEvent = ActivityEventNS.Reclassify | ActivityEventNS.DirectoryCreated | ActivityEventNS.Download | ActivityEventNS.Copy | ActivityEventNS.Uploaded | ActivityEventNS.UpdatedAcl | ActivityEventNS.UpdateProjectAcl | ActivityEventNS.Favorite | ActivityEventNS.Moved | ActivityEventNS.Deleted | ActivityEventNS.SingleFileUsedByApplication | ActivityEventNS.AllFilesUsedByApplication | ActivityEventNS.SharedWith
export interface ListActivityByPathRequest {
    path: string,
    itemsPerPage?: number /* int32 */,
    page?: number /* int32 */,
}
export namespace ActivityNS {

export namespace BrowseByUserNS {
export interface Response {
    endOfScroll: boolean,
    items: ActivityForFrontend[],
    nextOffset: number /* int32 */,
}
export interface Request {
    user?: string,
    type?: ("download" | "deleted" | "favorite" | "moved" | "copy" | "usedInApp" | "directoryCreated" | "reclassify" | "upload" | "updatedACL" | "sharedWith" | "allUsedInApp"),
    minTimestamp?: number /* int64 */,
    maxTimestamp?: number /* int64 */,
    offset?: number /* int32 */,
    scrollSize?: number /* int32 */,
}
}
}
export namespace ActivityEventNS {
export interface Reclassify {
    username: string,
    timestamp: number /* int64 */,
    filePath: string,
    newSensitivity: string,
    type: ("reclassify"),
}
export interface DirectoryCreated {
    username: string,
    timestamp: number /* int64 */,
    filePath: string,
    type: ("directory_created"),
}
export interface Download {
    username: string,
    timestamp: number /* int64 */,
    filePath: string,
    type: ("download"),
}
export interface Copy {
    username: string,
    timestamp: number /* int64 */,
    filePath: string,
    copyFilePath: string,
    type: ("copy"),
}
export interface Uploaded {
    username: string,
    timestamp: number /* int64 */,
    filePath: string,
    type: ("uploaded"),
}
export interface UpdatedAcl {
    username: string,
    timestamp: number /* int64 */,
    filePath: string,
    rightsAndUser: RightsAndUser[],
    type: ("updated_acl"),
}
export interface RightsAndUser {
    rights: string[],
    user: string,
}
export interface UpdateProjectAcl {
    username: string,
    timestamp: number /* int64 */,
    filePath: string,
    project: string,
    acl: ProjectAclEntry[],
    type: ("update_project_acl"),
}
export interface ProjectAclEntry {
    group: string,
    rights: string[],
}
export interface Favorite {
    username: string,
    isFavorite: boolean,
    timestamp: number /* int64 */,
    filePath: string,
    type: ("favorite"),
}
export interface Moved {
    username: string,
    newName: string,
    timestamp: number /* int64 */,
    filePath: string,
    type: ("moved"),
}
export interface Deleted {
    username: string,
    timestamp: number /* int64 */,
    filePath: string,
    type: ("deleted"),
}
export interface SingleFileUsedByApplication {
    username: string,
    timestamp: number /* int64 */,
    filePath: string,
    applicationName: string,
    applicationVersion: string,
    type: ("single_file_used_by_application"),
}
export interface AllFilesUsedByApplication {
    username: string,
    timestamp: number /* int64 */,
    filePath: string,
    applicationName: string,
    applicationVersion: string,
    type: ("all_files_used_by_application"),
}
export interface SharedWith {
    username: string,
    timestamp: number /* int64 */,
    filePath: string,
    sharedWith: string,
    status: string[],
    type: ("shared_with"),
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
        path: buildQueryString("/api/notifications", {type: request.type, since: request.since, itemsPerPage: request.itemsPerPage, page: request.page}),
        parameters: request,
        reloadId: Math.random(),
    };
}
export function remove(
    request: FindByNotificationIdBulk
): APICallParameters<FindByNotificationIdBulk, DeleteResponse> {
    return {
        context: "",
        method: "DELETE",
        path: "/api/notifications",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
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
    request: FindByNotificationIdBulk
): APICallParameters<FindByNotificationIdBulk, MarkResponse> {
    return {
        context: "",
        method: "POST",
        path: "/api/notifications" + "/read",
        parameters: request,
        reloadId: Math.random(),
        payload: request,
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
export interface Notification {
    type: string,
    message: string,
    id?: number /* int64 */,
    meta: Record<string, any /* unknown */>,
    ts: number /* int64 */,
    read: boolean,
}
export interface ListNotificationRequest {
    type?: string,
    since?: number /* int64 */,
    itemsPerPage?: number /* int32 */,
    page?: number /* int32 */,
}
export interface CreateNotification {
    user: string,
    notification: Notification,
}
export interface DeleteResponse {
    failures: number /* int64 */[],
}
export interface FindByNotificationIdBulk {
    ids: string,
}
export interface MarkResponse {
    failures: number /* int64 */[],
}
}
export namespace grant {
export interface AvailableGiftsResponse {
    gifts: GiftWithId[],
}
export interface GiftWithId {
    id: number /* int64 */,
    resourcesOwnedBy: string,
    title: string,
    description: string,
    resources: ResourceRequest[],
}
export interface ResourceRequest {
    productCategory: string,
    productProvider: string,
    balanceRequested?: number /* int64 */,
}
export interface ClaimGiftRequest {
    giftId: number /* int64 */,
}
export interface GiftWithCriteria {
    id: number /* int64 */,
    resourcesOwnedBy: string,
    title: string,
    description: string,
    resources: ResourceRequest[],
    criteria: UserCriteria[],
}
export type UserCriteria = UserCriteriaNS.Anyone | UserCriteriaNS.EmailDomain | UserCriteriaNS.WayfOrganization
export interface DeleteGiftRequest {
    giftId: number /* int64 */,
}
export interface ListGiftsResponse {
    gifts: GiftWithCriteria[],
}
export interface ApproveApplicationRequest {
    requestId: number /* int64 */,
}
export interface RejectApplicationRequest {
    requestId: number /* int64 */,
    notify?: boolean,
}
export interface CloseApplicationRequest {
    requestId: number /* int64 */,
}
export interface TransferApplicationRequest {
    applicationId: number /* int64 */,
    transferToProjectId: string,
}
export interface CommentOnApplicationRequest {
    requestId: number /* int64 */,
    comment: string,
}
export interface DeleteCommentRequest {
    commentId: number /* int64 */,
}
export interface CreateApplication {
    resourcesOwnedBy: string,
    grantRecipient: GrantRecipient,
    document: string,
    requestedResources: ResourceRequest[],
}
export type GrantRecipient = GrantRecipientNS.PersonalProject | GrantRecipientNS.ExistingProject | GrantRecipientNS.NewProject
export interface EditApplicationRequest {
    id: number /* int64 */,
    newDocument: string,
    newResources: ResourceRequest[],
}
export interface UploadTemplatesRequest {
    personalProject: string,
    newProject: string,
    existingProject: string,
}
export interface ProjectApplicationSettings {
    allowRequestsFrom: UserCriteria[],
    excludeRequestsFrom: UserCriteria[],
}
export interface ReadTemplatesRequest {
    projectId: string,
}
export interface Application {
    status: ("APPROVED" | "REJECTED" | "CLOSED" | "IN_PROGRESS"),
    resourcesOwnedBy: string,
    requestedBy: string,
    grantRecipient: GrantRecipient,
    document: string,
    requestedResources: ResourceRequest[],
    id: number /* int64 */,
    resourcesOwnedByTitle: string,
    grantRecipientPi: string,
    grantRecipientTitle: string,
    createdAt: number /* int64 */,
    updatedAt: number /* int64 */,
    statusChangedBy?: string,
}
export interface IngoingApplicationsRequest {
    itemsPerPage?: number /* int32 */,
    page?: number /* int32 */,
    filter: ("SHOW_ALL" | "ACTIVE" | "INACTIVE"),
}
export interface OutgoingApplicationsRequest {
    itemsPerPage?: number /* int32 */,
    page?: number /* int32 */,
    filter: ("SHOW_ALL" | "ACTIVE" | "INACTIVE"),
}
export interface ApplicationWithComments {
    application: Application,
    comments: Comment[],
    approver: boolean,
}
export interface Comment {
    id: number /* int64 */,
    postedBy: string,
    postedAt: number /* int64 */,
    comment: string,
}
export interface ViewApplicationRequest {
    id: number /* int64 */,
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
    itemsPerPage?: number /* int32 */,
    page?: number /* int32 */,
}
export interface GrantsRetrieveAffiliationsRequest {
    grantId: number /* int64 */,
    itemsPerPage?: number /* int32 */,
    page?: number /* int32 */,
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
    showHidden: boolean,
}
export namespace UserCriteriaNS {
export interface Anyone {
    type: ("anyone"),
}
export interface EmailDomain {
    domain: string,
    type: ("email"),
}
export interface WayfOrganization {
    org: string,
    type: ("wayf"),
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
export function transferApplication(
    request: TransferApplicationRequest
): APICallParameters<TransferApplicationRequest, any /* unknown */> {
    return {
        context: "",
        method: "POST",
        path: "/api/grant" + "/transfer",
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
        path: buildQueryString("/api/grant" + "/ingoing", {itemsPerPage: request.itemsPerPage, page: request.page, filter: request.filter}),
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
        path: buildQueryString("/api/grant" + "/outgoing", {itemsPerPage: request.itemsPerPage, page: request.page, filter: request.filter}),
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
        path: buildQueryString("/api/grant", {id: request.id}),
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
        path: buildQueryString("/api/grant" + "/browse-projects", {itemsPerPage: request.itemsPerPage, page: request.page}),
        parameters: request,
        reloadId: Math.random(),
    };
}
export function retrieveAffiliations(
    request: GrantsRetrieveAffiliationsRequest
): APICallParameters<GrantsRetrieveAffiliationsRequest, Page<ProjectWithTitle>> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/api/grant" + "/retrieveAffiliations", {grantId: request.grantId, itemsPerPage: request.itemsPerPage, page: request.page}),
        parameters: request,
        reloadId: Math.random(),
    };
}
export function uploadLogo(): APICallParameters<{}, any /* unknown */> {
    return {
        context: "",
        method: "POST",
        path: "/api/grant" + "/uploadLogo",
        reloadId: Math.random(),
    };
}
export function fetchLogo(
    request: FetchLogoRequest
): APICallParameters<FetchLogoRequest, any /* unknown */> {
    return {
        context: "",
        method: "GET",
        path: buildQueryString("/api/grant" + "/logo", {projectId: request.projectId}),
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
        path: buildQueryString("/api/grant" + "/retrieveProducts", {projectId: request.projectId, recipientId: request.recipientId, recipientType: request.recipientType, showHidden: request.showHidden}),
        parameters: request,
        reloadId: Math.random(),
    };
}
}
export namespace GrantRecipientNS {
export interface PersonalProject {
    username: string,
    type: ("personal"),
}
export interface ExistingProject {
    projectId: string,
    type: ("existing_project"),
}
export interface NewProject {
    projectTitle: string,
    type: ("new_project"),
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
export function markAsComplete(
    request: FindByStringId
): APICallParameters<FindByStringId, any /* unknown */> {
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
        path: buildQueryString("/api/tasks" + "/retrieve", {id: request.id}),
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
    title: string,
    speed: number /* float64 */,
    unit: string,
    asText: string,
}
export interface Progress {
    title: string,
    current: number /* int32 */,
    maximum: number /* int32 */,
}
export interface Task {
    jobId: string,
    owner: string,
    processor: string,
    title?: string,
    status?: string,
    complete: boolean,
    startedAt: number /* int64 */,
    modifiedAt: number /* int64 */,
}
export interface ListRequest {
    itemsPerPage?: number /* int32 */,
    page?: number /* int32 */,
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
export namespace kotlinx {

export namespace serialization {

export namespace json {
export type JsonElement = JsonPrimitive | JsonArray
export type JsonPrimitive = JsonLiteral | JsonNull
export interface JsonLiteral {
    body: any /* unknown */,
    isString: boolean,
    content: string,
    type: ("JsonLiteral"),
}
export interface JsonNull {
    content: string,
    isString: boolean,
    type: ("JsonNull"),
}
export interface JsonArray {
    content: JsonElement[],
    size: number /* int32 */,
    type: ("JsonArray"),
}
}
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
        path: buildQueryString("/api/news" + "/list", {filter: request.filter, withHidden: request.withHidden, itemsPerPage: request.itemsPerPage, page: request.page}),
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
    showFrom: number /* int64 */,
    category: string,
    hideFrom?: number /* int64 */,
}
export interface UpdatePostRequest {
    id: number /* int64 */,
    title: string,
    subtitle: string,
    body: string,
    showFrom: number /* int64 */,
    hideFrom?: number /* int64 */,
    category: string,
}
export interface DeleteNewsPostRequest {
    id: number /* int64 */,
}
export interface TogglePostHiddenRequest {
    id: number /* int64 */,
}
export interface NewsPost {
    id: number /* int64 */,
    title: string,
    subtitle: string,
    body: string,
    postedBy: string,
    showFrom: number /* int64 */,
    hideFrom?: number /* int64 */,
    hidden: boolean,
    category: string,
}
export interface ListPostsRequest {
    filter?: string,
    withHidden: boolean,
    page: number /* int32 */,
    itemsPerPage: number /* int32 */,
}
export interface GetPostByIdRequest {
    id: number /* int64 */,
}
}
export namespace kotlin {
export interface Pair<A = unknown, B = unknown> {
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
    serviceOrigin: ("SHARE_SERVICE" | "PROJECT_SERVICE"),
}
export interface DeleteRequest {
    fromUser: string,
    toUser: string,
    serviceOrigin: ("SHARE_SERVICE" | "PROJECT_SERVICE"),
}
export interface QueryContactsResponse {
    contacts: string[],
}
export interface QueryContactsRequest {
    query: string,
    serviceOrigin: ("SHARE_SERVICE" | "PROJECT_SERVICE"),
}
export interface AllContactsForUserRequest {
    serviceOrigin: ("SHARE_SERVICE" | "PROJECT_SERVICE"),
}
}
