import * as React from "react";
import {useEffect, useMemo, useState} from "react";
import {LowLevelFilesTable, LowLevelFilesTableProps} from "Files/LowLevelFilesTable";
import {Page} from "Types";
import {favoritesQuery, getParentPath, newMockFolder, resolvePath} from "Utilities/FileUtilities";
import {File} from "Files/index";
import {Cloud} from "Authentication/SDUCloudObject";
import {emptyPage} from "DefaultObjects";
import {buildQueryString} from "Utilities/URIUtilities";

export interface VirtualFilesTableProps extends LowLevelFilesTableProps, VirtualFolderDefinition {
    // Empty
}

export interface VirtualFolderDefinition {
    fakeFolders?: string[]
    loadFolder?: (folder: string, page: number, itemsPerPage: number) => Promise<Page<File>>
}

export const VirtualFilesTable: React.FunctionComponent<VirtualFilesTableProps> = props => {
    const [loadedFakeFolder, setLoadedFakeFolder] = useState<Page<File> | undefined>(undefined);
    const mergedProperties = {...props};

    let fakeFolderToUse: string | undefined;
    if (props.fakeFolders !== undefined && props.loadFolder !== undefined) {
        if (props.path !== undefined) {
            let resolvedPath = resolvePath(props.path);
            fakeFolderToUse = props.fakeFolders.find(it => resolvePath(it) === resolvedPath);
        }

        mergedProperties.page = loadedFakeFolder;

        mergedProperties.onPageChanged = (page, itemsPerPage) => {
            if (fakeFolderToUse !== undefined) {
                props.loadFolder!(fakeFolderToUse, page, itemsPerPage).then(it => setLoadedFakeFolder(it));
            } else if (props.onPageChanged !== undefined) {
                props.onPageChanged(page, itemsPerPage);
            }
        };

        mergedProperties.onReloadRequested = () => {
            if (fakeFolderToUse !== undefined && loadedFakeFolder !== undefined) {
                props.loadFolder!(fakeFolderToUse, loadedFakeFolder.pageNumber, loadedFakeFolder.itemsPerPage);
            } else if (props.onReloadRequested !== undefined) {
                props.onReloadRequested();
            }
        };
    }

    useEffect(() => {
        if (fakeFolderToUse !== undefined && props.loadFolder !== undefined) {
            props.loadFolder(fakeFolderToUse, 0, 25).then(it => setLoadedFakeFolder(it));
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
                .forEach(it => base.push(newMockFolder(it, false, `fakeFolder-${it}`)));
        }

        return base;
    }, [props.fakeFolders, props.loadFolder, props.injectedFiles, props.path]);

    return <LowLevelFilesTable {...mergedProperties}/>;
};

export const defaultVirtualFolders: () => VirtualFolderDefinition = () => ({
    fakeFolders: [
        Cloud.homeFolder + "Shares",
        Cloud.homeFolder + "Favorites"
    ],

    loadFolder: async (folder, page, itemsPerPage) => {
        if (folder === Cloud.homeFolder + "Favorites") {
            return (await Cloud.get<Page<File>>(favoritesQuery(page, itemsPerPage))).response;
        } else if (folder === Cloud.homeFolder + "Shares") {
            return (await Cloud.get<Page<File>>(buildQueryString("/shares/list-files", {page, itemsPerPage}))).response;
        } else {
            return emptyPage;
        }
    }
});