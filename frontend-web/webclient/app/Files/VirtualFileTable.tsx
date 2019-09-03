import {useAsyncWork} from "Authentication/DataHook";
import {Cloud} from "Authentication/SDUCloudObject";
import {emptyPage} from "DefaultObjects";
import {File} from "Files/index";
import {LowLevelFileTable, LowLevelFileTableProps} from "Files/LowLevelFileTable";
import * as React from "react";
import {useEffect, useMemo, useState} from "react";
import {Page} from "Types";
import {favoritesQuery, getParentPath, MOCK_VIRTUAL, mockFile, resolvePath} from "Utilities/FileUtilities";
import {buildQueryString} from "Utilities/URIUtilities";

export interface VirtualFileTableProps extends LowLevelFileTableProps, VirtualFolderDefinition {
    // Empty
}

export interface VirtualFolderDefinition {
    fakeFolders?: string[];
    loadFolder?: (folder: string, page: number, itemsPerPage: number) => Promise<Page<File>>;
}

export const VirtualFileTable: React.FunctionComponent<VirtualFileTableProps> = props => {
    const [loadedFakeFolder, setLoadedFakeFolder] = useState<Page<File> | undefined>(undefined);
    const mergedProperties = {...props};
    const asyncWorker = props.asyncWorker ? props.asyncWorker : useAsyncWork();
    mergedProperties.asyncWorker = asyncWorker;
    const [pageLoading, pageError, submitPageLoaderJob] = asyncWorker;

    let fakeFolderToUse: string | undefined;
    if (props.fakeFolders !== undefined && props.loadFolder !== undefined) {
        if (props.path !== undefined) {
            const resolvedPath = resolvePath(props.path);
            fakeFolderToUse = props.fakeFolders.find(it => resolvePath(it) === resolvedPath);
        }

        mergedProperties.page = loadedFakeFolder;

        mergedProperties.onPageChanged = (page, itemsPerPage) => {
            if (fakeFolderToUse !== undefined) {
                const capturedFolder = fakeFolderToUse;
                submitPageLoaderJob(async () => {
                    setLoadedFakeFolder(await props.loadFolder!(capturedFolder, page, itemsPerPage));
                });
            } else if (props.onPageChanged !== undefined) {
                props.onPageChanged(page, itemsPerPage);
            }
        };

        mergedProperties.onReloadRequested = () => {
            if (fakeFolderToUse !== undefined && loadedFakeFolder !== undefined) {
                const capturedFolder = fakeFolderToUse;
                submitPageLoaderJob(async () => {
                    setLoadedFakeFolder(
                        await props.loadFolder!(capturedFolder, loadedFakeFolder.pageNumber,
                            loadedFakeFolder.itemsPerPage)
                    );
                });
            } else if (props.onReloadRequested !== undefined) {
                props.onReloadRequested();
            }
        };
    }

    useEffect(() => {
        if (fakeFolderToUse !== undefined && props.loadFolder !== undefined) {
            const capturedFolder = fakeFolderToUse;
            submitPageLoaderJob(async () => {
                setLoadedFakeFolder(await props.loadFolder!(capturedFolder, 0, 25));
            });
        } else {
            setLoadedFakeFolder(undefined);
        }
    }, [props.path, props.fakeFolders, props.loadFolder]);

    mergedProperties.injectedFiles = useMemo(() => {
        const base = props.injectedFiles ? props.injectedFiles.slice() : [];
        if (props.fakeFolders !== undefined && props.loadFolder !== undefined && props.path !== undefined) {
            const resolvedPath = resolvePath(props.path);
            props.fakeFolders
                .filter(it => resolvePath(getParentPath(resolvePath(it))) === resolvedPath)
                .forEach(it => base.push(mockFile({
                    path: it,
                    fileId: `fakeFolder-${it}`,
                    tag: MOCK_VIRTUAL,
                    type: "DIRECTORY"
                })));
        }

        return base;
    }, [props.fakeFolders, props.loadFolder, props.injectedFiles, props.path]);

    return <LowLevelFileTable {...mergedProperties}/>;
};

const SHARES_FOLDER = "Shares";
const FAVORITES_FOLDER = "Favorites";
const APP_FS_FOLDER = "App File Systems";
export const defaultVirtualFolders: () => VirtualFolderDefinition = () => ({
    fakeFolders: [
        Cloud.homeFolder + SHARES_FOLDER,
        Cloud.homeFolder + FAVORITES_FOLDER,
        Cloud.homeFolder + APP_FS_FOLDER
    ],

    loadFolder: async (folder, page, itemsPerPage) => {
        if (folder === Cloud.homeFolder + FAVORITES_FOLDER) {
            return (await Cloud.get<Page<File>>(favoritesQuery(page, itemsPerPage))).response;
        } else if (folder === Cloud.homeFolder + SHARES_FOLDER) {
            return (await Cloud.get<Page<File>>(buildQueryString("/shares/list-files", {page, itemsPerPage}))).response;
        }  else if (folder === Cloud.homeFolder + APP_FS_FOLDER) {
            return (await Cloud.get<Page<File>>(buildQueryString("/app/fs/fs-compat", {page, itemsPerPage}))).response;
        } else {
            return emptyPage;
        }
    }
});