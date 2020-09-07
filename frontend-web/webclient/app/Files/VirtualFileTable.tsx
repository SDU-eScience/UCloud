import {callAPI, callAPIWithErrorHandler, useAsyncWork} from "Authentication/DataHook";
import {Client} from "Authentication/HttpClientInstance";
import {emptyPage} from "DefaultObjects";
import {File} from "Files/index";
import {LowLevelFileTable, LowLevelFileTableProps} from "Files/LowLevelFileTable";
import * as React from "react";
import {useEffect, useMemo, useState} from "react";
import {
    getParentPath,
    isProjectHome,
    MOCK_VIRTUAL,
    mockFile, projectIdFromPath,
    resolvePath
} from "Utilities/FileUtilities";
import {buildQueryString} from "Utilities/URIUtilities";
import {listFavorites} from "Files/favorite";
import {listRepositoryFiles} from "Project";
import {usePromiseKeeper} from "PromiseKeeper";

export type VirtualFileTableProps = LowLevelFileTableProps & VirtualFolderDefinition;

export interface VirtualFolderDefinition {
    fakeFolders?: string[];
    isFakeFolder?: (folder: string) => boolean;
    loadFolder?: (folder: string, page: number, itemsPerPage: number) => Promise<Page<File>>;
}

export const VirtualFileTable: React.FunctionComponent<VirtualFileTableProps> = props => {
    const [loadedFakeFolder, setLoadedFakeFolder] = useState<Page<File> | undefined>(undefined);
    const mergedProperties = {...props};
    const promises = usePromiseKeeper();
    const asyncWorker = props.asyncWorker ?? useAsyncWork();
    mergedProperties.asyncWorker = asyncWorker;
    const [, , submitPageLoaderJob] = asyncWorker;

    let fakeFolderToUse: string | undefined;
    if (props.fakeFolders !== undefined && props.loadFolder !== undefined) {
        if (props.path !== undefined) {
            const resolvedPath = resolvePath(props.path);
            fakeFolderToUse = props.fakeFolders.find(it => resolvePath(it) === resolvedPath);

            if (!fakeFolderToUse && props.isFakeFolder && props.isFakeFolder(resolvedPath)) {
                fakeFolderToUse = resolvedPath;
            }
        }

        mergedProperties.page = loadedFakeFolder;

        mergedProperties.onPageChanged = async (page, itemsPerPage): Promise<void> => {
            if (fakeFolderToUse !== undefined) {
                const capturedFolder = fakeFolderToUse;
                submitPageLoaderJob(async () => {
                    const result = await props.loadFolder?.(capturedFolder, page, itemsPerPage);
                    if (promises.canceledKeeper) return;
                    setLoadedFakeFolder(result);
                });
            } else if (props.onPageChanged !== undefined) {
                props.onPageChanged(page, itemsPerPage);
            }
        };

        mergedProperties.onReloadRequested = (): void => {
            if (fakeFolderToUse !== undefined && loadedFakeFolder !== undefined) {
                const capturedFolder = fakeFolderToUse;
                submitPageLoaderJob(async () => {
                    const result = await props.loadFolder?.(capturedFolder, loadedFakeFolder.pageNumber,
                        loadedFakeFolder.itemsPerPage);
                    if (promises.canceledKeeper) return;
                    setLoadedFakeFolder(result);
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
                const result = await props.loadFolder?.(capturedFolder, 0, 25);
                if (promises.canceledKeeper) return;
                setLoadedFakeFolder(result);
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
    return <LowLevelFileTable {...mergedProperties} />;
};

export const defaultVirtualFolders = (): VirtualFolderDefinition => ({
    fakeFolders: Client.fakeFolders,

    isFakeFolder: folder => {
        if (isProjectHome(folder)) return true;
        return false;
    },

    loadFolder: async (folder, page, itemsPerPage): Promise<Page<File>> => {
        if (folder === Client.favoritesFolder) {
            const favs = (await callAPIWithErrorHandler<Page<File>>(listFavorites({page, itemsPerPage})));
            return favs ?? emptyPage;
        } else if (folder === Client.sharesFolder) {
            return (await Client.get<Page<File>>(
                buildQueryString("/shares/list-files", {page, itemsPerPage}))
            ).response;
        } else if (isProjectHome(folder)) {
            try {
                const id = projectIdFromPath(folder)!;
                const response = await callAPI<Page<File>>(listRepositoryFiles({page, itemsPerPage}, id));
                response.items.forEach(f => f.isRepo = true);
                return response;
            } catch (err) {
                // Edge case that no repos exist for for project, but we want it to be empty instead of non-existant.
                if (err.request.status === 404) return emptyPage;
                else throw err;
            }
        } else {
            return emptyPage;
        }
    }
});
