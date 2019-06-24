import {Page, ClearRefresh} from "Types";
import Cloud from "Authentication/lib";
import * as React from "react";
import PromiseKeeper from "PromiseKeeper";
import {ResponsiveReduxObject, SensitivityLevelMap} from "DefaultObjects";
import {Times} from "./Redux/DetailedFileSearchActions";
import {RouterLocationProps} from "Utilities/URIUtilities";

export enum SortOrder {
    ASCENDING = "ASCENDING",
    DESCENDING = "DESCENDING"
}

export type FileType = "FILE" | "DIRECTORY" | "FAVFOLDER" | "TRASHFOLDER" | "RESULTFOLDER";
export interface File {
    fileType: FileType
    path: string
    creator: string | null
    fileId: string | null
    createdAt: number | null
    modifiedAt: number | null
    ownerName: string | null
    size: number | null
    acl: Acl[] | null
    favorited: boolean | null
    sensitivityLevel: SensitivityLevelMap | null
    ownSensitivityLevel: SensitivityLevelMap | null
    isChecked?: boolean
    beingRenamed?: boolean | null
    link: boolean
    isMockFolder?: boolean | null
}

export interface Acl {
    entity: string
    rights: string[]
    group: boolean
}

// FIXME: SortBy is subset of {FileResource}
export enum SortBy {
    FILE_TYPE = "fileType",
    PATH = "path",
    CREATED_AT = "createdAt",
    MODIFIED_AT = "modifiedAt",
    SIZE = "size",
    ACL = "acl",
    SENSITIVITY_LEVEL = "sensitivityLevel",
}

export enum FileResource {
    FAVORITED = "favorited",
    FILE_TYPE = "fileType",
    PATH = "path",
    CREATED_AT = "createdAt",
    MODIFIED_AT = "modifiedAt",
    OWNER_NAME = "ownerName",
    SIZE = "size",
    ACL = "acl",
    SENSITIVITY_LEVEL = "sensitivityLevel",
    OWN_SENSITIVITY_LEVEL = "ownSensitivityLevel",
    LINK = "link",
    FILE_ID = "fileId",
    CREATOR = "creator"
}

export type FilesProps = FilesStateProps & FilesOperations & RouterLocationProps;

export interface FilesStateProps {
    path: string
    page: Page<File>
    loading: boolean
    fileSelectorShown: boolean
    fileSelectorLoading: boolean
    disallowedPaths: string[]
    fileSelectorCallback: Function
    fileSelectorPath: string
    fileSelectorPage: Page<File>
    fileSelectorIsFavorites: boolean
    sortBy: SortBy
    sortOrder: SortOrder
    error?: string
    fileSelectorError?: string
    favFilesCount: number
    renamingCount: number
    sensitivityCount: number
    aclCount: number
    fileCount: number
    leftSortingColumn: SortBy
    rightSortingColumn: SortBy
    invalidPath: boolean
    responsive?: ResponsiveReduxObject
}

export interface FilesOperations extends ClearRefresh {
    onInit: () => void
    onFileSelectorErrorDismiss: () => void
    dismissError: () => void
    fetchFiles: (path: string, itemsPerPage: number, pageNumber: number, sortOrder: SortOrder, sortBy: SortBy, attrs: FileResource[], index?: number) => void
    fetchPageFromPath: (path: string, itemsPerPage: number, sortOrder: SortOrder, sortBy: SortBy, attrs: FileResource[]) => void;
    fetchSelectorFiles: (path: string, pageNumber: number, itemsPerPage: number) => void
    fetchFileSelectorFavorites: (pageNumber: number, itemsPerPage: number) => void
    setFileSelectorCallback: (callback: Function) => void
    checkFile: (checked: boolean, path: string) => void
    setLoading: (loading: boolean) => void
    updateFiles: (files: Page<File>) => void
    updatePath: (path: string) => void
    showFileSelector: (open: boolean) => void
    checkAllFiles: (checked: boolean) => void
    setDisallowedPaths: (disallowedPaths: string[]) => void
    showUploader: () => void
    setUploaderCallback: (callback: (s: string) => void) => void
    createFolder: () => void
}

export interface FileSelectorProps {
    allowUpload?: boolean
    inputRef?: React.RefObject<HTMLInputElement>
    showError?: boolean
    onFileSelect: (file: {path: string}) => void
    path: string
    defaultValue?: string
    isRequired?: boolean
    canSelectFolders?: boolean
    onlyAllowFolders?: boolean
    unitName?: string | React.ReactNode
    unitWidth?: string | number | undefined
    remove?: () => void
}

export interface FileSelectorState {
    promises: PromiseKeeper
    path: string
    error?: string
    loading: boolean
    page: Page<File>
    modalShown: boolean
    isFavorites: boolean
}

export interface FilesTableProps {
    onNavigationClick?: (path: string) => void
    sortOrder: SortOrder
    onDropdownSelect?: (sortOrder: SortOrder, sortBy: SortBy, index?: number) => void
    sortingColumns: SortBy[]
    files: File[]
    masterCheckbox?: React.ReactNode
    sortingIcon?: (name: SortBy) => "arrowUp" | "arrowDown" | undefined
    sortFiles: (sortOrder: SortOrder, sortBy: SortBy) => void
    onRenameFile?: (key: number, file: File, name: string) => void
    onCreateFolder?: (key: number, name: string) => void
    onCheckFile: (c: boolean, f: File) => void
    refetchFiles: () => void
    sortBy: SortBy
    onFavoriteFile?: (f: File[]) => void
    fileOperations: FileOperation[]
    responsive: ResponsiveReduxObject
    notStickyHeader?: boolean
}

export interface FilesTableHeaderProps {
    toSortingIcon?: (s: SortBy) => "arrowUp" | "arrowDown" | undefined
    sortFiles?: (sortOrder: SortOrder, sortBy: SortBy) => void
    sortOrder: SortOrder
    sortBy: SortBy
    masterCheckbox?: React.ReactNode
    sortingColumns: SortBy[]
    onDropdownSelect?: (sortOrder: SortOrder, sortBy: SortBy, index: number) => void
    customEntriesWidth?: string
    notStickyHeader?: boolean
    children: React.ReactNode
}

export interface FilenameAndIconsProps {
    size?: number | string
    file: File
    hasCheckbox: boolean
    onRenameFile?: (key: number, file: File, name: string) => void
    onCheckFile?: (c: boolean) => void
    onFavoriteFile?: (files: File[]) => void
    onNavigationClick?: (path: string) => void
}

export interface FileSelectorModalProps {
    toFavorites?: () => void
    show: boolean
    loading: boolean
    path: string
    onHide: () => void
    page: Page<File>
    setSelectedFile: Function
    fetchFiles: (path: string, pageNumber: number, itemsPerPage: number) => void
    fetchFavorites: (pageNumber: number, itemsPerPage: number) => void
    disallowedPaths?: string[]
    onlyAllowFolders?: boolean
    canSelectFolders?: boolean
    isFavorites: boolean
    errorMessage?: string
    onErrorDismiss?: () => void
    navigate?: (path: string, pageNumber: number, itemsPerPage: number) => void
}

export interface FileSelectorBodyProps {
    entriesPerPageSelector?: React.ReactNode
    disallowedPaths?: string[]
    onlyAllowFolders?: boolean
    creatingFolder?: boolean
    canSelectFolders: boolean
    page: Page<File>
    fetchFiles: (path: string) => void
    setSelectedFile: Function
    createFolder?: () => void
    path: string
    omitRelativeFolders: boolean
}

export interface MoveCopyOperations {
    showFileSelector: (show: boolean) => void
    setDisallowedPaths: (paths: string[]) => void
    setFileSelectorCallback: (callback: Function) => void
    fetchPageFromPath: (path: string) => void
    fetchFilesPage: (path: string) => void
}

export interface FileOptionsProps {
    files: File[]
    fileOperations: FileOperation[]
}

export interface SortByDropdownProps {
    currentSelection: SortBy
    sortOrder: SortOrder
    onSelect: (sortorder: SortOrder, s: SortBy) => void
    asDropdown: boolean
    isSortedBy: boolean
}

export type PredicatedOperation = {predicate: (files: File[], cloud: Cloud) => boolean, onTrue: Operation, onFalse: Operation}
export type Operation = {text: string, onClick: (files: File[], cloud: Cloud) => void, disabled: (files: File[], cloud: Cloud) => boolean, icon?: string, color?: string}
export type FileOperation = Operation | PredicatedOperation

export interface ContextButtonsProps {
    createFolder: () => void
    showUploader: () => void
    inTrashFolder: boolean
    toHome: () => void
}


export interface DetailedFileSearchOperations {
    toggleHidden: () => void
    addExtensions: (ext: string[]) => void
    removeExtensions: (ext: string[]) => void
    toggleFolderAllowed: () => void
    toggleFilesAllowed: () => void
    addSensitivity: (sensitivity: SensitivityLevel) => void
    removeSensitivity: (sensitivity: SensitivityLevel[]) => void
    addTags: (tags: string[]) => void
    removeTags: (tags: string[]) => void
    setFilename: (filename: string) => void
    fetchPage: (request: AdvancedSearchRequest, callback?: () => void) => void
    setLoading: (loading: boolean) => void
    setTimes: (times: Times) => void
}

export type DetailedFileSearchStateProps = DetailedFileSearchReduxState & DetailedFileSearchOperations;

export type SensitivityLevel = "Private" | "Confidential" | "Sensitive";

export interface DetailedFileSearchReduxState {
    hidden: boolean
    allowFolders: boolean
    allowFiles: boolean
    fileName: string
    extensions: Set<string>
    tags: Set<string>
    sensitivities: Set<SensitivityLevel>
    createdBefore?: Date
    createdAfter?: Date
    modifiedBefore?: Date
    modifiedAfter?: Date
    error?: string
    loading: boolean
}

export type ContextBarProps = ContextButtonsProps & FileOptionsProps & {invalidPath: boolean}

export type PossibleTime = "createdBefore" | "createdAfter" | "modifiedBefore" | "modifiedAfter";

export interface ResponsiveTableColumnProps extends SortByDropdownProps {
    iconName?: "arrowUp" | "arrowDown"
    minWidth?: number
    notSticky?: boolean
}

export type AdvancedSearchRequest = {
    fileName?: string
    extensions?: String[]
    fileTypes: [FileType?, FileType?]
    createdAt?: {after?: number, before?: number}
    modifiedAt?: {after?: number, before?: number}
    sensitivity?: SensitivityLevel[]
    itemsPerPage?: number
    page?: number
};