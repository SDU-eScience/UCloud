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
import { FileIcon, RefreshButton, Arrow } from "UtilityComponents";
import {
    FilesProps, SortBy, SortOrder, FilesStateProps, FilesOperations, File, FilesTableHeaderProps, FilenameAndIconsProps,
    FileOptionsProps, FilesTableProps, SortByDropdownProps, FileOperation, ContextButtonsProps, Operation, ContextBarProps,
    ResponsiveTableColumnProps
} from ".";
import { setPrioritizedSearch } from "Navigation/Redux/HeaderActions";
import {
    startRenamingFiles, AllFileOperations, isInvalidPathName, favoriteFileFromPage, getFilenameFromPath, isProject,
    toFileText, getParentPath, isDirectory, moveFile, createFolder, previewSupportedExtension, clearTrash, fileTablePage
} from "Utilities/FileUtilities";
import InlinedRelative from "ui-components/InlinedRelative";
import { Button, OutlineButton, Icon, Box, Hide, Flex, Divider, Checkbox, Label, Input, VerticalButtonGroup } from "ui-components";
import * as Heading from "ui-components/Heading";
import { Dispatch } from "redux";
import Table, { TableRow, TableCell, TableBody, TableHeaderCell, TableHeader } from "ui-components/Table";
import ClickableDropdown from "ui-components/ClickableDropdown";
import DetailedFileSearch from "./DetailedFileSearch";
import { TextSpan } from "ui-components/Text";
import { getQueryParamOrElse, RouterLocationProps } from "Utilities/URIUtilities";
import { allFilesHasAccessRight } from "Utilities/FileUtilities";
import { AccessRight } from "Types";

class Files extends React.Component<FilesProps> {
    componentDidMount() {
        const { page, sortOrder, sortBy, history, prioritizeFileSearch, ...props } = this.props;
        props.setPageTitle();
        prioritizeFileSearch();
        props.setUploaderCallback(
            (path: string) => props.fetchFiles(path, page.itemsPerPage, page.pageNumber, sortOrder, sortBy)
        );

        props.fetchFiles(this.urlPath, page.itemsPerPage, page.pageNumber, sortOrder, sortBy);
    }

    urlPathFromProps = (props: RouterLocationProps): string => getQueryParamOrElse(props, "path", Cloud.homeFolder);

    get urlPath(): string {
        return this.urlPathFromProps(this.props);
    }

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

    shouldComponentUpdate(nextProps: FilesProps, _nextState): boolean {
        const { fetchFiles, page, loading, sortOrder, sortBy } = this.props;
        const nextPath = this.urlPathFromProps(nextProps);
        if (nextProps.path !== nextPath && !loading) {
            fetchFiles(nextPath, page.itemsPerPage, 0, sortOrder, sortBy);
        }
        return true;
    }

    render() {
        const { page, path, loading, history, fetchFiles, checkFile, updateFiles, sortBy, sortOrder, leftSortingColumn,
            rightSortingColumn, setDisallowedPaths, setFileSelectorCallback, showFileSelector, ...props } = this.props;
        const selectedFiles = page.items.filter(file => file.isChecked);

        const masterCheckbox = (
            <Label>
                <Checkbox
                    onClick={e => this.props.checkAllFiles(!!e.target.checked)}
                    checked={page.items.length === selectedFiles.length && page.items.length > 0}
                    onChange={e => e.stopPropagation()}
                />
            </Label>
        );

        const refetch = () => fetchFiles(path, page.itemsPerPage, page.pageNumber, sortOrder, sortBy);
        const navigate = (path: string) => history.push(fileTablePage(path)); // FIXME Is this necessary?

        const fetchPageFromPath = (path: string) => {
            this.props.fetchPageFromPath(path, page.itemsPerPage, sortOrder, sortBy);
            this.props.updatePath(getParentPath(path)); // FIXME Could these be handled by shouldComponentUpdate?
            navigate(getParentPath(path)); // FIXME Could these be handled by shouldComponentUpdate?
        };

        const fileSelectorOperations = { setDisallowedPaths, setFileSelectorCallback, showFileSelector, fetchPageFromPath };
        const favoriteFile = (files: File[]) => updateFiles(favoriteFileFromPage(page, files, Cloud));
        const fileOperations: FileOperation[] = [
            {
                text: "Rename",
                onClick: files => updateFiles(startRenamingFiles(files, page)),
                disabled: (files: File[]) => !allFilesHasAccessRight(AccessRight.WRITE, files),
                icon: "rename",
                color: undefined
            },
            ...AllFileOperations(true, fileSelectorOperations, refetch, this.props.history)
        ];
        const customEntriesPerPage = (
            <>
                <Pagination.EntriesPerPageSelector
                    entriesPerPage={page.itemsPerPage}
                    content="Files per page"
                    onChange={itemsPerPage => fetchFiles(path, itemsPerPage, page.pageNumber, sortOrder, sortBy)}
                />
                <RefreshButton loading={loading} onClick={refetch} />
            </>
        );
        return (
            <Flex flexDirection="row">
                <Box width={[1, 13 / 16]}>
                    <Hide lg xl>
                        {!props.invalidPath ? (
                            <ContextButtons
                                createFolder={props.createFolder}
                                showUploader={props.showUploader}
                                inTrashFolder={UF.addTrailingSlash(path) === Cloud.trashFolder}
                                toHome={() => navigate(Cloud.homeFolder)}
                            />
                        ) : null}
                    </Hide>
                    <BreadCrumbs currentPath={path} navigate={newPath => navigate(newPath)} homeFolder={Cloud.homeFolder} />
                    <Pagination.List
                        loading={loading}
                        errorMessage={props.error}
                        onErrorDismiss={props.dismissError}
                        customEmptyPage={(<Heading.h3>No files in current folder</Heading.h3>)}
                        pageRenderer={page => (
                            <FilesTable
                                onFavoriteFile={favoriteFile}
                                fileOperations={fileOperations}
                                sortFiles={(sortOrder, sortBy) => fetchFiles(path, page.itemsPerPage, page.pageNumber, sortOrder, sortBy)}
                                sortingIcon={name => UF.getSortingIcon(sortBy, sortOrder, name)}
                                sortOrder={sortOrder}
                                sortingColumns={[leftSortingColumn, rightSortingColumn]}
                                refetchFiles={() => refetch()}
                                onDropdownSelect={(sortOrder, sortBy, index) => fetchFiles(path, page.itemsPerPage, page.pageNumber, sortOrder, sortBy, index)}
                                masterCheckbox={masterCheckbox}
                                onRenameFile={this.onRenameFile}
                                files={page.items}
                                sortBy={sortBy}
                                onCheckFile={(checked, file) => checkFile(checked, file.path)}
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
                    {!props.invalidPath ?
                        <Box pl="5px" pr="5px">
                            <ContextBar
                                invalidPath={props.invalidPath}
                                showUploader={props.showUploader}
                                fileOperations={fileOperations}
                                files={selectedFiles}
                                inTrashFolder={UF.addTrailingSlash(path) === Cloud.trashFolder}
                                createFolder={() => props.createFolder()}
                                toHome={() => navigate(Cloud.homeFolder)}
                            />
                            <DetailedFileSearch />
                        </Box> : null
                    }
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

const ResponsiveTableColumn = ({
    asDropdown,
    iconName,
    onSelect = (_1: SortOrder, _2: SortBy) => null,
    isSortedBy,
    currentSelection,
    sortOrder
}: ResponsiveTableColumnProps) => (
        <TableHeaderCell width="17.5%" xs sm md textAlign="left">
            <Flex>
                <SortByDropdown
                    isSortedBy={isSortedBy}
                    onSelect={onSelect}
                    asDropdown={asDropdown}
                    currentSelection={currentSelection}
                    sortOrder={sortOrder} />
                <Box ml="auto" />
                <Arrow name={iconName} />
            </Flex>
        </TableHeaderCell>
    );

const toSortOrder = (sortBy: SortBy, lastSort: SortBy, sortOrder: SortOrder) =>
    sortBy === lastSort ? (sortOrder === SortOrder.ASCENDING ? SortOrder.DESCENDING : SortOrder.ASCENDING) : SortOrder.ASCENDING;

const FilesTableHeader = ({
    toSortingIcon = () => undefined,
    sortFiles = () => null,
    sortOrder,
    masterCheckbox,
    sortingColumns,
    onDropdownSelect,
    sortBy,
    customEntriesPerPage
}: FilesTableHeaderProps) => (
        <TableHeader>
            <TableRow>
                <TableHeaderCell width="50%" textAlign="left">
                    <Flex>
                        <Box ml="9px">
                            {masterCheckbox}
                        </Box>
                        <Box ml="9px" onClick={() => sortFiles(toSortOrder(SortBy.PATH, sortBy, sortOrder), SortBy.PATH)}>
                            Filename
                    </Box>
                        <Box ml="auto" onClick={() => sortFiles(toSortOrder(SortBy.PATH, sortBy, sortOrder), SortBy.PATH)} />
                        <Arrow name={toSortingIcon(SortBy.PATH)} />
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
                <TableHeaderCell width="15%" colSpan={3} textAlign="right">
                    {customEntriesPerPage}
                </TableHeaderCell>
            </TableRow>
        </TableHeader>
    );

const SortByDropdown = ({ currentSelection, sortOrder, onSelect, asDropdown, isSortedBy }: SortByDropdownProps) => asDropdown ? (
    <ClickableDropdown trigger={<TextSpan>{UF.prettierString(currentSelection)}</TextSpan>} chevron>
        <Box ml="-16px" mr="-16px" pl="15px"
            hidden={sortOrder === SortOrder.ASCENDING && isSortedBy}
            onClick={() => onSelect(SortOrder.ASCENDING, currentSelection)}
        >
            {UF.prettierString(SortOrder.ASCENDING)}
        </Box>
        <Box ml="-16px" mr="-16px" pl="15px"
            onClick={() => onSelect(SortOrder.DESCENDING, currentSelection)}
            hidden={sortOrder === SortOrder.DESCENDING && isSortedBy}
        >
            {UF.prettierString(SortOrder.DESCENDING)}
        </Box>
        <Divider ml="-16px" mr="-16px" />
        {Object.keys(SortBy).map((sortByKey: SortBy, i) => (
            <Box ml="-16px" mr="-16px" pl="15px" key={i}
                onClick={() => onSelect(sortOrder, sortByKey)}
                hidden={sortByKey === currentSelection || sortByKey === SortBy.PATH}
            >
                {UF.prettierString(sortByKey)}
            </Box>
        ))}
    </ClickableDropdown>) : <>{UF.prettierString(currentSelection)}</>;

const ContextBar = ({ files, ...props }: ContextBarProps) => (
    <Box mt="65px">
        <ContextButtons toHome={props.toHome} inTrashFolder={props.inTrashFolder} showUploader={props.showUploader} createFolder={props.createFolder} />
        <FileOptions files={files} {...props} />
    </Box>
);

const ContextButtons = ({ createFolder, showUploader, inTrashFolder, toHome }: ContextButtonsProps) => (
    <VerticalButtonGroup>
        <Button color="blue" onClick={showUploader}>Upload Files</Button>
        <OutlineButton color="blue" onClick={createFolder}>New folder</OutlineButton>
        {inTrashFolder ?
            <Button color="red"
                onClick={() => clearTrash(Cloud, () => toHome())}
            >
                Clear trash
                </Button> : null}
    </VerticalButtonGroup>
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
            onClick={() => onClick([item])}
        />
    ) : null;

// FIXME Use own icons when available
const GroupIcon = ({ isProject }: { isProject: boolean }) => isProject ? (<i style={{ paddingLeft: "10px", verticalAlign: "middle" }} className="fas fa-users" />) : null;

const FileLink = ({ file, children }) => {
    if (isDirectory(file)) {
        return (<Link to={fileTablePage(file.path)}>{children}</Link>);
    } else if (previewSupportedExtension(file.path)) {
        return (<Link to={`/files/preview/${file.path}`}>{children}</Link>);
    } else {
        return (<>{children}</>);
    }
}

function FilenameAndIcons({ file, size = "big", onRenameFile = () => null, onCheckFile = () => null, hasCheckbox = false, onFavoriteFile }: FilenameAndIconsProps) {
    const fileName = getFilenameFromPath(file.path);
    const checkbox = <Box ml="9px"><PredicatedCheckbox predicate={hasCheckbox} checked={file.isChecked} onClick={e => onCheckFile(e.target.checked)} /></Box>
    const icon = (
        <Box mr="5px">
            <FileIcon
                color={isDirectory(file) ? "blue" : "gray"}
                name={UF.iconFromFilePath(file.path, file.fileType, Cloud.homeFolder)}
                size={size} link={file.link} shared={(file.acl !== undefined ? file.acl.length : 0) > 0}
            />
        </Box>
    );
    const nameLink = (<FileLink file={file}>{icon}{fileName}</FileLink>);
    return file.beingRenamed ?
        <TableCell width="50%">
            <Flex>
                {checkbox}
                <Box ml="5px" pr="5px" />
                {icon}
                <Input
                    placeholder={getFilenameFromPath(file.path)}
                    pb="6px"
                    pt="8px"
                    mt="-2px"
                    pl="0"
                    noBorder
                    type="text"
                    width="100%"
                    autoFocus
                    onKeyDown={e => { if (!!onRenameFile) onRenameFile(e.keyCode, file, (e.target as any).value) }}
                />
                <Box>
                    <OutlineButton size="tiny" color="red" onClick={() => onRenameFile(KeyCode.ESC, file, "")}>Cancel</OutlineButton>
                </Box>
            </Flex>
        </TableCell > :
        <TableCell width="50%">
            <Flex flexDirection="row">
                {checkbox}
                <Box ml="5px" pr="5px" />
                {nameLink}
                <GroupIcon isProject={isProject(file)} />
                <InlinedRelative pl="7px">
                    <PredicatedFavorite predicate={!!onFavoriteFile && !file.path.startsWith(`${Cloud.homeFolder}Favorites`)} item={file} onClick={onFavoriteFile} />
                </InlinedRelative>
            </Flex>
        </TableCell>
};

const FileOptions = ({ files, fileOperations }: FileOptionsProps) => files.length ? (
    <Box mb="13px">
        <Heading.h5 pl="20px" pt="5px" pb="8px">{toFileText(files)}</Heading.h5>
        <FileOperations files={files} fileOperations={fileOperations} As={Box} pl="20px" />
    </Box>
) : null;

export const FileOperations = ({ files, fileOperations, As, ...props }) => files.length && fileOperations.length ?
    fileOperations.map((fileOp, i) => {
        let operation = fileOp;
        if (fileOp.predicate) {
            operation = fileOp.predicate(files, Cloud) ? operation.onTrue : operation.onFalse;
        }
        operation = operation as Operation;
        return !operation.disabled(files, Cloud) ? (
            <As key={i} onClick={() => (operation as Operation).onClick(files, Cloud)} {...props}>
                <Icon size={16} mr="1em" color={operation.color} name={operation.icon} />
                <span>{operation.text}</span>
            </As>
        ) : null;
    }) : null;

const mapStateToProps = ({ files }: ReduxObject): FilesStateProps => {
    const { page, loading, path, fileSelectorPage, fileSelectorPath, sortBy, sortOrder, fileSelectorShown, invalidPath,
        fileSelectorCallback, disallowedPaths, fileSelectorLoading, error, fileSelectorError, sortingColumns } = files;
    const favFilesCount = page.items.filter(file => file.favorited).length; // HACK to ensure changes to favorites are rendered.
    const renamingCount = page.items.filter(file => file.beingRenamed).length;
    const fileCount = page.items.length;
    return {
        error, fileSelectorError, page, loading, path, favFilesCount, fileSelectorPage, fileSelectorPath,
        fileSelectorShown, fileSelectorCallback, disallowedPaths, sortOrder, sortBy, fileCount, fileSelectorLoading,
        leftSortingColumn: sortingColumns[0], rightSortingColumn: sortingColumns[1], renamingCount, invalidPath
    }
};

const mapDispatchToProps = (dispatch: Dispatch): FilesOperations => ({
    prioritizeFileSearch: () => dispatch(setPrioritizedSearch("files")),
    onFileSelectorErrorDismiss: () => dispatch(Actions.setFileSelectorError({})),
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