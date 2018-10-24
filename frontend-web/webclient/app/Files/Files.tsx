import * as React from "react";
import { connect } from "react-redux";
import { Cloud } from "Authentication/SDUCloudObject";
import { Link } from "react-router-dom";
import {
    Dropdown as SDropdown, Icon as SIcon, Input as SInput, Grid as SGrid, Responsive as SResponsive, Checkbox as SCheckbox
} from "semantic-ui-react";
import { setUploaderVisible, setUploaderCallback } from "Uploader/Redux/UploaderActions";
import { dateToString } from "Utilities/DateUtilities";
import * as Pagination from "Pagination";
import { BreadCrumbs } from "Breadcrumbs/Breadcrumbs";
import * as UF from "UtilityFunctions";
import { KeyCode, ReduxObject } from "DefaultObjects";
import * as Actions from "./Redux/FilesActions";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import { FileSelectorModal } from "./FileSelector";
import { FileIcon, RefreshButton } from "UtilityComponents";
import {
    FilesProps, SortBy, SortOrder, FilesStateProps, FilesOperations, File, FilesTableHeaderProps, FilenameAndIconsProps,
    FileOptionsProps, FilesTableProps, SortByDropdownProps, FileOperation, ContextButtonsProps, Operation, ContextBarProps,
    PredicatedOperation, ResponsiveTableColumnProps
} from ".";
import { setPrioritizedSearch } from "Navigation/Redux/HeaderActions";
import {
    startRenamingFiles, AllFileOperations, isInvalidPathName, favoriteFileFromPage, getFilenameFromPath,
    isProject, toFileText, getParentPath, isDirectory, moveFile, createFolder, previewSupportedExtension
} from "Utilities/FileUtilities";
import { Dispatch } from "redux";
import { Button, OutlineButton, Icon, Box, Text, Heading } from "ui-components";
import InlinedRelative from "ui-components/InlinedRelative";
import Table, { TableRow, TableCell, TableBody, TableHeaderCell, TableHeader } from "ui-components/Table";
import { Dropdown, DropdownContent } from "ui-components/Dropdown";
import ClickableDropdown from "ui-components/ClickableDropdown";

class Files extends React.Component<FilesProps> {

    componentDidMount() {
        const { page, sortOrder, sortBy, history, prioritizeFileSearch, ...props } = this.props;
        props.setPageTitle();
        prioritizeFileSearch();
        props.setUploaderCallback(
            (path: string) => props.fetchFiles(path, page.itemsPerPage, page.pageNumber, sortOrder, sortBy)
        );
        if (!this.urlPath) { history.push(`/files/${Cloud.homeFolder}/`); }
        else { props.fetchFiles(this.urlPath, page.itemsPerPage, page.pageNumber, sortOrder, sortBy); }
    }

    get urlPath(): string { return this.props.match.params[0]; }

    onRenameFile = (key: number, file: File, name: string) => {
        const { path, fetchPageFromPath, updateFiles, page } = this.props;
        if (key === KeyCode.ESC) {
            const item = page.items.find(f => f.path === file.path);
            if (item !== undefined) item.beingRenamed = false;
            page.items = page.items.filter(it => !it.isMockFolder);
            updateFiles(page);
        } else if (key === KeyCode.ENTER) {
            const fileNames = page.items.map((it) => getFilenameFromPath(it.path));
            if (isInvalidPathName(name, fileNames)) return;
            const fullPath = `${UF.addTrailingSlash(path)}${name}`;
            if (file.isMockFolder) {
                createFolder(fullPath, Cloud,
                    () => fetchPageFromPath(fullPath, page.itemsPerPage, this.props.sortOrder, this.props.sortBy));
            } else {
                moveFile(file.path, fullPath, Cloud,
                    () => fetchPageFromPath(fullPath, page.itemsPerPage, this.props.sortOrder, this.props.sortBy));
            }
        }
    }

    shouldComponentUpdate(nextProps, _nextState): boolean {
        const { fetchFiles, page, loading, sortOrder, sortBy } = this.props;
        if (nextProps.path !== nextProps.match.params[0] && !loading) {
            fetchFiles(nextProps.match.params[0], page.itemsPerPage, 0, sortOrder, sortBy);
        }
        return true;
    }

    render() {
        const { page, path, loading, history, fetchFiles, checkFile, updateFiles, sortBy, sortOrder, leftSortingColumn,
            rightSortingColumn, setDisallowedPaths, setFileSelectorCallback, showFileSelector, ...props } = this.props;
        const selectedFiles = page.items.filter(file => file.isChecked);
        const checkbox = (<SCheckbox
            className="hidden-checkbox checkbox-margin"
            onClick={(_, d) => this.props.checkAllFiles(!!d.checked)}
            checked={page.items.length === selectedFiles.length && page.items.length > 0}
            indeterminate={selectedFiles.length < page.items.length && selectedFiles.length > 0}
            onChange={e => e.stopPropagation()}
        />);
        const refetch = () => fetchFiles(path, page.itemsPerPage, page.pageNumber, sortOrder, sortBy);
        const navigate = (path: string) => history.push(`/files/${path}`);
        const fetchPageFromPath = (path: string) => {
            this.props.fetchPageFromPath(path, page.itemsPerPage, sortOrder, sortBy);
            this.props.updatePath(getParentPath(path));
            navigate(getParentPath(path));
        };
        const fileSelectorOperations = { setDisallowedPaths, setFileSelectorCallback, showFileSelector, fetchPageFromPath };
        const favoriteFile = (files: File[]) => updateFiles(favoriteFileFromPage(page, files, Cloud));
        const fileOperations: FileOperation[] = [
            {
                text: "Rename", onClick: (files: File[]) => updateFiles(startRenamingFiles(files, page)),
                disabled: () => false, icon: "edit outline", color: undefined
            },
            ...AllFileOperations(true, fileSelectorOperations, refetch, this.props.history)
        ];
        const customEntriesPerPage = (
            <>
                <RefreshButton loading={loading} onClick={refetch} className="float-right" />
                <Pagination.EntriesPerPageSelector
                    className="items-per-page-padding float-right"
                    entriesPerPage={page.itemsPerPage}
                    content="Files per page"
                    onChange={itemsPerPage => fetchFiles(path, itemsPerPage, page.pageNumber, sortOrder, sortBy)}
                />
            </>
        );
        return (
            <SGrid>
                <SGrid.Column computer={13} tablet={16}>
                    <SGrid.Row>
                        <SResponsive
                            as={ContextButtons}
                            maxWidth={991}
                            createFolder={props.createFolder}
                            currentPath={path}
                            showUploader={props.showUploader}
                        />
                        <BreadCrumbs currentPath={path} navigate={newPath => navigate(newPath)} homeFolder={Cloud.homeFolder} />
                    </SGrid.Row>
                    <Pagination.List
                        loading={loading}
                        errorMessage={props.error}
                        onErrorDismiss={props.dismissError}
                        customEmptyPage={(<Heading>No files in current folder</Heading>)}
                        pageRenderer={page => (
                            <FilesTable
                                onFavoriteFile={favoriteFile}
                                fileOperations={fileOperations}
                                sortFiles={(sortOrder: SortOrder, sortBy: SortBy) => fetchFiles(path, page.itemsPerPage, page.pageNumber, sortOrder, sortBy)}
                                sortingIcon={(name: SortBy) => UF.getSortingIcon(sortBy, sortOrder, name)}
                                sortOrder={sortOrder}
                                sortingColumns={[leftSortingColumn, rightSortingColumn]}
                                refetchFiles={() => refetch()}
                                onDropdownSelect={(sortOrder: SortOrder, sortBy: SortBy, index: number) => fetchFiles(path, page.itemsPerPage, page.pageNumber, sortOrder, sortBy, index)}
                                masterCheckbox={checkbox}
                                onRenameFile={this.onRenameFile}
                                files={page.items}
                                sortBy={sortBy}
                                onCheckFile={(checked: boolean, file: File) => checkFile(checked, file.path)}
                                customEntriesPerPage={customEntriesPerPage}
                            />
                        )}
                        customEntriesPerPage
                        onItemsPerPageChanged={pageSize => fetchFiles(path, pageSize, 0, sortOrder, sortBy)}
                        page={page}
                        onPageChanged={pageNumber => fetchFiles(path, page.itemsPerPage, pageNumber, sortOrder, sortBy)}
                    />
                </SGrid.Column>
                <SResponsive as={SGrid.Column} computer={3} minWidth={992}>
                    <ContextBar
                        showUploader={props.showUploader}
                        fileOperations={fileOperations}
                        files={selectedFiles}
                        createFolder={() => props.createFolder()}
                    />
                </SResponsive>
                <FileSelectorModal
                    show={props.fileSelectorShown}
                    onHide={() => showFileSelector(false)}
                    path={props.fileSelectorPath}
                    fetchFiles={(path, pageNumber, itemsPerPage) => props.fetchSelectorFiles(path, pageNumber, itemsPerPage)}
                    loading={props.fileSelectorLoading}
                    errorMessage={props.fileSelectorError}
                    onErrorDismiss={props.onFileSelectorErrorDismiss}
                    onlyAllowFolders
                    canSelectFolders
                    page={props.fileSelectorPage}
                    setSelectedFile={props.fileSelectorCallback}
                    disallowedPaths={props.disallowedPaths}
                />
            </SGrid>);
    }
}

export const FilesTable = ({
    files, masterCheckbox, sortingIcon, sortFiles, onRenameFile, onCheckFile, sortingColumns, onDropdownSelect,
    fileOperations, sortOrder, onFavoriteFile, sortBy, customEntriesPerPage
}: FilesTableProps) => (
        <Table>
            <FilesTableHeader
                onDropdownSelect={onDropdownSelect}
                sortOrder={sortOrder}
                sortingColumns={sortingColumns}
                masterCheckbox={masterCheckbox}
                toSortingIcon={sortingIcon}
                sortFiles={sortFiles}
                sortBy={sortBy}
                customEntriesPerPage={customEntriesPerPage}
            />
            <TableBody>
                {files.map((file, i) => (
                    // FIXME Use :has() or parent selector when available
                    <TableRow style={file.isChecked ? { backgroundColor: "#EBF4FD" } : {}} key={i}>
                        <FilenameAndIcons
                            file={file}
                            onFavoriteFile={onFavoriteFile}
                            hasCheckbox={masterCheckbox != null}
                            onRenameFile={onRenameFile}
                            onCheckFile={checked => onCheckFile(checked, file)}
                        />
                        <SResponsive as={TableCell} minWidth={768}>{sortingColumns ? UF.sortingColumnToValue(sortingColumns[0], file) : dateToString(file.modifiedAt)}</SResponsive>
                        <SResponsive as={TableCell} minWidth={768}>{sortingColumns ? UF.sortingColumnToValue(sortingColumns[1], file) : UF.getOwnerFromAcls(file.acl)}</SResponsive>
                        <TableCell textAlign="center">
                            <ClickableDropdown width="175px" trigger={<SIcon name="ellipsis horizontal" />}>
                                <FileOperations files={[file]} fileOperations={fileOperations} As={SDropdown.Item} />
                            </ClickableDropdown>
                        </TableCell>
                    </TableRow>)
                )}
            </TableBody>
        </Table>
    );

const ResponsiveTableColumn = ({ asDropdown, iconName, onSelect = (_1: SortOrder, _2: SortBy) => null, isSortedBy, currentSelection, sortOrder, minWidth = undefined }: ResponsiveTableColumnProps) => (
    <SResponsive minWidth={minWidth} as={TableHeaderCell} textAlign="left">
        <SortByDropdown isSortedBy={isSortedBy} onSelect={onSelect} asDropdown={asDropdown} currentSelection={currentSelection} sortOrder={sortOrder} />
        <Chevron name={iconName} />
    </SResponsive>
);

const toSortOrder = (sortBy: SortBy, lastSort: SortBy, sortOrder: SortOrder) =>
    sortBy === lastSort ? (sortOrder === SortOrder.ASCENDING ? SortOrder.DESCENDING : SortOrder.ASCENDING) : SortOrder.ASCENDING;

const FilesTableHeader = ({ toSortingIcon = () => undefined, sortFiles = () => null, sortOrder, masterCheckbox, sortingColumns, onDropdownSelect, sortBy, customEntriesPerPage }: FilesTableHeaderProps) => (
    <TableHeader>
        <TableRow>
            <TableHeaderCell textAlign="left" onClick={() => sortFiles(toSortOrder(SortBy.PATH, sortBy, sortOrder), SortBy.PATH)}>
                {masterCheckbox}
                Filename
                <Chevron className="float-right" name={toSortingIcon(SortBy.PATH)} />
            </TableHeaderCell>
            {sortingColumns.map((sC, i) => (
                <ResponsiveTableColumn
                    key={i}
                    isSortedBy={sC === sortBy}
                    minWidth={768}
                    onSelect={(sortOrder: SortOrder, sortBy: SortBy) => { if (!!onDropdownSelect) onDropdownSelect(sortOrder, sortBy, i) }}
                    currentSelection={sC}
                    sortOrder={sortOrder}
                    asDropdown={!!onDropdownSelect}
                    iconName={toSortingIcon(sC)}
                />
            ))}
            <TableHeaderCell colSpan={3} textAlign="right">
                {customEntriesPerPage}
            </TableHeaderCell>
        </TableRow>
    </TableHeader>
);

const SortByDropdown = ({ currentSelection, sortOrder, onSelect, asDropdown, isSortedBy }: SortByDropdownProps) => asDropdown ? (
    <SDropdown simple text={UF.prettierString(currentSelection)}>
        <SDropdown.Menu>
            <SDropdown.Item text={UF.prettierString(SortOrder.ASCENDING)} onClick={() => onSelect(SortOrder.ASCENDING, currentSelection)} disabled={sortOrder === SortOrder.ASCENDING && isSortedBy} />
            <SDropdown.Item text={UF.prettierString(SortOrder.DESCENDING)} onClick={() => onSelect(SortOrder.DESCENDING, currentSelection)} disabled={sortOrder === SortOrder.DESCENDING && isSortedBy} />
            <SDropdown.Divider />
            {Object.keys(SortBy).filter(it => it !== currentSelection).map((sortByKey: SortBy, i) => (
                <SDropdown.Item key={i} onClick={() => onSelect(sortOrder, sortByKey)} text={UF.prettierString(sortByKey)} />
            ))}
        </SDropdown.Menu>
    </SDropdown>) : <>{UF.prettierString(currentSelection)}</>;

const ContextBar = ({ files, ...props }: ContextBarProps) => (
    <Box mt="65px">
        <ContextButtons showUploader={props.showUploader} createFolder={props.createFolder} />
        <FileOptions files={files} {...props} />
    </Box>
);

const ContextButtons = ({ createFolder, showUploader }: ContextButtonsProps) => (
    <div>
        <Button mt="3px" color="blue" fullWidth onClick={showUploader}>Upload Files</Button>
        <OutlineButton mt="3px" color="black" fullWidth onClick={createFolder}>New folder</OutlineButton>
        <Link to={`/filesearch`}><OutlineButton color="green" mt="3px" fullWidth>Advanced Search</OutlineButton></Link>
    </div>
);

const PredicatedCheckbox = ({ predicate, checked, onClick }) =>
    predicate ? (
        <SCheckbox checked={checked} onClick={onClick} className="checkbox-margin" onChange={e => e.stopPropagation()} />
    ) : null;

const PredicatedFavorite = ({ predicate, item, onClick }) =>
    predicate ? (
        <Icon
            size={15}
            color="blue"
            name={item.favorited ? "starFilled" : "starEmpty"}
            className={`${item.favorited ? "" : "file-data"}`}
            onClick={onClick}
        />
    ) : null;

const GroupIcon = ({ isProject }: { isProject: boolean }) => isProject ? (<SIcon className="group-icon-padding" name="users" />) : null;

const FileLink = ({ file, children }) => {
    if (isDirectory(file)) {
        return (<Link to={`/files/${file.path}`}>{children}</Link>);
    } else if (previewSupportedExtension(file.path)) {
        return (<Link to={`/filepreview/${file.path}`}>{children}</Link>);
    } else {
        return (<>{children}</>);
    }
}

function FilenameAndIcons({ file, size = "big", onRenameFile = () => null, onCheckFile = () => null, hasCheckbox = false, onFavoriteFile = () => null }: FilenameAndIconsProps) {
    const fileName = getFilenameFromPath(file.path);
    const checkbox = <PredicatedCheckbox predicate={hasCheckbox} checked={file.isChecked} onClick={(_, { checked }) => onCheckFile(checked)} />
    const icon = (
        <FileIcon
            color={isDirectory(file) ? "blue" : "grey"}
            name={UF.iconFromFilePath(file.path, file.fileType, Cloud.homeFolder)}
            size={size} link={file.link} shared={file.acl.length > 0}
        />
    );
    const nameLink = <FileLink file={file}>{icon}{fileName}</FileLink>;
    return file.beingRenamed ?
        <TableCell>
            <SInput
                defaultValue={fileName}
                onKeyDown={(e) => { if (!!onRenameFile) onRenameFile(e.keyCode, file, e.target.value) }}
                autoFocus
                transparent
                fluid
            >
                {checkbox}
                {icon}
                <input />
                <OutlineButton size="tiny" color="red" onClick={() => onRenameFile(KeyCode.ESC, file, "")}>Cancel</OutlineButton>
            </SInput>
        </TableCell> :
        <TableCell>
            {checkbox}
            {nameLink}
            <GroupIcon isProject={isProject(file)} />
            <InlinedRelative pl="7px">
                <PredicatedFavorite predicate={!!onFavoriteFile && !file.path.startsWith(`${Cloud.homeFolder}Favorites`)} item={file} onClick={() => onFavoriteFile([file])} />
            </InlinedRelative>
        </TableCell>
};

const FileOptions = ({ files, fileOperations }: FileOptionsProps) => files.length ? (
    <div>
        <Heading>{toFileText(files)}</Heading>
        <FileOperations files={files} fileOperations={fileOperations} As="div" />
    </div>
) : null;

export const FileOperations = ({ files, fileOperations, As }) => files.length && fileOperations.length ?
    fileOperations.map((fileOp, i) => {
        let operation = fileOp;
        if (fileOp.predicate) {
            operation = fileOp.predicate(files, Cloud) ? operation.onTrue : operation.onFalse;
        }
        operation = operation as Operation;
        return !operation.disabled(files, Cloud) ? (
            <As
                key={i}
                className="context-button-margin pointer-cursor"
                onClick={() => (operation as Operation).onClick(files, Cloud)}
            >
                <SIcon color={operation.color} name={operation.icon} />
                <span className="operation-text" style={{ fontSize: "16px" }}>{operation.text}</span>
            </As>
        ) : null;
    }) : null;

const mapStateToProps = ({ files }: ReduxObject): FilesStateProps => {
    const { page, loading, path, fileSelectorPage, fileSelectorPath, sortBy, sortOrder, fileSelectorShown,
        fileSelectorCallback, disallowedPaths, fileSelectorLoading, error, fileSelectorError, sortingColumns } = files;
    const favFilesCount = page.items.filter(file => file.favorited).length; // HACK to ensure changes to favorites are rendered.
    const renamingCount = page.items.filter(file => file.beingRenamed).length;
    const fileCount = page.items.length;
    return {
        error, fileSelectorError, page, loading, path, favFilesCount, fileSelectorPage, fileSelectorPath,
        fileSelectorShown, fileSelectorCallback, disallowedPaths, sortOrder, sortBy, fileCount, fileSelectorLoading,
        leftSortingColumn: sortingColumns[0], rightSortingColumn: sortingColumns[1], renamingCount
    }
};

function Chevron(props) {
    if (props.name === "chevron down") return (<Icon className="float-right" rotation={0} name="chevronDown" />);
    else if (props.name === "chevron up") return (<Icon className="float-right" rotation={180} name="chevronDown" />);
    return null;
}

const mapDispatchToProps = (dispatch: Dispatch): FilesOperations => ({
    prioritizeFileSearch: () => dispatch(setPrioritizedSearch("files")),
    onFileSelectorErrorDismiss: () => dispatch(Actions.setFileSelectorError()),
    dismissError: () => dispatch(Actions.setErrorMessage()),
    createFolder: () => dispatch(Actions.createFolder()),
    fetchFiles: async (path, itemsPerPage, pageNumber, sortOrder, sortBy, index?) => {
        dispatch(Actions.updatePath(path));
        dispatch(Actions.setLoading(true));
        if (index != null) dispatch(Actions.setSortingColumn(sortBy, index));
        dispatch(await Actions.fetchFiles(path, itemsPerPage, pageNumber, sortOrder, sortBy));
    },
    fetchPageFromPath: async (path, itemsPerPage, sortOrder, sortBy) => {
        dispatch(Actions.setLoading(true));
        dispatch(await Actions.fetchPageFromPath(path, itemsPerPage, sortOrder, sortBy));
    },
    updatePath: path => dispatch(Actions.updatePath(path)),
    fetchSelectorFiles: async (path, pageNumber, itemsPerPage) => dispatch(await Actions.fetchFileselectorFiles(path, pageNumber, itemsPerPage)),
    showFileSelector: open => dispatch(Actions.fileSelectorShown(open)),
    setFileSelectorCallback: callback => dispatch(Actions.setFileSelectorCallback(callback)),
    checkFile: (checked, path) => dispatch(Actions.checkFile(checked, path)),
    setPageTitle: () => dispatch(updatePageTitle("Files")),
    updateFiles: page => dispatch(Actions.updateFiles(page)),
    checkAllFiles: checked => dispatch(Actions.checkAllFiles(checked)),
    setDisallowedPaths: disallowedPaths => dispatch(Actions.setDisallowedPaths(disallowedPaths)),
    showUploader: () => dispatch(setUploaderVisible(true)),
    setUploaderCallback: callback => dispatch(setUploaderCallback(callback))
});

export default connect(mapStateToProps, mapDispatchToProps)(Files);