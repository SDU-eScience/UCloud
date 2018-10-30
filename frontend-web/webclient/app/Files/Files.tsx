import * as React from "react";
import { connect } from "react-redux";
import { Cloud } from "Authentication/SDUCloudObject";
import Link from "ui-components/Link";
import { setUploaderVisible, setUploaderCallback } from "Uploader/Redux/UploaderActions";
import { dateToString } from "Utilities/DateUtilities";
import * as Pagination from "Pagination";
import { BreadCrumbs } from "ui-components/Breadcrumbs";
import * as UF from "UtilityFunctions";
import { KeyCode, ReduxObject } from "DefaultObjects";
import * as Actions from "./Redux/FilesActions";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import { FileSelectorModal } from "./FileSelector";
import { FileIcon, RefreshButton, Chevron } from "UtilityComponents";
import {
    FilesProps, SortBy, SortOrder, FilesStateProps, FilesOperations, File, FilesTableHeaderProps, FilenameAndIconsProps,
    FileOptionsProps, FilesTableProps, SortByDropdownProps, FileOperation, ContextButtonsProps, Operation, ContextBarProps,
    ResponsiveTableColumnProps
} from ".";
import { setPrioritizedSearch } from "Navigation/Redux/HeaderActions";
import {
    startRenamingFiles, AllFileOperations, isInvalidPathName, favoriteFileFromPage, getFilenameFromPath,
    isProject, toFileText, getParentPath, isDirectory, moveFile, createFolder, previewSupportedExtension
} from "Utilities/FileUtilities";
import InlinedRelative from "ui-components/InlinedRelative";
import { Button, OutlineButton, Icon, Box, Heading, Hide, Flex, Divider, Checkbox, Label, Input } from "ui-components";
import { Dispatch } from "redux";
import Table, { TableRow, TableCell, TableBody, TableHeaderCell, TableHeader } from "ui-components/Table";
import ClickableDropdown from "ui-components/ClickableDropdown";
import { Dropdown, DropdownContent } from "ui-components/Dropdown";
import DetailedFileSearch from "./DetailedFileSearch";

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
            page.items = page.items.filter(file => !file.isMockFolder);
            updateFiles(page);
        } else if (key === KeyCode.ENTER) {
            const fileNames = page.items.map(file => getFilenameFromPath(file.path));
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
        const checkbox = (
            <Label>
                <Checkbox
                    onClick={e => this.props.checkAllFiles(!!e.target.checked)}
                    checked={page.items.length === selectedFiles.length && page.items.length > 0}
                    onChange={e => e.stopPropagation()}
                />
            </Label>
        );
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
                disabled: () => false, icon: "rename", color: undefined
            },
            ...AllFileOperations(true, fileSelectorOperations, refetch, this.props.history)
        ];
        const customEntriesPerPage = (
            <>
                <RefreshButton loading={loading} onClick={refetch} className="float-right" />
                <Pagination.EntriesPerPageSelector
                    entriesPerPage={page.itemsPerPage}
                    content="Files per page"
                    onChange={itemsPerPage => fetchFiles(path, itemsPerPage, page.pageNumber, sortOrder, sortBy)}
                />
            </>
        );
        return (
            <Flex flexDirection="row">
                <Box width={[1, 13 / 16]}>
                    <Hide lg xl>
                        <ContextButtons createFolder={props.createFolder} showUploader={props.showUploader} />
                    </Hide>
                    <BreadCrumbs currentPath={path} navigate={newPath => navigate(newPath)} homeFolder={Cloud.homeFolder} />
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
                </Box>
                <Hide xs sm md width={3 / 16}>
                    <ContextBar
                        showUploader={props.showUploader}
                        fileOperations={fileOperations}
                        files={selectedFiles}
                        createFolder={() => props.createFolder()}
                    />
                    <DetailedFileSearch />
                </Hide>
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
            </Flex>);
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
                        <TableCell xs sm md>{sortingColumns ? UF.sortingColumnToValue(sortingColumns[0], file) : dateToString(file.modifiedAt)}</TableCell>
                        <TableCell xs sm md>{sortingColumns ? UF.sortingColumnToValue(sortingColumns[1], file) : UF.getOwnerFromAcls(file.acl)}</TableCell>
                        <TableCell textAlign="center">
                            <ClickableDropdown width="175px" trigger={<i className="fas fa-ellipsis-h" />}>
                                <FileOperations files={[file]} fileOperations={fileOperations} As={Box} ml="-17px" mr="-17px" pl="15px" />
                            </ClickableDropdown>
                        </TableCell>
                    </TableRow>)
                )}
            </TableBody>
        </Table>
    );

const ResponsiveTableColumn = ({ asDropdown, iconName, onSelect = (_1: SortOrder, _2: SortBy) => null, isSortedBy, currentSelection, sortOrder }: ResponsiveTableColumnProps) => (
    <TableHeaderCell xs sm md textAlign="left">
        <SortByDropdown isSortedBy={isSortedBy} onSelect={onSelect} asDropdown={asDropdown} currentSelection={currentSelection} sortOrder={sortOrder} />
        <Chevron name={iconName} />
    </TableHeaderCell>
);

const toSortOrder = (sortBy: SortBy, lastSort: SortBy, sortOrder: SortOrder) =>
    sortBy === lastSort ? (sortOrder === SortOrder.ASCENDING ? SortOrder.DESCENDING : SortOrder.ASCENDING) : SortOrder.ASCENDING;

const FilesTableHeader = ({ toSortingIcon = () => undefined, sortFiles = () => null, sortOrder, masterCheckbox, sortingColumns, onDropdownSelect, sortBy, customEntriesPerPage }: FilesTableHeaderProps) => (
    <TableHeader>
        <TableRow>
            <TableHeaderCell textAlign="left">
                <Flex>
                    <Box ml="9px">
                        {masterCheckbox}
                    </Box>
                    <Box ml="9px" onClick={() => sortFiles(toSortOrder(SortBy.PATH, sortBy, sortOrder), SortBy.PATH)}>
                        Filename
                    </Box>
                    <Box ml="auto" onClick={() => sortFiles(toSortOrder(SortBy.PATH, sortBy, sortOrder), SortBy.PATH)} />
                    <Chevron name={toSortingIcon(SortBy.PATH)} />
                </Flex>
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
    <Dropdown>
        {UF.prettierString(currentSelection)}
        <DropdownContent cursor="pointer">
            <Box ml="-16px" mr="-16px" pl="15px" onClick={() => onSelect(SortOrder.ASCENDING, currentSelection)} /* disabled={sortOrder === SortOrder.ASCENDING && isSortedBy} */>{UF.prettierString(SortOrder.ASCENDING)}</Box>
            <Box ml="-16px" mr="-16px" pl="15px" onClick={() => onSelect(SortOrder.DESCENDING, currentSelection)} /* disabled={sortOrder === SortOrder.DESCENDING && isSortedBy} */>{UF.prettierString(SortOrder.DESCENDING)}</Box>
            <Divider ml="-16px" mr="-16px" />
            {Object.keys(SortBy).filter(it => it !== currentSelection).map((sortByKey: SortBy, i) => (
                <Box ml="-16px" mr="-16px" pl="15px" key={i} onClick={() => onSelect(sortOrder, sortByKey)}>{UF.prettierString(sortByKey)}</Box>
            ))}
        </DropdownContent>
    </Dropdown>) : <>{UF.prettierString(currentSelection)}</>;

const ContextBar = ({ files, ...props }: ContextBarProps) => (
    <Box mt="65px">
        <ContextButtons showUploader={props.showUploader} createFolder={props.createFolder} />
        <FileOptions files={files} {...props} />
    </Box>
);

const ContextButtons = ({ createFolder, showUploader }: ContextButtonsProps) => (
    <Box pl="5px" pr="5px">
        <Button mt="3px" color="blue" fullWidth onClick={showUploader}>Upload Files</Button>
        <OutlineButton mt="3px" color="black" fullWidth onClick={createFolder}>New folder</OutlineButton>
    </Box>
);

const PredicatedCheckbox = ({ predicate, checked, onClick }) => predicate ? (
    <Label><Checkbox checked={checked} onClick={onClick} onChange={e => e.stopPropagation()} /></Label>
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

// FIXME Use own icons when available
const GroupIcon = ({ isProject }: { isProject: boolean }) => isProject ? (<i style={{ paddingLeft: "10px" }} className="fas fa-users" />) : null;

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
    const checkbox = <Box ml="9px" mt="4px"><PredicatedCheckbox predicate={hasCheckbox} checked={file.isChecked} onClick={(e) => onCheckFile(e.target.checked)} /></Box>
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
            <Flex>
                {checkbox}
                <Box ml="9px">
                    {icon}
                </Box>
                <Input
                    pb="6px"
                    pt="8px"
                    mt="-2px"
                    pl="0"
                    noBorder
                    width="100%"
                    autoFocus
                    onKeyDown={(e) => { if (!!onRenameFile) onRenameFile(e.keyCode, file, (e.target as any).value) }}
                />
                <Box>
                    <OutlineButton size="tiny" color="red" onClick={() => onRenameFile(KeyCode.ESC, file, "")}>Cancel</OutlineButton>
                </Box>
            </Flex>
        </TableCell > :
        <TableCell>
            <Flex>
                {checkbox}
                <Box ml="9px">
                    {nameLink}
                </Box>
                <Box>
                    <GroupIcon isProject={isProject(file)} />
                    <InlinedRelative top="3px" pl="7px">
                        <PredicatedFavorite predicate={!!onFavoriteFile && !file.path.startsWith(`${Cloud.homeFolder}Favorites`)} item={file} onClick={() => onFavoriteFile([file])} />
                    </InlinedRelative>
                </Box>
            </Flex>
        </TableCell>
};

const FileOptions = ({ files, fileOperations }: FileOptionsProps) => files.length ? (
    <Box>
        <Heading pl="5px" pt="5px">{toFileText(files)}</Heading>
        <FileOperations files={files} fileOperations={fileOperations} As={Box} pl="30px" />
    </Box>
) : null;

export const FileOperations = ({ files, fileOperations, As, ...props }) => files.length && fileOperations.length ?
    fileOperations.map((fileOp, i) => {
        let operation = fileOp;
        if (fileOp.predicate) {
            operation = fileOp.predicate(files, Cloud) ? operation.onTrue : operation.onFalse;
        }
        operation = operation as Operation;
        console.log(operation.icon)
        return !operation.disabled(files, Cloud) ? (
            <As key={i} onClick={() => (operation as Operation).onClick(files, Cloud)} {...props}>
                <Icon size={16} color={operation.color} name={operation.icon} />
                <span>{operation.text}</span>
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