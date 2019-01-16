import { Page } from "Types";
import Cloud from "Authentication/lib";
import * as React from "react";
import PromiseKeeper from "PromiseKeeper";
import { Activity } from "Activity";
import { IconName } from "ui-components/Icon";
import { ComponentWithPage, ResponsiveReduxObject, SensitivityLevelMap } from "DefaultObjects";
import { Times } from "./Redux/DetailedFileSearchActions";
import { RouterLocationProps } from "Utilities/URIUtilities";
import { ThemeColor } from "ui-components/theme";

export enum SortOrder {
    ASCENDING = "ASCENDING",
    DESCENDING = "DESCENDING"
}

export type FileType = "FILE" | "DIRECTORY" | "FAVFOLDER" | "TRASHFOLDER" | "RESULTFOLDER";
export interface File {
    fileType: FileType
    path: string
    createdAt: number
    modifiedAt: number
    ownerName: string
    size: number
    acl?: Acl[]
    favorited?: boolean
    sensitivityLevel: SensitivityLevelMap
    isChecked?: boolean
    beingRenamed?: boolean
    link: boolean
    annotations: string[]
    isMockFolder?: boolean
    content?: any
}

export interface Acl {
    entity: string
    rights: string[]
    group: boolean
}

export enum SortBy {
    TYPE = "TYPE",
    PATH = "PATH",
    CREATED_AT = "CREATED_AT",
    MODIFIED_AT = "MODIFIED_AT",
    SIZE = "SIZE",
    ACL = "ACL",
    FAVORITED = "FAVORITED",
    SENSITIVITY = "SENSITIVITY"
}

export interface FilesProps extends FilesStateProps, FilesOperations, RouterLocationProps { }

export interface MockedTableProps {
    onCreateFolder: (a: number, c: number) => void
    creatingFolder: boolean
}

export interface FilesStateProps { // Redux Props
    path: string
    page: Page<File>
    loading: boolean
    fileSelectorShown: boolean
    fileSelectorLoading: boolean
    disallowedPaths: string[]
    fileSelectorCallback: Function
    fileSelectorPath: string
    fileSelectorPage: Page<File>
    sortBy: SortBy
    sortOrder: SortOrder
    error?: string
    fileSelectorError?: string
    favFilesCount: number
    renamingCount: number
    fileCount: number
    leftSortingColumn: SortBy
    rightSortingColumn: SortBy
    invalidPath: boolean
    responsiveState?: ResponsiveReduxObject
}

export interface FilesOperations { // Redux operations
    prioritizeFileSearch: () => void
    onFileSelectorErrorDismiss: () => void
    dismissError: () => void
    fetchFiles: (path: string, itemsPerPage: number, pageNumber: number, sortOrder: SortOrder, sortBy: SortBy, index?: number) => void
    fetchPageFromPath: (path: string, itemsPerPage: number, sortOrder: SortOrder, sortBy: SortBy) => void;
    fetchSelectorFiles: (path: string, pageNumber: number, itemsPerPage: number) => void
    setFileSelectorCallback: (callback: Function) => void
    checkFile: (checked: boolean, path: string) => void
    setPageTitle: () => void
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
    onFileSelect: (file: { path: string }) => void
    path: string
    isRequired?: boolean
    canSelectFolders?: boolean
    onlyAllowFolders?: boolean
    remove?: () => void
}

export interface FileSelectorState {
    promises: PromiseKeeper
    path: string
    error?: string
    loading: boolean
    page: Page<File>
    modalShown: boolean
    creatingFolder: boolean
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
    customEntriesPerPage?: React.ReactNode
    notStickyHeader?: boolean
}

export interface CreateFolderProps {
    creatingNewFolder: boolean
    onCreateFolder: (key: number, name: string) => void
}

export interface FilesTableHeaderProps {
    toSortingIcon?: (s: SortBy) => "arrowUp" | "arrowDown" | undefined
    sortFiles?: (sortOrder: SortOrder, sortBy: SortBy) => void
    sortOrder: SortOrder
    sortBy: SortBy
    masterCheckbox?: React.ReactNode
    sortingColumns: SortBy[]
    onDropdownSelect?: (sortOrder: SortOrder, sortBy: SortBy, index: number) => void
    customEntriesPerPage?: React.ReactNode
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
    show: boolean
    loading: boolean
    path: string
    onHide: () => void
    page: Page<File>
    setSelectedFile: Function
    fetchFiles: (path: string, pageNumber: number, itemsPerPage: number) => void
    disallowedPaths?: string[]
    onlyAllowFolders?: boolean
    canSelectFolders?: boolean
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
}

export interface FileListProps {
    files: File[]
    setSelectedFile: Function
    fetchFiles: Function
    canSelectFolders: boolean
}

export interface MoveCopyOperations {
    showFileSelector: (show: boolean) => void
    setDisallowedPaths: (paths: string[]) => void
    setFileSelectorCallback: (callback?: Function) => void
    fetchPageFromPath: (path: string) => void
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

export interface MobileButtonsProps {
    file: File
    fileOperations: FileOperation[]
}

export type PredicatedOperation = { predicate: (files: File[], cloud: Cloud) => boolean, onTrue: Operation, onFalse: Operation }
export type Operation = { text: string, onClick: (files: File[], cloud: Cloud) => void, disabled: (files: File[], cloud: Cloud) => boolean, icon?: string, color?: string }
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
    fetchPage: (request: AdvancedSearchRequest, callback?: Function) => void
    setLoading: (loading: boolean) => void
    setTimes: (times: Times) => void
    setError: (error?: string) => void
}

export type DetailedFileSearchProps = DetailedFileSearchReduxState & DetailedFileSearchOperations;

export type SensitivityLevel = "Open Access" | "Confidential" | "Sensitive";

export interface DetailedFileSearchReduxState extends ComponentWithPage<File> {
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
}

export type ContextBarProps = ContextButtonsProps & FileOptionsProps & { invalidPath: boolean }

export type PossibleTime = "createdBefore" | "createdAfter" | "modifiedBefore" | "modifiedAfter";

export interface ResponsiveTableColumnProps extends SortByDropdownProps {
    iconName?: "arrowUp" | "arrowDown"
    minWidth?: number
    notSticky?: boolean
}

export interface FileInfoState {
    activity: Page<Activity>
}

export type AdvancedSearchRequest = {
    fileName?: string
    extensions?: Array<String>
    fileTypes: [FileType?, FileType?]
    createdAt?: { after?: number, before?: number }
    modifiedAt?: { after?: number, before?: number }
    sensitivity?: Array<SensitivityLevel>
    itemsPerPage?: number
    page?: number
};