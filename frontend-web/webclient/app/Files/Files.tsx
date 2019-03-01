import * as React from "react";
import { connect } from "react-redux";
import { Cloud } from "Authentication/SDUCloudObject";
import { setUploaderVisible, setUploaderCallback } from "Uploader/Redux/UploaderActions";
import * as Pagination from "Pagination";
import { BreadCrumbs } from "ui-components/Breadcrumbs";
import * as UF from "UtilityFunctions";
import { KeyCode, ReduxObject } from "DefaultObjects";
import * as Actions from "./Redux/FilesActions";
import { updatePageTitle, setActivePage } from "Navigation/Redux/StatusActions";
import { FileSelectorModal } from "./FileSelector";
import { MasterCheckbox } from "UtilityComponents";
import { FilesProps, FilesStateProps, FilesOperations, File, FileOperation } from ".";
import { setPrioritizedSearch, setRefreshFunction } from "Navigation/Redux/HeaderActions";
import {
    startRenamingFiles, AllFileOperations, isInvalidPathName, favoriteFileFromPage, getFilenameFromPath,
    getParentPath, moveFile, createFolder, fileTablePage
} from "Utilities/FileUtilities";
import { Box } from "ui-components";
import * as Heading from "ui-components/Heading";
import { Dispatch } from "redux";
import { getQueryParamOrElse, RouterLocationProps } from "Utilities/URIUtilities";
import { allFilesHasAccessRight } from "Utilities/FileUtilities";
import { AccessRight } from "Types";
import { FilesTable, ContextBar } from "./FilesTable";
import { MainContainer } from "MainContainer/MainContainer";
import { setFileSelectorLoading } from "./Redux/FilesActions";
import { SidebarPages } from "ui-components/Sidebar";
import { Spacer } from "ui-components/Spacer";

class Files extends React.Component<FilesProps> {
    componentDidMount() {
        const { page, sortOrder, sortBy, history, ...props } = this.props;
        props.setPageTitle();
        props.setActivePage();
        props.prioritizeFileSearch();
        props.setUploaderCallback(
            (path: string) => props.fetchFiles(path, page.itemsPerPage, page.pageNumber, sortOrder, sortBy)
        );
        props.fetchFiles(this.urlPath, page.itemsPerPage, page.pageNumber, sortOrder, sortBy);
        this.props.setRefresh(() => props.fetchFiles(this.urlPath, page.itemsPerPage, page.pageNumber, sortOrder, sortBy));
    }

    componentWillUnmount() {
        this.props.setRefresh();
    }

    componentWillReceiveProps(nextProps: FilesProps) {
        const { page, fetchFiles, sortBy, sortOrder } = nextProps;
        this.props.setRefresh(() => fetchFiles(this.urlPath, page.itemsPerPage, page.pageNumber, sortOrder, sortBy));
    }

    private urlPathFromProps = (props: RouterLocationProps): string => getQueryParamOrElse(props, "path", Cloud.homeFolder);

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
                moveFile(file.path, fullPath, Cloud, () => this.props.setLoading(true),
                    () => fetchPageFromPath(fullPath, page.itemsPerPage, this.props.sortOrder, this.props.sortBy));
            }
        }
    }

    private fetchPageFromPath = (path: string) => {
        const { page, history, sortOrder, sortBy } = this.props;
        this.props.fetchPageFromPath(path, page.itemsPerPage, sortOrder, sortBy);
        this.props.updatePath(getParentPath(path)); // FIXME Could these be handled by shouldComponentUpdate?
        history.push(fileTablePage(getParentPath(path)))
    }

    shouldComponentUpdate(nextProps: FilesProps): boolean {
        const { fetchFiles, page, loading, sortOrder, sortBy } = this.props;
        const nextPath = this.urlPathFromProps(nextProps);
        if (nextProps.path !== nextPath && !loading) {
            fetchFiles(nextPath, page.itemsPerPage, 0, sortOrder, sortBy);
            return false;
        }
        return true;
    }

    private readonly fileSelectorOperations = {
        setDisallowedPaths: this.props.setDisallowedPaths,
        setFileSelectorCallback: this.props.setFileSelectorCallback,
        showFileSelector: this.props.showFileSelector,
        fetchPageFromPath: this.fetchPageFromPath,
        fetchFilesPage: (path: string) =>
            this.props.fetchFiles(path, this.props.page.itemsPerPage, this.props.page.pageNumber, this.props.sortOrder, this.props.sortBy)
    };

    private readonly refetch = () => {
        const { path, page, sortOrder, sortBy } = this.props;
        this.props.fetchFiles(path, page.itemsPerPage, page.pageNumber, sortOrder, sortBy);
    }

    private readonly fileOperations: FileOperation[] = [
        {
            text: "Rename",
            onClick: files => this.props.updateFiles(startRenamingFiles(files, this.props.page)),
            disabled: (files: File[]) => !allFilesHasAccessRight(AccessRight.WRITE, files),
            icon: "rename",
            color: undefined
        },
        ...AllFileOperations({
            stateless: true,
            fileSelectorOps: this.fileSelectorOperations,
            onDeleted: this.refetch,
            onExtracted: this.refetch,
            onSensitivityChange: this.refetch,
            onClearTrash: () => this.props.fetchFiles(this.props.path, this.props.page.itemsPerPage, this.props.page.pageNumber, this.props.sortOrder, this.props.sortBy),
            onLinkCreate: p => this.props.fetchPageFromPath(p, this.props.page.itemsPerPage, this.props.sortOrder, this.props.sortBy),
            history: this.props.history,
            setLoading: () => this.props.setLoading(true)
        })
    ];

    render() {
        const { page, path, loading, history, fetchFiles, checkFile, updateFiles, sortBy, sortOrder, leftSortingColumn,
            rightSortingColumn, setDisallowedPaths, setFileSelectorCallback, showFileSelector, ...props } = this.props;
        const selectedFiles = page.items.filter(file => file.isChecked);
        const navigate = (path: string) => history.push(fileTablePage(path)); // FIXME Is this necessary?
        const header = (
            <Spacer
                left={<BreadCrumbs currentPath={path} navigate={newPath => navigate(newPath)} homeFolder={Cloud.homeFolder} />}
                right={<Pagination.EntriesPerPageSelector
                    content="Files per page"
                    entriesPerPage={page.itemsPerPage}
                    onChange={itemsPerPage => fetchFiles(path, itemsPerPage, page.pageNumber, sortOrder, sortBy)}
                />}
            />
        );
        const main = (
            <Pagination.List
                loading={loading}
                errorMessage={props.error}
                onErrorDismiss={props.dismissError}
                customEmptyPage={!this.props.error ? <Heading.h3>No files in current folder</Heading.h3> : <Box />}
                pageRenderer={page => (
                    <FilesTable
                        onFavoriteFile={files => updateFiles(favoriteFileFromPage(page, files, Cloud))}
                        fileOperations={this.fileOperations}
                        sortFiles={(sortOrder, sortBy) => fetchFiles(path, page.itemsPerPage, page.pageNumber, sortOrder, sortBy)}
                        sortingIcon={name => UF.getSortingIcon(sortBy, sortOrder, name)}
                        sortOrder={sortOrder}
                        sortingColumns={[leftSortingColumn, rightSortingColumn]}
                        refetchFiles={() => this.refetch()}
                        onDropdownSelect={(sortOrder, sortBy, index) => fetchFiles(path, page.itemsPerPage, page.pageNumber, sortOrder, sortBy, index)}
                        masterCheckbox={
                            <MasterCheckbox
                                checked={page.items.length === selectedFiles.length && page.items.length > 0}
                                onClick={this.props.checkAllFiles}
                            />}
                        onRenameFile={this.onRenameFile}
                        files={page.items}
                        sortBy={sortBy}
                        onCheckFile={(checked, file) => checkFile(checked, file.path)}
                    />
                )}
                page={page}
                onPageChanged={pageNumber => fetchFiles(path, page.itemsPerPage, pageNumber, sortOrder, sortBy)}
            />
        );

        const sidebar = (
            !props.invalidPath ?
                <Box pl="5px" pr="5px">
                    <ContextBar
                        invalidPath={props.invalidPath}
                        showUploader={props.showUploader}
                        fileOperations={this.fileOperations}
                        files={selectedFiles}
                        inTrashFolder={UF.addTrailingSlash(path) === Cloud.trashFolder}
                        createFolder={() => props.createFolder()}
                        toHome={() => navigate(Cloud.homeFolder)}
                    />
                </Box> : null
        );
        const additional = (
            <FileSelectorModal
                isFavorites={props.fileSelectorIsFavorites}
                fetchFavorites={(pageNumber, itemsPerPage) => props.fetchFileSelectorFavorites(pageNumber, itemsPerPage)}
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
        );

        return (
            <MainContainer
                header={header}
                main={<>{props.responsive!.lessThan.xl ? sidebar : null}{main}</>}
                sidebar={props.responsive!.lessThan.xl ? null : sidebar}
                additional={additional}
            />
        );
    }
}

const mapStateToProps = ({ files, responsive }: ReduxObject): FilesStateProps => {
    const { page, loading, path, fileSelectorPage, fileSelectorPath, sortBy, sortOrder, fileSelectorShown, invalidPath,
        fileSelectorCallback, disallowedPaths, fileSelectorLoading, error, fileSelectorError, sortingColumns, fileSelectorIsFavorites } = files;
    const favFilesCount = page.items.filter(file => file.favorited).length; // HACK to ensure changes to favorites are rendered.
    const renamingCount = page.items.filter(file => file.beingRenamed).length;
    const fileCount = page.items.length;
    return {
        error, fileSelectorError, page, loading, path, favFilesCount, fileSelectorPage, fileSelectorPath, invalidPath,
        fileSelectorShown, fileSelectorCallback, disallowedPaths, sortOrder, sortBy, fileCount, fileSelectorLoading,
        renamingCount, leftSortingColumn: sortingColumns[0], rightSortingColumn: sortingColumns[1], fileSelectorIsFavorites,
        responsive
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
    setLoading: loading => dispatch(Actions.setLoading(loading)),
    updatePath: path => dispatch(Actions.updatePath(path)),
    fetchSelectorFiles: async (path, pageNumber, itemsPerPage) => {
        dispatch(setFileSelectorLoading());
        dispatch(await Actions.fetchFileselectorFiles(path, pageNumber, itemsPerPage));
    },
    fetchFileSelectorFavorites: async (pageNumber, itemsPerPage) => {
        dispatch(setFileSelectorLoading());
        dispatch(await Actions.fetchFileSelectorFavorites(pageNumber, itemsPerPage))
    },
    showFileSelector: open => dispatch(Actions.fileSelectorShown(open)),
    setFileSelectorCallback: callback => dispatch(Actions.setFileSelectorCallback(callback)),
    checkFile: (checked, path) => dispatch(Actions.checkFile(checked, path)),
    setPageTitle: () => dispatch(updatePageTitle("Files")),
    updateFiles: page => dispatch(Actions.updateFiles(page)),
    checkAllFiles: checked => dispatch(Actions.checkAllFiles(checked)),
    setDisallowedPaths: disallowedPaths => dispatch(Actions.setDisallowedPaths(disallowedPaths)),
    showUploader: () => dispatch(setUploaderVisible(true)),
    setUploaderCallback: callback => dispatch(setUploaderCallback(callback)),
    setActivePage: () => dispatch(setActivePage(SidebarPages.Files)),
    setRefresh: refresh => dispatch(setRefreshFunction(refresh))
});

export default connect<FilesStateProps, FilesOperations>(mapStateToProps, mapDispatchToProps)(Files);