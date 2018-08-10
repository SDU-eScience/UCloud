import { Page } from "Types";
import { History } from "history";
import { SemanticICONS, SemanticSIZES, ButtonProps, ModalProps } from "semantic-ui-react";
import { match } from "react-router-dom";

export enum SortOrder {
    ASCENDING = "ASCENDING",
    DESCENDING = "DESCENDING"
}

export interface File {
    type: string
    path: string
    createdAt: number
    modifiedAt: number
    ownerName: string
    size: number
    acl: Array<Acl>
    favorited: boolean
    sensitivityLevel: string
    isChecked?: boolean
    link: boolean
    annotations: string[]
}

export interface Acl {
    entity: Entity
    right: string
}

export interface Entity {
    type: string
    name: string
    displayName: string
    zone: string
}

export enum SortBy {
    TYPE = "TYPE",
    PATH = "PATH",
    CREATED_AT = "CREATED_AT",
    MODIFIED_AT = "MODIFIED_AT",
    SIZE = "SIZE",
    ACL = "ACL",
    FAVORITED = "FAVORITED",
    SENSITIVITY = "SENSITIVITY",
    ANNOTATION = "ANNOTATION"
}

export interface FilesProps extends FilesStateProps, FilesOperations {
    match: match<string[]>
    history: History
}

export interface MockedTableProps {
    handleKeyDown: (a, b, c) => void
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
    creatingFolder: boolean
    editFileIndex: number
    error: string
    fileSelectorError: string
    checkedFilesCount: number
    favFilesCount: number
    leftSortingColumn: SortBy
    rightSortingColumn: SortBy
}

export interface FilesOperations { // Redux operations
    prioritizeFileSearch: () => void
    onFileSelectorErrorDismiss: () => void
    dismissError: () => void
    fetchFiles: (path: string, itemsPerPage: number, pageNumber: number, sortOrder: SortOrder, sortBy: SortBy, sortingColumns: [SortBy, SortBy]) => void
    fetchPageFromPath: (path: string, itemsPerPage: number, sortOrder: SortOrder, sortBy: SortBy) => void;
    fetchSelectorFiles: (path: string, pageNumber: number, itemsPerPage: number) => void
    setFileSelectorCallback: (callback: Function) => void
    checkFile: (checked: boolean, page: Page<File>, newFile: File) => void
    setPageTitle: () => void
    updateFiles: (files: Page<File>) => void
    updatePath: (path: string) => void
    showFileSelector: (open: boolean) => void
    setDisallowedPaths: (disallowedPaths: string[]) => void
    setCreatingFolder: (creating: boolean) => void
    setEditingFileIndex: (index: number) => void
    resetFolderEditing: () => void
}

export interface FileSelectorProps {
    allowUpload?: boolean
    onFileSelect: Function
    uppy?: any
    path: string
    isRequired: boolean
    canSelectFolders?: boolean
    onlyAllowFolders?: boolean
    remove?: Function
}

export interface FileSelectorState {
    promises: any
    path: string
    loading: boolean
    page: Page<File>
    modalShown: boolean
    breadcrumbs: { path: string, actualPath: string }[]
    uppyOnUploadSuccess: Function
    creatingFolder: boolean
}

export interface FilesTableProps {
    onDropdownSelect?: (s: SortBy, a: [SortBy, SortBy]) => void
    sortingColumns?: [SortBy, SortBy]
    files: File[]
    masterCheckbox?: React.ReactNode
    showFileSelector: (open: boolean) => void
    setFileSelectorCallback: (c: Function) => void
    setDisallowedPaths: (p: string[]) => void
    sortingIcon: (name: string) => SemanticICONS
    editFolderIndex: number
    sortFiles: (sortBy: SortBy) => void
    handleKeyDown: (a: number, b: boolean, c: string) => void
    onCheckFile: (c: boolean, f: File) => void
    refetchFiles: () => void
    startEditFile: (i: number) => void
    projectNavigation: (p: string) => void
    creatingNewFolder: boolean
    allowCopyAndMove: boolean
    onFavoriteFile: (p: string) => void
    fetchPageFromPath: (p: string) => void
}

export interface EditOrCreateProjectButtonProps {
    file: File
    disabled: boolean
    projectNavigation: (s: string) => void
}

export interface CreateFolderProps {
    creatingNewFolder: boolean
    handleKeyDown: (a: number, b: boolean, c: string) => void
}

export interface PredicatedDropDownItemProps {
    predicate: boolean
    content: string
    onClick: () => void
}

export interface FilesTableHeaderProps {
    sortingIcon?: (s: SortBy) => SemanticICONS
    sortFiles?: (s: SortBy) => void
    masterCheckbox?: React.ReactNode
    sortingColumns?: [SortBy, SortBy]
    onDropdownSelect?: (a: SortBy, s: [SortBy, SortBy]) => void
}

export interface FilenameAndIconsProps {
    file: File
    beingRenamed: boolean
    hasCheckbox: boolean
    size?: SemanticSIZES
    onKeyDown: (a: number, b: boolean, c: string) => void
    onCheckFile: (c: boolean, f: File) => void
    onFavoriteFile: (p: string) => void
}

export interface FileSelectorModalProps {
    show, loading: boolean
    path: string
    onHide: (event: React.MouseEvent<HTMLButtonElement | HTMLElement>, data: ButtonProps | ModalProps) => void
    page: Page<File>
    setSelectedFile: Function
    fetchFiles: (path: string, pageNumber: number, itemsPerPage: number) => void
    disallowedPaths?: string[]
    onlyAllowFolders?: boolean
    canSelectFolders?: boolean
    creatingFolder?: boolean
    handleKeyDown?: Function
    createFolder?: Function
    errorMessage?: string
    onErrorDismiss?: () => void
    navigate?: (path, pageNumber, itemsPerPage) => void
}

export interface FileSelectorBodyProps {
    disallowedPaths?: string[]
    onlyAllowFolders?: boolean
    creatingFolder?: boolean
    canSelectFolders?: boolean
    page: Page<File>
    fetchFiles: (path: string) => void
    handleKeyDown?: Function
    setSelectedFile: Function
    createFolder?: Function
    path: string
}

export interface FileListProps {
    files: File[]
    setSelectedFile, fetchFiles: Function
    canSelectFolders: boolean
}

export interface MoveCopyOperations {
    showFileSelector: (show: boolean) => void
    setDisallowedPaths: (paths: string[]) => void
    setFileSelectorCallback: (callback: Function) => void
    fetchPageFromPath: (path: string) => void
}

export interface FileOptionsProps extends MoveCopyOperations {
    files: File[]
    rename: () => void
    refetch: () => void
    projectNavigation: (str: string) => void
}

export interface SortByDropdownProps {
    currentSelection: SortBy
    sortOrder: SortOrder
    onSortOrderChange: (s: SortOrder) => void
    onSelect: (s: SortBy) => void
}