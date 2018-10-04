import * as React from "react";
import { connect } from "react-redux";
import { Cloud } from "Authentication/SDUCloudObject";
import { Link } from "react-router-dom";
import { Dropdown, Button, Icon, Table, Header, Input, Grid, Responsive, Checkbox, Divider } from "semantic-ui-react";
import { setUploaderVisible, setUploaderCallback } from "Uploader/Redux/UploaderActions";
import { dateToString } from "Utilities/DateUtilities";
import * as Pagination from "Pagination";
import { BreadCrumbs } from "Breadcrumbs/Breadcrumbs";
import * as UF from "UtilityFunctions";
import { KeyCode, ReduxObject } from "DefaultObjects";
import * as Actions from "./Redux/FilesActions";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import { FileSelectorModal } from "./FileSelector";
import { FileIcon } from "UtilityComponents";
import { Page } from "Types";
import {
    FilesProps, SortBy, SortOrder, FilesStateProps, FilesOperations, File, FilesTableHeaderProps, FilenameAndIconsProps,
    FileOptionsProps, FilesTableProps, SortByDropdownProps, FileOperation, ContextButtonsProps, Operation, ContextBarProps,
    PredicatedOperation, ResponsiveTableColumnProps
} from ".";
import { setPrioritizedSearch } from "Navigation/Redux/HeaderActions";
import {
    startRenamingFiles, AllFileOperations, newMockFolder, isInvalidPathName, favoriteFileFromPage, getFilenameFromPath,
    isProject, toFileText, getParentPath, isDirectory, moveFile, createFolder, previewSupportedExtension
} from "Utilities/FileUtilities";
import { Dispatch } from "redux";

class Files extends React.Component<FilesProps> {

    componentDidMount() {
        const { page, fetchFiles, sortOrder, sortBy, history, setPageTitle, prioritizeFileSearch } = this.props;
        setPageTitle();
        prioritizeFileSearch();
        this.props.setUploaderCallback(
            (path: string) => fetchFiles(path, page.itemsPerPage, page.pageNumber, sortOrder, sortBy)
        );
        if (!this.urlPath) { history.push(`/files/${Cloud.homeFolder}/`); }
        else { fetchFiles(this.urlPath, page.itemsPerPage, page.pageNumber, sortOrder, sortBy); }
    }

    get urlPath(): string { return this.props.match.params[0]; }

    newFolder() {
        let { page, updateFiles } = this.props;
        page.items = [newMockFolder()].concat([...page.items.filter(it => !it.isMockFolder)]);
        updateFiles(page);
    }

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
            rightSortingColumn, setDisallowedPaths, setFileSelectorCallback, showFileSelector, error } = this.props;
        const selectedFiles = page.items.filter(file => file.isChecked);
        const checkbox = (<Checkbox
            className="hidden-checkbox checkbox-margin"
            onClick={(_, d) => this.props.checkAllFiles(!!d.checked)}
            checked={page.items.length === selectedFiles.length && page.items.length > 0}
            indeterminate={selectedFiles.length < page.items.length && selectedFiles.length > 0}
            onChange={(e) => e.stopPropagation()}
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
                disabled: (files: File[]) => false, icon: "edit", color: undefined
            },
            ...AllFileOperations(true, fileSelectorOperations, refetch, this.props.history)
        ];
        return (
            <Grid>
                <Grid.Column computer={13} tablet={16}>
                    <Grid.Row>
                        <Responsive
                            as={ContextButtons}
                            maxWidth={991}
                            createFolder={() => this.newFolder()}
                            currentPath={path}
                            showUploader={this.props.showUploader}
                        />
                        <BreadCrumbs currentPath={path} navigate={(newPath) => navigate(newPath)} homeFolder={Cloud.homeFolder} />
                    </Grid.Row>
                    <Pagination.List
                        loading={loading}
                        errorMessage={error}
                        onErrorDismiss={this.props.dismissError}
                        customEmptyPage={(<Header.Subheader content="No files in current folder" />)}
                        pageRenderer={(page) => (
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
                            />
                        )}
                        onRefresh={refetch}
                        onItemsPerPageChanged={(pageSize) => fetchFiles(path, pageSize, 0, sortOrder, sortBy)}
                        page={page}
                        onPageChanged={(pageNumber: number) => fetchFiles(path, page.itemsPerPage, pageNumber, sortOrder, sortBy)}
                    />
                </Grid.Column>
                <Responsive as={Grid.Column} computer={3} minWidth={992}>
                    <ContextBar
                        showUploader={this.props.showUploader}
                        fileOperations={fileOperations}
                        files={selectedFiles}
                        createFolder={() => this.newFolder()}
                    />
                </Responsive>
                <FileSelectorModal
                    show={this.props.fileSelectorShown}
                    onHide={() => this.props.showFileSelector(false)}
                    path={this.props.fileSelectorPath}
                    fetchFiles={(path, pageNumber, itemsPerPage) => this.props.fetchSelectorFiles(path, pageNumber, itemsPerPage)}
                    loading={this.props.fileSelectorLoading}
                    errorMessage={this.props.fileSelectorError}
                    onErrorDismiss={() => this.props.onFileSelectorErrorDismiss()}
                    onlyAllowFolders
                    canSelectFolders
                    page={this.props.fileSelectorPage}
                    setSelectedFile={this.props.fileSelectorCallback}
                    disallowedPaths={this.props.disallowedPaths}
                />
            </Grid>);
    }
}

export const FilesTable = ({
    files, masterCheckbox, sortingIcon, sortFiles, onRenameFile, onCheckFile, sortingColumns, onDropdownSelect,
    fileOperations, sortOrder, onFavoriteFile, sortBy
}: FilesTableProps) => (
        <Table unstackable basic="very">
            <FilesTableHeader
                onDropdownSelect={onDropdownSelect}
                sortOrder={sortOrder}
                sortingColumns={sortingColumns}
                masterCheckbox={masterCheckbox}
                toSortingIcon={sortingIcon}
                sortFiles={sortFiles}
                sortBy={sortBy}
            />
            <Table.Body>
                {files.map((file: File, i: number) => (
                    <Table.Row className="file-row" key={i}>
                        <FilenameAndIcons
                            file={file}
                            onFavoriteFile={onFavoriteFile}
                            hasCheckbox={masterCheckbox != null}
                            onRenameFile={onRenameFile}
                            onCheckFile={(checked: boolean) => onCheckFile(checked, file)}
                        />
                        <Responsive as={Table.Cell} minWidth={768} content={sortingColumns ? UF.sortingColumnToValue(sortingColumns[0], file) : dateToString(file.modifiedAt)} />
                        <Responsive as={Table.Cell} minWidth={768} content={sortingColumns ? UF.sortingColumnToValue(sortingColumns[1], file) : UF.getOwnerFromAcls(file.acl)} />
                        <Table.Cell>
                            <Dropdown direction="left" icon="ellipsis horizontal">
                                <Dropdown.Menu>
                                    <FileOperations files={[file]} fileOperations={fileOperations} As={Dropdown.Item} />
                                </Dropdown.Menu>
                            </Dropdown>
                        </Table.Cell>
                    </Table.Row>)
                )}
            </Table.Body>
        </Table>
    );

const ResponsiveTableColumn = ({ asDropdown, iconName, onSelect = (sO: SortOrder, sB: SortBy) => null, isSortedBy, currentSelection, sortOrder, minWidth = undefined }: ResponsiveTableColumnProps) => (
    <Responsive minWidth={minWidth} as={Table.HeaderCell}>
        <SortByDropdown isSortedBy={isSortedBy} onSelect={onSelect} asDropdown={asDropdown} currentSelection={currentSelection} sortOrder={sortOrder} />
        <Icon className="float-right" name={iconName} />
    </Responsive>
)

const toSortOrder = (sortBy: SortBy, lastSort: SortBy, sortOrder: SortOrder) =>
    sortBy === lastSort ? (sortOrder === SortOrder.ASCENDING ? SortOrder.DESCENDING : SortOrder.ASCENDING) : SortOrder.ASCENDING;

const FilesTableHeader = ({ toSortingIcon = () => undefined, sortFiles = () => null, sortOrder, masterCheckbox, sortingColumns, onDropdownSelect, sortBy }: FilesTableHeaderProps) => (
    <Table.Header>
        <Table.Row>
            <Table.HeaderCell className="filename-row" onClick={() => sortFiles(toSortOrder(SortBy.PATH, sortBy, sortOrder), SortBy.PATH)}>
                {masterCheckbox}
                Filename
                <Icon className="float-right" name={toSortingIcon(SortBy.PATH)} />
            </Table.HeaderCell>
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
            <Table.HeaderCell />
        </Table.Row>
    </Table.Header>
);

const SortByDropdown = ({ currentSelection, sortOrder, onSelect, asDropdown, isSortedBy }: SortByDropdownProps) => asDropdown ? (
    <Dropdown simple text={UF.prettierString(currentSelection)}>
        <Dropdown.Menu>
            <Dropdown.Item text={UF.prettierString(SortOrder.ASCENDING)} onClick={() => onSelect(SortOrder.ASCENDING, currentSelection)} disabled={sortOrder === SortOrder.ASCENDING && isSortedBy} />
            <Dropdown.Item text={UF.prettierString(SortOrder.DESCENDING)} onClick={() => onSelect(SortOrder.DESCENDING, currentSelection)} disabled={sortOrder === SortOrder.DESCENDING && isSortedBy} />
            <Dropdown.Divider />
            {Object.keys(SortBy).filter(it => it !== currentSelection).map((sortByKey: SortBy, i) => (
                <Dropdown.Item key={i} onClick={() => onSelect(sortOrder, sortByKey)} text={UF.prettierString(sortByKey)} />
            ))}
        </Dropdown.Menu>
    </Dropdown>) : <>{UF.prettierString(currentSelection)}</>;

const ContextBar = ({ files, ...props }: ContextBarProps) => (
    <div>
        <ContextButtons showUploader={props.showUploader} createFolder={props.createFolder} />
        <Divider />
        <FileOptions files={files} {...props} />
    </div>
);

const ContextButtons = ({ createFolder, showUploader }: ContextButtonsProps) => (
    <div>
        <Button color="blue" className="context-button-margin" fluid onClick={() => showUploader()} content="Upload Files" />
        <Button basic className="context-button-margin" fluid onClick={() => createFolder()} content="New folder" />
        <Button as={Link} to={`/filesearch`} basic className="context-button-margin" fluid content="Advanced Search" color="green" />
    </div>
);

const PredicatedCheckbox = ({ predicate, checked, onClick }) =>
    predicate ? (
        <Checkbox
            checked={checked}
            type="checkbox"
            className="hidden-checkbox checkbox-margin"
            onClick={onClick}
        />
    ) : null;

const PredicatedFavorite = ({ predicate, item, onClick }) =>
    predicate ? (
        <Icon
            color="blue"
            name={item.favorited ? "star" : "star outline"}
            className={`${item.favorited ? "" : "file-data"} favorite-padding`}
            onClick={onClick}
        />
    ) : null;

const GroupIcon = ({ isProject }: { isProject: boolean }) => isProject ? (<Icon className="group-icon-padding" name="users" />) : null;

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
            size={size} link={file.link}
        />
    );
    const nameLink = <FileLink file={file}>{icon}{fileName}</FileLink>;
    return file.beingRenamed ?
        <Table.Cell className="table-cell-padding-left">
            <Input
                defaultValue={fileName}
                onKeyDown={(e) => { if (!!onRenameFile) onRenameFile(e.keyCode, file, e.target.value) }}
                autoFocus
                transparent
                fluid
            >
                {checkbox}
                {icon}
                <input />
                <Button content="Cancel" size="small" color="red" basic onClick={() => onRenameFile(KeyCode.ESC, file, "")} />
            </Input>

        </Table.Cell> :
        <Table.Cell className="table-cell-padding-left">
            {checkbox}
            {nameLink}
            <GroupIcon isProject={isProject(file)} />
            <PredicatedFavorite predicate={!!onFavoriteFile && !file.path.startsWith(`${Cloud.homeFolder}Favorites`)} item={file} onClick={() => onFavoriteFile([file])} />
        </Table.Cell>
};

const FileOptions = ({ files, fileOperations }: FileOptionsProps) => files.length ? (
    <div>
        <Header as="h3">{toFileText(files)}</Header>
        <FileOperations files={files} fileOperations={fileOperations} As={Button} fluid basic />
    </div>
) : null;

export const FileOperations = ({ files, fileOperations, As, fluid, basic }) => files.length && fileOperations.length ?
    fileOperations.map((fileOp, i) => {
        let operation = fileOp;
        if ((fileOp as PredicatedOperation).predicate) {
            operation = fileOp as PredicatedOperation
            operation = operation.predicate(files, Cloud) ? operation.onTrue : operation.onFalse;
        }
        operation = operation as Operation;
        return !operation.disabled(files, Cloud) ? (
            <As
                key={i}
                content={operation.text}
                icon={operation.icon}
                color={operation.color}
                className="context-button-margin"
                onClick={() => (operation as Operation).onClick(files, Cloud)}
                fluid={fluid}
                basic={basic}
            />
        ) : null;
    }) : null;

const mapStateToProps = ({ files }: ReduxObject): FilesStateProps => {
    const { page, loading, path, fileSelectorPage, fileSelectorPath, sortBy, sortOrder, fileSelectorShown,
        fileSelectorCallback, disallowedPaths, fileSelectorLoading, error, fileSelectorError, sortingColumns } = files;
    const favFilesCount = page.items.filter(file => file.favorited).length; // HACK to ensure changes to favorites are rendered.
    const checkedFilesCount = page.items.filter(file => file.isChecked).length; // HACK to ensure changes to file checkings are rendered.
    const renamingCount = page.items.filter(file => file.beingRenamed).length;
    const fileCount = page.items.length;
    return {
        error, fileSelectorError, page, loading, path, checkedFilesCount, favFilesCount, fileSelectorPage, fileSelectorPath,
        fileSelectorShown, fileSelectorCallback, disallowedPaths, sortOrder, sortBy, fileCount, fileSelectorLoading,
        leftSortingColumn: sortingColumns[0], rightSortingColumn: sortingColumns[1], renamingCount
    }
};

const mapDispatchToProps = (dispatch: Dispatch): FilesOperations => ({
    prioritizeFileSearch: () => dispatch(setPrioritizedSearch("files")),
    onFileSelectorErrorDismiss: () => dispatch(Actions.setFileSelectorError(undefined)),
    dismissError: () => dispatch(Actions.setErrorMessage()),
    fetchFiles: async (path: string, itemsPerPage: number, pageNumber: number, sortOrder: SortOrder, sortBy: SortBy, index?: number) => {
        dispatch(Actions.updatePath(path));
        dispatch(Actions.setLoading(true));
        if (index != null) dispatch(Actions.setSortingColumn(sortBy, index));
        dispatch(await Actions.fetchFiles(path, itemsPerPage, pageNumber, sortOrder, sortBy));
    },
    fetchPageFromPath: async (path: string, itemsPerPage: number, sortOrder: SortOrder, sortBy: SortBy) => {
        dispatch(Actions.setLoading(true));
        dispatch(await Actions.fetchPageFromPath(path, itemsPerPage, sortOrder, sortBy));
    },
    updatePath: (path: string) => dispatch(Actions.updatePath(path)),
    fetchSelectorFiles: async (path: string, pageNumber: number, itemsPerPage: number) => dispatch(await Actions.fetchFileselectorFiles(path, pageNumber, itemsPerPage)),
    showFileSelector: (open: boolean) => dispatch(Actions.fileSelectorShown(open)),
    setFileSelectorCallback: (callback) => dispatch(Actions.setFileSelectorCallback(callback)),
    checkFile: (checked: boolean, path: string) => dispatch(Actions.checkFile(checked, path)),
    setPageTitle: () => dispatch(updatePageTitle("Files")),
    updateFiles: (page: Page<File>) => dispatch(Actions.updateFiles(page)),
    checkAllFiles: (checked: boolean) => dispatch(Actions.checkAllFiles(checked)),
    setDisallowedPaths: (disallowedPaths: string[]) => dispatch(Actions.setDisallowedPaths(disallowedPaths)),
    showUploader: () => dispatch(setUploaderVisible(true)),
    setUploaderCallback: (callback) => dispatch(setUploaderCallback(callback))
});

export default connect(mapStateToProps, mapDispatchToProps)(Files);