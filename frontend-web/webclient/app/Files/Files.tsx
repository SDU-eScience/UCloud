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
import { FilesProps, FilesStateProps, FilesOperations, File, FileOperation, FileResource as FR, FileResource } from ".";
import { setPrioritizedSearch, setRefreshFunction } from "Navigation/Redux/HeaderActions";
import {
    startRenamingFiles, allFileOperations, isInvalidPathName, favoriteFileFromPage, getFilenameFromPath,
    getParentPath, moveFile, createFolder, fileTablePage, addFileAcls, markFileAsChecked
} from "Utilities/FileUtilities";
import Box from "ui-components/Box";
import * as Heading from "ui-components/Heading";
import { Dispatch } from "redux";
import { getQueryParamOrElse } from "Utilities/URIUtilities";
import { allFilesHasAccessRight } from "Utilities/FileUtilities";
import { AccessRight } from "Types";
import FilesTable, { ContextBar } from "./FilesTable";
import { MainContainer } from "MainContainer/MainContainer";
import { setFileSelectorLoading } from "./Redux/FilesActions";
import { SidebarPages } from "ui-components/Sidebar";
import { Spacer } from "ui-components/Spacer";
import { addNotificationEntry } from "Utilities/ReduxUtilities";

const Files = (props: FilesProps) => {

    const urlPath = () => urlPathFromProps();

    React.useEffect(() => {
        const { page, sortOrder, sortBy } = props;
        props.onInit();
        props.setUploaderCallback(path => props.fetchFiles(path, page.itemsPerPage, page.pageNumber, sortOrder, sortBy, baseFRs));
        return () => props.clearRefresh();
    }, []);

    const urlPathFromProps = (): string => getQueryParamOrElse(props, "path", Cloud.homeFolder);

    const onRenameFile = (key: number, file: File, name: string) => {
        const { path, fetchPageFromPath, updateFiles, page, addSnack } = props;
        if (key === KeyCode.ESC) {
            const item = page.items.find(f => f.path === file.path);
            if (item !== undefined) item.beingRenamed = false;
            page.items = page.items.filter(file => !file.isMockFolder);
            updateFiles(page);
        } else if (key === KeyCode.ENTER) {
            const fileNames = page.items.map(file => getFilenameFromPath(file.path));
            if (isInvalidPathName({ path: name, filePaths: fileNames, addSnack: addSnack })) return;
            const fullPath = `${UF.addTrailingSlash(path)}${name}`;
            const { sortOrder, sortBy } = props;
            if (file.isMockFolder) {
                createFolder({
                    path: fullPath,
                    cloud: Cloud,
                    onSuccess: () => fetchPageFromPath(fullPath, page.itemsPerPage, sortOrder, sortBy, baseFRs),
                    addSnack: props.addSnack
                })
            } else {
                moveFile({
                    oldPath: file.path,
                    newPath: fullPath,
                    cloud: Cloud,
                    setLoading: () => props.setLoading(true),
                    onSuccess: () => fetchPageFromPath(fullPath, page.itemsPerPage, sortOrder, sortBy, baseFRs),
                    addSnack: props.addSnack
                });
            }
        }
    };

    React.useEffect(() => {
        if (!props.loading) props.fetchFiles(urlPath(), page.itemsPerPage, 0, sortOrder, sortBy, baseFRs)
    }, [urlPath()]);


    const fileSelectorOperations = {
        setDisallowedPaths: props.setDisallowedPaths,
        setFileSelectorCallback: props.setFileSelectorCallback,
        showFileSelector: props.showFileSelector,
        fetchPageFromPath: (path: string) =>
            (props.fetchPageFromPath(path, props.page.itemsPerPage, props.sortOrder, props.sortBy, baseFRs),
                props.history.push(fileTablePage(getParentPath(path)))),
        fetchFilesPage: (path: string) =>
            props.fetchFiles(path, props.page.itemsPerPage, props.page.pageNumber, props.sortOrder, props.sortBy, baseFRs)
    };

    const refetch = () => {
        const { path, page, sortOrder, sortBy } = props;
        props.fetchFiles(path, page.itemsPerPage, page.pageNumber, sortOrder, sortBy, baseFRs);
    };

    const fileOperations: FileOperation[] = [
        {
            text: "Rename",
            onClick: files => props.updateFiles(startRenamingFiles(files, props.page)),
            disabled: (files: File[]) => !allFilesHasAccessRight(AccessRight.WRITE, files),
            icon: "rename",
            color: undefined
        },
        ...allFileOperations({
            stateless: true,
            fileSelectorOperations: fileSelectorOperations,
            onDeleted: refetch,
            onExtracted: refetch,
            onSensitivityChange: refetch,
            onClearTrash: () => props.fetchFiles(props.path, props.page.itemsPerPage, props.page.pageNumber, props.sortOrder, props.sortBy, baseFRs),
            history: props.history,
            setLoading: () => props.setLoading(true),
            addSnack: props.addSnack
        })
    ];

    const baseFRs: FileResource[] = [
        FR.FILE_ID,
        FR.PATH,
        FR.LINK,
        FR.FILE_TYPE,
        props.leftSortingColumn as unknown as FileResource,
        props.rightSortingColumn as unknown as FileResource
    ];


    const { page, path, loading, history, fetchFiles, checkFile, updateFiles, sortBy, sortOrder, leftSortingColumn,
        rightSortingColumn, showFileSelector } = props;
    const selectedFiles = page.items.filter(file => file.isChecked);
    const navigate = (path: string) => history.push(fileTablePage(path));
    const header = (
        <Spacer
            left={<BreadCrumbs currentPath={path} navigate={newPath => navigate(newPath)} homeFolder={Cloud.homeFolder} />}
            right={<Pagination.EntriesPerPageSelector
                content="Files per page"
                entriesPerPage={page.itemsPerPage}
                onChange={itemsPerPage => fetchFiles(path, itemsPerPage, 0, sortOrder, sortBy, baseFRs)}
            />}
        />
    );
    const main = (
        <Pagination.List
            loading={loading}
            errorMessage={props.error}
            onErrorDismiss={props.dismissError}
            customEmptyPage={!props.error ? <Heading.h3>No files in current folder</Heading.h3> : <Box />}
            pageRenderer={page => (
                <FilesTable
                    onFavoriteFile={files => updateFiles(favoriteFileFromPage(page, files, Cloud))}
                    fileOperations={fileOperations}
                    sortFiles={(sortOrder, sortBy) => fetchFiles(path, page.itemsPerPage, page.pageNumber, sortOrder, sortBy, baseFRs)}
                    sortingIcon={name => UF.getSortingIcon(sortBy, sortOrder, name)}
                    sortOrder={sortOrder}
                    sortingColumns={[leftSortingColumn, rightSortingColumn]}
                    refetchFiles={() => refetch()}
                    onDropdownSelect={(sortOrder, sortBy, index) => fetchFiles(path, page.itemsPerPage, page.pageNumber, sortOrder, sortBy, baseFRs, index)}
                    masterCheckbox={
                        <MasterCheckbox
                            checked={page.items.length === selectedFiles.length && page.items.length > 0}
                            onClick={props.checkAllFiles}
                        />}
                    onRenameFile={onRenameFile}
                    files={page.items}
                    sortBy={sortBy}
                    onCheckFile={(checked, file) => checkFile(checked, file.path)}
                />
            )}
            page={page}
            onPageChanged={pageNumber => fetchFiles(path, page.itemsPerPage, pageNumber, sortOrder, sortBy, baseFRs)}
        />
    );

    const sidebar = (
        !props.invalidPath ?
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
            </Box> : null
    );
    const additional = (
        <FileSelectorModal
            isFavorites={props.fileSelectorIsFavorites}
            fetchFiles={(path, pageNumber, itemsPerPage) => props.fetchSelectorFiles(path, pageNumber, itemsPerPage)}
            fetchFavorites={(pageNumber, itemsPerPage) => props.fetchFileSelectorFavorites(pageNumber, itemsPerPage)}
            show={props.fileSelectorShown}
            onHide={() => showFileSelector(false)}
            path={props.fileSelectorPath}
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
            main={main}
            sidebar={sidebar}
            additional={additional}
        />
    );
}

const mapStateToProps = ({ files, responsive }: ReduxObject): FilesStateProps => {
    const { page, loading, path, fileSelectorPage, fileSelectorPath, sortBy, sortOrder, fileSelectorShown, invalidPath,
        fileSelectorCallback, disallowedPaths, fileSelectorLoading, error, fileSelectorError, sortingColumns, fileSelectorIsFavorites } = files;
    const favFilesCount = page.items.filter(file => file.favorited).length; // HACK to ensure changes to favorites are rendered.
    const renamingCount = page.items.filter(file => file.beingRenamed).length;
    const fileCount = page.items.length;
    const aclCount = page.items.filter(it => it.acl !== null).flatMap(it => it.acl!).length;
    const sensitivityCount = page.items.filter(it => it.sensitivityLevel != null).length;
    return {
        error, fileSelectorError, page, loading, path, favFilesCount, fileSelectorPage, fileSelectorPath, invalidPath,
        fileSelectorShown, fileSelectorCallback, disallowedPaths, sortOrder, sortBy, fileCount, fileSelectorLoading,
        renamingCount, leftSortingColumn: sortingColumns[0], rightSortingColumn: sortingColumns[1], fileSelectorIsFavorites,
        responsive, aclCount, sensitivityCount
    }
};

const mapDispatchToProps = (dispatch: Dispatch): FilesOperations => ({
    onInit: () => {
        dispatch(setPrioritizedSearch("files"));
        dispatch(updatePageTitle("Files"));
        dispatch(setActivePage(SidebarPages.Files));
    },
    onFileSelectorErrorDismiss: () => dispatch(Actions.setFileSelectorError({})),
    dismissError: () => dispatch(Actions.setErrorMessage()),
    createFolder: () => dispatch(Actions.createFolder()),
    fetchFiles: (path, itemsPerPage, pageNumber, sortOrder, sortBy, attrs, index) => {
        dispatch(Actions.updatePath(path));
        /* FIXME: Must be a better way */
        const fetch = async (): Promise<void> => {
            dispatch(Actions.setLoading(true));
            const promiseWithoutAcl = Actions.fetchFiles(path, itemsPerPage, pageNumber, sortOrder, sortBy, attrs)
                .then(action => (dispatch(action), action));

            const promiseWithAcl = Actions.fetchFiles(path, itemsPerPage, pageNumber, sortOrder, sortBy, [FR.ACL, FR.FILE_ID, FR.OWNER_NAME, FR.FAVORITED, FR.SENSITIVITY_LEVEL])

            const [hasAcls, noAcls] = await Promise.all([promiseWithAcl, promiseWithoutAcl])
            if ("page" in noAcls.payload) {
                if ("page" in hasAcls.payload) {
                    dispatch(Actions.receiveFiles(addFileAcls(noAcls.payload.page, hasAcls.payload.page), path, sortOrder, sortBy));
                } else {
                    dispatch(noAcls); // Dispatch other error
                }
            }
        };
        if (index != null) dispatch(Actions.setSortingColumn(sortBy, index));
        fetch();
        dispatch(setRefreshFunction(fetch));
    },
    fetchPageFromPath: (path, itemsPerPage, sortOrder, sortBy, attrs) => {
        const fetch = async () => {
            dispatch(Actions.setLoading(true));
            const promiseWithoutAcl = Actions.fetchPageFromPath(path, itemsPerPage, sortOrder, sortBy, attrs)
                .then(action => (dispatch(action), action));

            const promiseWithAcl = Actions.fetchPageFromPath(path, itemsPerPage, sortOrder, sortBy, [FR.ACL, FR.FILE_ID, FR.OWNER_NAME, FR.FAVORITED, FR.SENSITIVITY_LEVEL]);

            const [hasAcls, noAcls] = await Promise.all([promiseWithAcl, promiseWithoutAcl])
            if ("page" in noAcls.payload) {
                if ("page" in hasAcls.payload) {
                    const joinedPage = markFileAsChecked(path, addFileAcls(noAcls.payload.page, hasAcls.payload.page));
                    dispatch(Actions.receiveFiles(joinedPage, getParentPath(path), sortOrder, sortBy));
                } else {
                    dispatch(noAcls);
                }
            }

        };
        fetch();
        dispatch(setRefreshFunction(fetch));
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
    updateFiles: page => dispatch(Actions.updateFiles(page)),
    checkAllFiles: checked => dispatch(Actions.checkAllFiles(checked)),
    setDisallowedPaths: disallowedPaths => dispatch(Actions.setDisallowedPaths(disallowedPaths)),
    showUploader: () => dispatch(setUploaderVisible(true)),
    setUploaderCallback: callback => dispatch(setUploaderCallback(callback)),
    clearRefresh: () => dispatch(setRefreshFunction()),
    addSnack: snack => addNotificationEntry(dispatch, snack)
});

export default connect<FilesStateProps, FilesOperations>(mapStateToProps, mapDispatchToProps)(Files);