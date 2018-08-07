import { Page } from "Types";
import { History } from "history";
import { SemanticICONS } from "semantic-ui-react";
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
    // Ignore, used to ensure rerender.
    checkedFilesCount: number
    favFilesCount: number
}

export interface FilesOperations { // Redux operations
    prioritizeFileSearch: () => void
    onFileSelectorErrorDismiss: () => void
    dismissError: () => void
    fetchFiles: (path: string, itemsPerPage: number, pageNumber: number, sortOrder: SortOrder, sortBy: SortBy) => void
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
    files: File[]
    masterCheckbox?: React.ReactNode
    showFileSelector: (open: boolean) => void
    setFileSelectorCallback: Function
    setDisallowedPaths: (p: string[]) => void
    sortingIcon: (name: string) => SemanticICONS
    editFolderIndex: number
    sortFiles: (sortBy: SortBy) => void
    handleKeyDown: (a: number, b: boolean, c: string) => void
    onCheckFile: (c: boolean, f: File) => void
    fetchFiles: (p: string) => void
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
    projectNavigation: (s) => void
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