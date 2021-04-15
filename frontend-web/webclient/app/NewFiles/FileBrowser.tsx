import * as React from "react";
import {useHistory, useLocation} from "react-router";
import {useProjectId} from "Project";
import {useCallback, useEffect, useState} from "react";
import {InvokeCommand, useCloudAPI, useCloudCommand} from "Authentication/DataHook";
import {file, PageV2} from "UCloud";
import UFile = file.orchestrator.UFile;
import {emptyPageV2} from "DefaultObjects";
import FileCollection = file.orchestrator.FileCollection;
import filesApi = file.orchestrator.files;
import collectionsApi = file.orchestrator.collections;
import {useLoading, useTitle} from "Navigation/Redux/StatusActions";
import {SidebarPages, useSidebarPage} from "ui-components/Sidebar";
import {buildQueryString, getQueryParam} from "Utilities/URIUtilities";
import {UCLOUD_PROVIDER} from "Accounting";
import {useRefreshFunction} from "Navigation/Redux/HeaderActions";
import {useGlobal} from "Utilities/ReduxHooks";
import {FileCollections} from "NewFiles/FileCollections";
import {Files} from "NewFiles/Files";
import {FileType} from "NewFiles/index";

interface FileBrowserProps {
    initialPath?: string;
    embedded?: boolean;
    onSelect?: (file: UFile) => void;
    selectFileRequirement?: FileType;
}

export interface CommonFileProps {
    path: string;
    reload: () => void;
    loadMore: () => void;
    navigateTo: (path: string) => void;
    generation: number;
    embedded: boolean;
    invokeCommand: InvokeCommand;
    onSelect?: (file: UFile) => void;
    selectFileRequirement?: FileType;
}

const FileBrowser: React.FunctionComponent<FileBrowserProps> = props => {
    const projectId = useProjectId();
    const params = useLocation();
    const pathFromQuery = getQueryParam(params.search, "path");
    const history = useHistory();
    const [pathFromProps, setPath] = useState(props.initialPath);
    const path = pathFromProps ?? pathFromQuery ?? "/";
    const [commandLoading, invokeCommand] = useCloudCommand();
    const [uploadPath, setUploadPath] = useGlobal("uploadPath", "/");

    const [files, fetchFiles] = useCloudAPI<PageV2<UFile>>({noop: true}, emptyPageV2);
    const [collections, fetchCollections] = useCloudAPI<PageV2<FileCollection>>({noop: true}, emptyPageV2);
    const [generation, setGeneration] = useState(0);

    const reload = useCallback((): void => {
        if (path === "/") {
            fetchCollections(collectionsApi.browse({provider: UCLOUD_PROVIDER, itemsPerPage: 50}));
        } else {
            fetchFiles(filesApi.browse({itemsPerPage: 50, path}));
        }
        setGeneration(gen => gen + 1);
    }, [path]);

    const loadMore = useCallback(() => {
        if (path === "/") {
            fetchCollections(collectionsApi.browse({
                provider: UCLOUD_PROVIDER,
                itemsPerPage: 50,
                next: collections.data.next
            }));
        } else {
            fetchFiles(filesApi.browse({itemsPerPage: 50, next: files.data.next, path}));
        }
    }, [path, files, collections]);

    const navigateTo = useCallback((newPath: string) => {
        if (props.initialPath) {
            setPath(newPath);
        } else {
            history.push(buildQueryString("/files", {path: newPath}));
        }
    }, [props.initialPath]);

    useEffect(() => reload(), [path, projectId]);

    useEffect(() => {
        setUploadPath(path);
    }, [path]);

    if (props.embedded !== true) { // NOTE(Dan): I know, we are breaking rules of hooks
        useTitle("Files");
        useSidebarPage(SidebarPages.Files);
        useLoading(files.loading || collections.loading || commandLoading);
        useRefreshFunction(reload);
    }

    const commonProps: CommonFileProps = {
        reload,
        loadMore,
        generation,
        navigateTo,
        embedded: props.embedded === true,
        invokeCommand,
        path,
        onSelect: props.onSelect,
        selectFileRequirement: props.selectFileRequirement,
    };

    if (path === "/") {
        return <FileCollections provider={UCLOUD_PROVIDER} collections={collections} {...commonProps} />;
    } else {
        return <Files files={files} {...commonProps} />;
    }
};

export default FileBrowser;