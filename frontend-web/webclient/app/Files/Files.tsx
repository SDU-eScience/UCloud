import * as React from "react";
import {APICallState, useCloudAPI, useCloudCommand} from "Authentication/DataHook";
import {BulkResponse, file, PageV2} from "UCloud";
import {useToggleSet} from "Utilities/ToggleSet";
import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {useGlobal} from "Utilities/ReduxHooks";
import {bulkRequestOf} from "DefaultObjects";
import {getParentPath, pathComponents, resolvePath, sizeToString} from "Utilities/FileUtilities";
import {BreadCrumbsBase} from "ui-components/Breadcrumbs";
import HexSpin from "LoadingIcon/LoadingIcon";
import {extensionFromPath, joinToString} from "UtilityFunctions";
import {Box, Flex, FtIcon, List} from "ui-components";
import {ListRow, ListRowStat, ListStatContainer} from "ui-components/List";
import {NamingField} from "UtilityComponents";
import {dateToString} from "Utilities/DateUtilities";
import {Operation, Operations} from "ui-components/Operation";
import MainContainer from "MainContainer/MainContainer";
import Icon, {IconName} from "ui-components/Icon";
import FileBrowser, {CommonFileProps} from "Files/FileBrowser";
import UFile = file.orchestrator.UFile;
import filesApi = file.orchestrator.files;
import collectionsApi = file.orchestrator.collections;
import FileCollection = file.orchestrator.FileCollection;
import ReactModal from "react-modal";
import {FileType} from "./";
import FilesMoveRequestItem = file.orchestrator.FilesMoveRequestItem;
import {EmbeddedShareCard} from "Files/Shares";
import FileMetadataAttached = file.orchestrator.FileMetadataAttached;
import {associateBy} from "Utilities/CollectionUtilities";
import metadataApi = file.orchestrator.metadata;
import {buildQueryString} from "Utilities/URIUtilities";
import {removeUploadFromStorage} from "./ChunkedFileReader";

function fileName(path: string): string {
    const lastSlash = path.lastIndexOf("/");
    if (lastSlash !== -1 && path.length > lastSlash + 1) {
        return path.substring(lastSlash + 1);
    } else {
        return path;
    }
}

export const Files: React.FunctionComponent<CommonFileProps & {
    files: APICallState<PageV2<UFile>>;
}> = props => {
    const [commandLoading, invokeCommand] = useCloudCommand();
    const [collection, fetchCollection] = useCloudAPI<FileCollection | null>({noop: true}, null);

    const toggleSet = useToggleSet(props.files.data.items);

    const creatingFolderRef = useRef<HTMLInputElement>(null);
    const renameRef = useRef<HTMLInputElement>(null);

    const [renaming, setRenaming] = useState<string | null>(null);
    const [isCreatingFolder, setIsCreatingFolder] = useState(false);
    const [selectFileRequirement, setSelectFileRequirement] = useState<FileType | undefined>(undefined);
    const [onSelectFile, setOnSelectFile] = useState<((file: UFile | null) => void) | null>(null);
    const [sharing, setSharing] = useState<UFile | null>(null);
    const [favoriteCache, setFavoriteCache] = useState<Record<string, true>>({});

    const [, setUploaderVisible] = useGlobal("uploaderVisible", false);

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

    const startShare = useCallback((batch: UFile) => {
        setSharing(batch);
    }, []);

    const closeShare = useCallback(() => {
        setSharing(null);
    }, []);

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
        batch.forEach(f => removeUploadFromStorage(f.path));
        reload();
    }, [commandLoading, reload]);

    const download = useCallback(async (batch: UFile[]) => {
        const result = await invokeCommand<BulkResponse<file.orchestrator.FilesCreateDownloadResponseItem>>(
            filesApi.createDownload(bulkRequestOf(
                ...batch.map(it => ({path: it.path})),
            ))
        );

        console.log(result?.responses);
    }, []);

    const emptyTrash = useCallback(async (batch: UFile[]) => {
        // TODO
    }, [commandLoading, reload]);

    const selectFile = useCallback((type: FileType) => {
        return new Promise<UFile>((resolve, reject) => {
            setSelectFileRequirement(type);
            setOnSelectFile((prev) => (file) => {
                resolve(file);
                setOnSelectFile(null);
            });
        });
    }, [setOnSelectFile]);

    const closeFileSelector = useCallback(() => {
        onSelectFile?.(null);
        setOnSelectFile(null);
    }, [onSelectFile, setOnSelectFile]);

    const callbacks: FilesCallbacks = {
        ...props, reload, startRenaming, startFolderCreation, openUploader, trash, selectFile, startShare, download, emptyTrash
    };

    const renameFile = useCallback(async () => {
        if (!renaming) return;

        await props.invokeCommand(filesApi.move(bulkRequestOf(
            {
                conflictPolicy: "REJECT",
                oldPath: renaming,
                newPath: getParentPath(renaming) + renameRef.current?.value
            }
        )));

        removeUploadFromStorage(renaming);

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

    useEffect(() => {
        const components = pathComponents(props.path);
        if (components.length >= 4) {
            const provider = components[0];
            const collectionId = components[3];

            if (collection.data?.id !== collectionId && !collection.loading) {
                fetchCollection(collectionsApi.retrieve({id: collectionId, provider}));
            }
        }
    }, [props.path]);

    useEffect(() => {
        const result: Record<string, true> = {};
        for (const path in props.metadata) {
            if (!props.metadata.hasOwnProperty(path)) continue;

            const metadata = props.metadata[path];
            let metadataByType: Record<string, FileMetadataAttached> = {};
            if (metadata) {
                metadataByType = associateBy(metadata, it => it.metadata.specification.templateId);
            }

            result[path] = metadataByType["favorite"]?.metadata?.specification?.document?.["favorite"];
        }
        setFavoriteCache(result);
    }, [props.metadata]);

    const breadcrumbsComponent = useMemo((): JSX.Element => {
        const components = pathComponents(props.path);
        let breadcrumbs: string[] = [];
        let breadcrumbOffset = 0;
        if (components.length >= 4) {
            if (collection.data !== null) {
                breadcrumbs.push(collection.data.specification.title)
                for (let i = 4; i < components.length; i++) {
                    breadcrumbs.push(components[i]);
                }
                breadcrumbOffset = 3;
            }
        } else {
            breadcrumbs = components;
        }

        return <>
            <BreadCrumbsBase embedded={props.embedded}>
                {breadcrumbs.length === 0 ? <HexSpin size={42} /> : null}
                {breadcrumbs.map((it, idx) => (
                    <span key={it} test-tag={it} title={it}
                        onClick={() =>
                            navigateTo("/" + joinToString(components.slice(0, idx + breadcrumbOffset + 1), "/"))}>
                        {it}
                    </span>
                ))}
            </BreadCrumbsBase>
        </>;
    }, [props.path, props.embedded, collection.data]);

    const fileredOperations = props.embedded ? (
        filesOperations.filter(it => !["Move to...", "Copy to..."].includes(it.text))
    ) : filesOperations;

    const main = <>
        {!props.embedded ? null :
            <Flex>
                <Box flexGrow={1}>{breadcrumbsComponent}</Box>
                <Operations
                    selected={toggleSet.checked.items}
                    location={"TOPBAR"}
                    entityNameSingular={filesEntityName}
                    extra={callbacks}
                    operations={fileredOperations}
                    displayTitle={false}
                />
            </Flex>
        }

        {props.embedded ? null : breadcrumbsComponent}

        <List childPadding={"8px"} bordered={true}>
            {!isCreatingFolder ? null : (
                <ListRow
                    icon={
                        <>
                            <Icon mr={10} size={24} name={"starEmpty"} color={"midGray"} />
                            <FtIcon fileIcon={{type: "DIRECTORY"}} size={"42px"} />
                        </>
                    }
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
            {props.files.data.items.map(it => {
                const isFavorite = favoriteCache[it.path] ?? false;

                return <ListRow
                    key={it.path}
                    icon={
                        <>
                            <Icon
                                mr={10}
                                cursor={"pointer"}
                                size={"24"}
                                name={isFavorite ? "starFilled" : "starEmpty"}
                                color={isFavorite ? "blue" : "midGray"}
                                onClick={() => {
                                    invokeCommand(metadataApi.create(bulkRequestOf({
                                        path: it.path,
                                        metadata: {
                                            templateId: "favorite",
                                            changeLog: "",
                                            document: {"favorite": !isFavorite},
                                            product: undefined
                                        }
                                    })));

                                    setFavoriteCache(prev => {
                                        const newCache = {...prev};
                                        if (isFavorite) {
                                            delete newCache[it.path];
                                        } else {
                                            newCache[it.path] = true;
                                        }
                                        return newCache;
                                    });
                                }}
                                hoverColor={"blue"}
                            />
                            <FtIcon
                                iconHint={it.icon}
                                fileIcon={{type: it.type, ext: extensionFromPath(it.path)}}
                                size={"42px"}
                            />
                        </>
                    }
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
                            operations={fileredOperations}
                            row={it}
                        />
                    }
                    navigate={it.type === "FILE" ? undefined : () => {
                        navigateTo(it.path);
                    }}
                />;
            })}
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
            additional={
                <>
                    <ReactModal
                        isOpen={onSelectFile !== null}
                        style={FileSelectorModalStyle}
                        onRequestClose={closeFileSelector}
                        ariaHideApp={false}
                    >
                        <FileBrowser
                            initialPath={props.path}
                            embedded={true}
                            onSelect={onSelectFile!}
                            selectFileRequirement={selectFileRequirement}
                        />
                    </ReactModal>

                    <ReactModal
                        isOpen={sharing != null}
                        style={FileSelectorModalStyle}
                        onRequestClose={closeShare}
                        ariaHideApp={false}
                    >
                        {!sharing ? null : <EmbeddedShareCard path={sharing.path} />}
                    </ReactModal>
                </>
            }
        />;
    }
    return main;
};

interface FilesCallbacks extends CommonFileProps {
    startRenaming: (file: UFile) => void;
    startFolderCreation: () => void;
    openUploader: () => void;
    trash: (batch: UFile[]) => void;
    selectFile: (requiredType: FileType | null) => Promise<UFile | null>;
    startShare: (batch: UFile) => void;

    /* TODO */
    download: (batch: UFile[]) => void;
    // is batch even needed?
    emptyTrash: (batch: UFile[]) => void;
}

const filesOperations: Operation<UFile, FilesCallbacks>[] = [
    {
        text: "Select",
        icon: "check",
        primary: true,
        enabled: (selected, cb) => selected.length === 1 && cb.onSelect !== undefined &&
            (cb.selectFileRequirement == null || cb.selectFileRequirement == selected[0].type),
        onClick: (selected, cb) => {
            cb.onSelect!(selected[0]);
        }
    },
    {
        text: "Upload files",
        icon: "upload",
        primary: true,
        canAppearInLocation: location => location === "SIDEBAR",
        enabled: (selected, cb) => selected.length === 0 && cb.onSelect === undefined,
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
        onClick: (files, cb) => cb.download(files),
        enabled: selected => selected.every(it => it.type === "FILE"),
    },
    {
        text: "Empty trash",
        icon: "trash",
        primary: false,
        onClick: (selected, cb) => {
            cb.emptyTrash(selected);
        },
        enabled: (files) => files.length === 1 && files.every(it => it.icon === "DIRECTORY_TRASH"),
        color: "red",
        confirm: true,
    },
    {
        text: "Copy to...",
        icon: "copy",
        primary: false,
        onClick: async (selected, cb) => {
            const file = await cb.selectFile("DIRECTORY");
            if (!file) return;

            await cb.invokeCommand(filesApi.copy(bulkRequestOf(
                ...(selected.map<FilesMoveRequestItem>(it => (
                    {
                        conflictPolicy: "RENAME",
                        oldPath: it.path,
                        newPath: file.path + "/" + fileName(it.path)
                    }
                )))
            )));

            cb.reload();
        },
        enabled: selected => selected.length > 0,
    },
    {
        text: "Move to...",
        icon: "move",
        primary: false,
        onClick: async (selected, cb) => {
            const file = await cb.selectFile("DIRECTORY");
            if (!file) return;

            await cb.invokeCommand(filesApi.move(bulkRequestOf(
                ...(selected.map<FilesMoveRequestItem>(it => (
                    {
                        conflictPolicy: "RENAME",
                        oldPath: it.path,
                        newPath: file.path + "/" + fileName(it.path)
                    }
                )))
            )));

            selected.forEach(f => removeUploadFromStorage(f.path));

            cb.reload();
        },
        enabled: selected => selected.length > 0,
    },
    {
        text: "Share with...",
        icon: "share",
        enabled: (selected, cb) => selected.length === 1 && cb.onSelect == null,
        onClick: (selected, cb) => {
            cb.startShare(selected[0]);
        }
    },
    {
        text: "Move to trash",
        icon: "trash",
        confirm: true,
        color: "red",
        primary: false,
        onClick: (selected, cb) => cb.trash(selected),
        enabled: selected => selected.length > 0 && selected.every(f => f.icon !== "DIRECTORY_TRASH"),
    },
    {
        text: "Properties",
        icon: "properties",
        primary: false,
        onClick: (selected, cb) => cb.history.push(buildQueryString("/files/properties/", {path: selected[0].path})),
        enabled: selected => selected.length === 1,
    },
];

const filesEntityName = "File";

const filesAclOptions: {icon: IconName; name: string, title?: string}[] = [
    {icon: "search", name: "READ", title: "Read"},
    {icon: "edit", name: "WRITE", title: "Write"},
];

const FileSelectorModalStyle = {
    content: {
        borderRadius: "6px",
        top: "80px",
        left: "25%",
        right: "25%",
        background: ""
    }
};
