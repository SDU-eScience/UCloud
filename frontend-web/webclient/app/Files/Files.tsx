import * as React from "react";
import { connect } from "react-redux";
import { Cloud } from "Authentication/SDUCloudObject";
import { setUploaderVisible, setUploaderCallback } from "Uploader/Redux/UploaderActions";
import * as Pagination from "Pagination";
import { BreadCrumbs } from "ui-components/Breadcrumbs";
import * as UF from "UtilityFunctions";
import { KeyCode, ReduxObject } from "DefaultObjects";
import * as Actions from "./Redux/FilesActions";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import { FileSelectorModal } from "./FileSelector";
import { RefreshButton, MasterCheckbox, CustomEntriesPerPage } from "UtilityComponents";
import { FilesProps, FilesStateProps, FilesOperations, File, FileOperation } from ".";
import { setPrioritizedSearch } from "Navigation/Redux/HeaderActions";
import {
    startRenamingFiles, AllFileOperations, isInvalidPathName, favoriteFileFromPage, getFilenameFromPath,
    getParentPath, moveFile, createFolder, fileTablePage
} from "Utilities/FileUtilities";
import { Box, Hide, Flex, Checkbox, Label } from "ui-components";
import * as Heading from "ui-components/Heading";
import { Dispatch } from "redux";
import DetailedFileSearch from "./DetailedFileSearch";
import { getQueryParamOrElse, RouterLocationProps } from "Utilities/URIUtilities";
import { allFilesHasAccessRight } from "Utilities/FileUtilities";
import { AccessRight } from "Types";
import { FilesTable, ContextBar, ContextButtons } from "./FilesTable";

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
            <MasterCheckbox
                checked={page.items.length === selectedFiles.length && page.items.length > 0}
                onClick={this.props.checkAllFiles}
            />
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
            <CustomEntriesPerPage
                entriesPerPage={page.itemsPerPage}
                text="Files per page"
                onChange={itemsPerPage => fetchFiles(path, itemsPerPage, page.pageNumber, sortOrder, sortBy)}
                loading={loading}
                onRefreshClick={refetch}
            />
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