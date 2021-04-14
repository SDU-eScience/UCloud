import * as React from "react";
import {useHistory, useLocation, useParams} from "react-router";
import {useProjectId} from "Project";
import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {APICallState, InvokeCommand, useCloudAPI, useCloudCommand} from "Authentication/DataHook";
import {file, PageV2} from "UCloud";
import UFile = file.orchestrator.UFile;
import {bulkRequestOf, emptyPageV2} from "DefaultObjects";
import FileCollection = file.orchestrator.FileCollection;
import filesApi = file.orchestrator.files;
import collectionsApi = file.orchestrator.collections;
import {useLoading, useTitle} from "Navigation/Redux/StatusActions";
import {SidebarPages, useSidebarPage} from "ui-components/Sidebar";
import MainContainer from "MainContainer/MainContainer";
import {buildQueryString, getQueryParam} from "Utilities/URIUtilities";
import {UCLOUD_PROVIDER} from "Accounting";
import {useRefreshFunction} from "Navigation/Redux/HeaderActions";
import {IconName} from "ui-components/Icon";
import {ListRow, ListRowStat, ListStatContainer} from "ui-components/List";
import {creditFormatter} from "Project/ProjectUsage";
import {Operation, Operations} from "ui-components/Operation";
import {FtIcon, List} from "ui-components";
import {useToggleSet} from "Utilities/ToggleSet";
import {
    getParentPath,
    pathComponents, resolvePath,
    sizeToString
} from "Utilities/FileUtilities";
import {dateToString} from "Utilities/DateUtilities";
import {NamingField} from "UtilityComponents";
import {BreadCrumbsBase} from "ui-components/Breadcrumbs";
import HexSpin from "LoadingIcon/LoadingIcon";
import {extensionFromPath, joinToString} from "UtilityFunctions";
import {useGlobal} from "Utilities/ReduxHooks";

interface FileBrowserProps {
    initialPath?: string;
    embedded?: boolean;
}

interface CommonProps {
    path: string;
    reload: () => void;
    loadMore: () => void;
    navigateTo: (path: string) => void;
    generation: number;
    embedded: boolean;
    invokeCommand: InvokeCommand;
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

    const commonProps: CommonProps = {
        reload,
        loadMore,
        generation,
        navigateTo,
        embedded: props.embedded === true,
        invokeCommand,
        path,
    };

    if (path === "/") {
        return <FileCollections collections={collections} {...commonProps} />;
    } else {
        return <Files files={files} {...commonProps} />;
    }
};

function fileName(path: string): string {
    const lastSlash = path.lastIndexOf("/");
    if (lastSlash !== -1 && path.length > lastSlash + 1) {
        return path.substring(lastSlash + 1);
    } else {
        return path;
    }
}

const Files: React.FunctionComponent<CommonProps & {
    files: APICallState<PageV2<UFile>>;
}> = props => {
    const toggleSet = useToggleSet(props.files.data.items);
    const [renaming, setRenaming] = useState<string | null>(null);
    const [collection, fetchCollection] = useCloudAPI<FileCollection | null>({noop: true}, null);
    const [isCreatingFolder, setIsCreatingFolder] = useState(false);
    const [commandLoading, invokeCommand] = useCloudCommand();
    const creatingFolderRef = useRef<HTMLInputElement>(null);

    const reload = useCallback(() => {
        toggleSet.uncheckAll();
        props.reload();
        setRenaming(null);
        setIsCreatingFolder(false);
    }, [props.reload]);

    const navigateTo = useCallback((path: string) => {
        props.navigateTo(path);
        setRenaming(null);
        toggleSet.uncheckAll();
    }, [props.navigateTo]);

    const [uploaderVisible, setUploaderVisible] = useGlobal("uploaderVisible", false);
    const openUploader = useCallback(() => {
        setUploaderVisible(true);
    }, []);

    const startFolderCreation = useCallback(() => {
        setIsCreatingFolder(true);
    }, []);

    const startRenaming = useCallback((file: UFile) => {
        setRenaming(file.path);
    }, []);

    const trash = useCallback(async (batch: UFile[]) => {
        if (commandLoading) return;
        await invokeCommand(filesApi.trash(bulkRequestOf(...(batch.map(it => ({path: it.path}))))));
        reload();
    }, [commandLoading, reload]);

    const callbacks: FilesCallbacks = useMemo(
        () => ({...props, reload, startRenaming, startFolderCreation, openUploader, trash}),
        [reload, startRenaming, trash, props]
    );

    const renameRef = useRef<HTMLInputElement>(null);
    const renameFile = useCallback(async () => {
        if (!renaming) return;

        await props.invokeCommand(filesApi.move(bulkRequestOf(
            {
                conflictPolicy: "REJECT",
                oldPath: renaming,
                newPath: getParentPath(renaming) + renameRef.current?.value
            }
        )));

        reload();
    }, [reload, renaming, renameRef]);

    const createFolder = useCallback(async () => {
        if (!isCreatingFolder) return;
        await props.invokeCommand(filesApi.createFolder(bulkRequestOf(
            {
                conflictPolicy: "RENAME",
                path: resolvePath(props.path) + "/" + creatingFolderRef.current?.value
            }
        )));

        reload();
    }, [isCreatingFolder, reload, creatingFolderRef, props.path]);

    const components = pathComponents(props.path);
    let breadcrumbs: string[] = [];
    let breadcrumbOffset = 0;
    if (components.length >= 4) {
        const provider = components[0];
        const collectionId = components[3];

        if (collection.data?.id !== collectionId && !collection.loading) {
            console.log(collection.data?.id, collectionId, collection);
            fetchCollection(collectionsApi.retrieve({id: collectionId, provider}));
        } else if (collection.data !== null) {
            breadcrumbs.push(collection.data.specification.title)
            for (let i = 4; i < components.length; i++) {
                breadcrumbs.push(components[i]);
            }
            breadcrumbOffset = 3;
        }
    } else {
        breadcrumbs = components;
    }

    const main = <>
        <BreadCrumbsBase embedded={props.embedded}>
            {breadcrumbs.length === 0 ? <HexSpin size={42}/> : null}
            {breadcrumbs.map((it, idx) => (
                <span key={it} test-tag={it} title={it}
                      onClick={() =>
                          navigateTo("/" + joinToString(components.slice(0, idx + breadcrumbOffset + 1), "/"))}>
                    {it}
                </span>
            ))}
        </BreadCrumbsBase>
        <List childPadding={"8px"} bordered={false}>
            {!isCreatingFolder ? null : (
                <ListRow
                    icon={<FtIcon fileIcon={{type: "DIRECTORY"}} size={"42px"}/>}
                    left={
                        <NamingField
                            confirmText={"Create"}
                            onCancel={() => setIsCreatingFolder(false)}
                            onSubmit={createFolder}
                            inputRef={creatingFolderRef}
                        />
                    }
                    right={null}
                />
            )}
            {props.files.data.items.map(it =>
                <ListRow
                    key={it.path}
                    icon={<FtIcon fileIcon={{type: it.type, ext: extensionFromPath(it.path)}} size={"42px"}/>}
                    left={
                        renaming === it.path ?
                            <NamingField
                                confirmText="Rename"
                                defaultValue={fileName(it.path)}
                                onCancel={() => setRenaming(null)}
                                onSubmit={renameFile}
                                inputRef={renameRef}
                            /> : fileName(it.path)
                    }
                    isSelected={toggleSet.checked.has(it)}
                    select={() => toggleSet.toggle(it)}
                    leftSub={
                        <ListStatContainer>
                            {it.stats?.sizeIncludingChildrenInBytes == null || it.type !== "DIRECTORY" ? null :
                                <ListRowStat icon={"info"}>
                                    {sizeToString(it.stats.sizeIncludingChildrenInBytes)}
                                </ListRowStat>
                            }
                            {it.stats?.sizeInBytes == null || it.type !== "FILE" ? null :
                                <ListRowStat icon={"info"}>
                                    {sizeToString(it.stats.sizeInBytes)}
                                </ListRowStat>
                            }
                            {!it.stats?.modifiedAt ? null :
                                <ListRowStat icon={"edit"}>
                                    {dateToString(it.stats.modifiedAt)}
                                </ListRowStat>
                            }
                        </ListStatContainer>
                    }
                    right={
                        <Operations
                            selected={toggleSet.checked.items}
                            location={"IN_ROW"}
                            entityNameSingular={filesEntityName}
                            extra={callbacks}
                            operations={filesOperations}
                            row={it}
                        />
                    }
                    navigate={() => {
                        navigateTo(it.path);
                    }}
                />
            )}
        </List>
    </>;

    if (!props.embedded) {
        return <MainContainer
            main={main}
            sidebar={<>
                <Operations
                    location={"SIDEBAR"}
                    operations={filesOperations}
                    selected={toggleSet.checked.items}
                    extra={callbacks}
                    entityNameSingular={filesEntityName}
                />
            </>}
        />;
    }
    return null;
};

// eslint-disable-next-line
interface FilesCallbacks extends CommonProps {
    startRenaming: (file: UFile) => void;
    startFolderCreation: () => void;
    openUploader: () => void;
    trash: (batch: UFile[]) => void;
}

const filesOperations: Operation<UFile, FilesCallbacks>[] = [
    {
        text: "Upload files",
        icon: "upload",
        primary: true,
        canAppearInLocation: location => location === "SIDEBAR",
        enabled: selected => selected.length === 0,
        onClick: (_, cb) => cb.openUploader(),
    },
    {
        text: "New folder",
        icon: "uploadFolder",
        primary: true,
        canAppearInLocation: location => location === "SIDEBAR",
        enabled: selected => selected.length === 0,
        onClick: (_, cb) => cb.startFolderCreation(),
    },
    {
        text: "Rename",
        icon: "rename",
        primary: false,
        onClick: (selected, cb) => cb.startRenaming(selected[0]),
        enabled: selected => selected.length === 1,
    },
    {
        text: "Download",
        icon: "download",
        primary: false,
        onClick: () => 42,
        enabled: selected => selected.length === 1,
    },
    {
        text: "Copy to...",
        icon: "copy",
        primary: false,
        onClick: () => 42,
        enabled: selected => selected.length > 0,
    },
    {
        text: "Move to...",
        icon: "move",
        primary: false,
        onClick: () => 42,
        enabled: selected => selected.length > 0,
    },
    {
        text: "Move to trash",
        icon: "trash",
        confirm: true,
        color: "red",
        primary: false,
        onClick: (selected, cb) => cb.trash(selected),
        enabled: selected => selected.length > 0,
    },
    {
        text: "Properties",
        icon: "properties",
        primary: false,
        onClick: () => 42,
        enabled: selected => selected.length === 1,
    },
];

const filesEntityName = "File";

const filesAclOptions: { icon: IconName; name: string, title?: string }[] = [
    {icon: "search", name: "READ", title: "Read"},
    {icon: "edit", name: "WRITE", title: "Write"},
];

const FileCollections: React.FunctionComponent<CommonProps & {
    collections: APICallState<PageV2<FileCollection>>;
}> = props => {
    const toggleSet = useToggleSet(props.collections.data.items);
    const reload = useCallback(() => {
        toggleSet.uncheckAll();
        props.reload();
    }, [props.reload]);

    const callbacks: CollectionsCallbacks = useMemo(() => ({...props, reload}), [reload, props]);

    const main = <>
        <List childPadding={"8px"} bordered={false}>
            {props.collections.data.items.map(it =>
                <ListRow
                    key={it.id}
                    icon={<FtIcon fileIcon={{type: "DIRECTORY"}} size={"42px"}/>}
                    left={it.specification.title}
                    isSelected={toggleSet.checked.has(it)}
                    select={() => toggleSet.toggle(it)}
                    leftSub={
                        <ListStatContainer>
                            <ListRowStat>
                                {it.specification.product.category} ({it.specification.product.provider})
                            </ListRowStat>
                            <ListRowStat>
                                {creditFormatter(it.billing.pricePerUnit)}
                            </ListRowStat>
                        </ListStatContainer>
                    }
                    right={
                        <Operations
                            selected={toggleSet.checked.items}
                            location={"IN_ROW"}
                            entityNameSingular={collectionsEntityName}
                            extra={callbacks}
                            operations={collectionOperations}
                            row={it}
                        />
                    }
                    navigate={() => {
                        const path = `/${it.specification.product.provider}/${it.specification.product.category}/` +
                            `${it.specification.product.id}/${it.id}`;
                        props.navigateTo(path);
                    }}
                />
            )}
        </List>
    </>;

    if (!props.embedded) {
        return <MainContainer
            main={main}
            sidebar={<>
                <Operations
                    location={"SIDEBAR"}
                    operations={collectionOperations}
                    selected={toggleSet.checked.items}
                    extra={callbacks}
                    entityNameSingular={collectionsEntityName}
                />
            </>}
        />;
    }
    return null;
};

// eslint-disable-next-line
interface CollectionsCallbacks extends CommonProps {
}

const collectionOperations: Operation<FileCollection, CollectionsCallbacks>[] = [
    {
        text: "Delete",
        icon: "trash",
        confirm: true,
        color: "red",
        primary: false,
        onClick: () => 42,
        enabled: selected => selected.length > 0,
    },
    {
        text: "Properties",
        icon: "properties",
        primary: false,
        onClick: () => 42,
        enabled: selected => selected.length > 0,
    },
];

const collectionsEntityName = "Collections";

const collectionAclOptions: { icon: IconName; name: string, title?: string }[] = [
    {icon: "search", name: "READ", title: "Read"},
    {icon: "edit", name: "WRITE", title: "Write"},
];

export default FileBrowser;