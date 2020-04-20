import {SensitivityLevelMap} from "DefaultObjects";
import * as React from "react";
import {Operation, PredicatedOperation} from "Types";
import {Times} from "./Redux/DetailedFileSearchActions";

export enum SortOrder {
    ASCENDING = "ASCENDING",
    DESCENDING = "DESCENDING"
}

export type FileType = |
    "FILE" |
    "DIRECTORY" |
    "FAVFOLDER" |
    "SHARESFOLDER" |
    "TRASHFOLDER" |
    "FSFOLDER" |
    "RESULTFOLDER";

export interface File {
    fileType: FileType;
    path: string;
    modifiedAt: number | null;
    ownerName: string | null;
    size: number | null;
    acl: Acl[] | null;
    favorited: boolean | null;
    sensitivityLevel: SensitivityLevelMap | null;
    ownSensitivityLevel: SensitivityLevelMap | null;
    mockTag?: string;
    permissionAlert: boolean | null;
    isRepo?: boolean;
}

export interface UserEntity {
    username: string;
}

export interface ProjectEntity {
    group: string;
    projectId: string;
}

export interface Acl {
    entity: ProjectEntity | UserEntity | string;
    rights: string[];
    group: boolean;
}

// FIXME: SortBy is subset of {FileResource}
export enum SortBy {
    FILE_TYPE = "fileType",
    PATH = "path",
    MODIFIED_AT = "modifiedAt",
    SIZE = "size",
    ACL = "acl",
    SENSITIVITY_LEVEL = "sensitivityLevel",
}

export enum FileResource {
    FAVORITED = "favorited",
    FILE_TYPE = "fileType",
    PATH = "path",
    MODIFIED_AT = "modifiedAt",
    OWNER_NAME = "ownerName",
    SIZE = "size",
    ACL = "acl",
    SENSITIVITY_LEVEL = "sensitivityLevel",
    OWN_SENSITIVITY_LEVEL = "ownSensitivityLevel",
}

export interface FileSelectorProps {
    initialPath?: string;
    onFileSelect: (file: {path: string} | null) => void;
    canSelectFolders?: boolean;
    onlyAllowFolders?: boolean;
    trigger: React.ReactNode;
    visible: boolean;
    disallowedPaths?: string[];
}

export interface FileOptionsProps {
    files: File[];
    fileOperations: FileOperation[];
}

export interface SortByDropdownProps {
    currentSelection: SortBy;
    sortOrder: SortOrder;
    onSelect: (sortorder: SortOrder, s: SortBy) => void;
    asDropdown: boolean;
    isSortedBy: boolean;
}

export type FileOperation = Operation<File> | PredicatedOperation<File>;

export interface ContextButtonsProps {
    createFolder: () => void;
    showUploader: () => void;
    inTrashFolder: boolean;
    toHome: () => void;
}


export interface DetailedFileSearchOperations {
    toggleHidden: () => void;
    addExtensions: (ext: string[]) => void;
    removeExtensions: (ext: string[]) => void;
    toggleFolderAllowed: () => void;
    toggleFilesAllowed: () => void;
    toggleIncludeShares: () => void;
    addSensitivity: (sensitivity: SensitivityLevel) => void;
    removeSensitivity: (sensitivity: SensitivityLevel[]) => void;
    addTags: (tags: string[]) => void;
    removeTags: (tags: string[]) => void;
    setFilename: (filename: string) => void;
    setLoading: (loading: boolean) => void;
    setTimes: (times: Times) => void;
    setSearch: (search: string) => void;
}

export type DetailedFileSearchStateProps = DetailedFileSearchReduxState & DetailedFileSearchOperations;

export type SensitivityLevel = "Private" | "Confidential" | "Sensitive";

export interface DetailedFileSearchReduxState {
    hidden: boolean;
    allowFolders: boolean;
    allowFiles: boolean;
    extensions: Set<string>;
    tags: Set<string>;
    sensitivities: Set<SensitivityLevel>;
    modifiedBefore?: Date;
    modifiedAfter?: Date;
    includeShares: boolean;
    error?: string;
    loading: boolean;
}

export type PossibleTime = "createdBefore" | "createdAfter" | "modifiedBefore" | "modifiedAfter";

export interface AdvancedSearchRequest {
    fileName?: string;
    extensions?: string[];
    fileTypes: [FileType?, FileType?];
    modifiedAt?: {after?: number; before?: number};
    sensitivity?: SensitivityLevel[];
    includeShares?: boolean;
    itemsPerPage?: number;
    page?: number;
}
