import * as React from "react";
import {useCallback, useEffect, useMemo, useState} from "react";
import {Box, Button, Icon} from "@/ui-components";
import TabbedCard, {TabbedCardTab} from "@/ui-components/TabbedCard";
import {injectStyle} from "@/Unstyled";
import {dialogStore} from "@/Dialog/DialogStore";
import {api as FilesApi} from "@/UCloud/FilesApi";
import FileBrowse from "@/Files/FileBrowse";
import {Selection} from "@/ui-components/ResourceBrowser";
import {doNothing, removeTrailingSlash} from "@/UtilityFunctions";
import {UFile} from "@/UCloud/UFile";
import {compute} from "@/UCloud";
import {usePrettyFilePath} from "@/Files/FilePath";
import {TooltipV2} from "@/ui-components/Tooltip";

type VmFolder = compute.AppParameterValueNS.File;

export const VirtualMachineFolders: React.FunctionComponent<{
    jobId: string;
    providerId: string;
    parameters?: Record<string, compute.AppParameterValue>;
    onFolderAdded?: (newFolders: VmFolder[], addedFolder: VmFolder) => Promise<void> | void;
    onFolderRemoved?: (newFolders: VmFolder[], removedFolder: VmFolder) => Promise<void> | void;
}> = ({jobId, providerId, parameters, onFolderAdded, onFolderRemoved}) => {
    const initialFolders = useMemo(() => extractFolders(parameters), [parameters]);
    const [folders, setFolders] = useState<VmFolder[]>(initialFolders);

    useEffect(() => {
        setFolders(initialFolders);
    }, [jobId, initialFolders]);

    const onRemoveFolder = useCallback((idx: number) => {
        setFolders(prev => {
            const removed = prev[idx];
            const newFolders = prev.filter((_, i) => i !== idx);
            onFolderRemoved?.(newFolders, removed);
            return newFolders;
        });
    }, [onFolderRemoved]);

    const openFolderSelector = useCallback(() => {
        const normalize = (path: string) => removeTrailingSlash(path);

        const showFolder = (file: UFile): boolean | string => {
            if (file.status.type !== "DIRECTORY") {
                return "Only directories can be attached";
            }

            if (file.specification.product.provider !== providerId) {
                return "Folder must be located in a drive on the same provider as this VM";
            }

            const targetPath = normalize(file.id);
            const alreadyAttached = folders.some(existing => normalize(existing.path) === targetPath);
            if (alreadyAttached) {
                return "Folder is already attached";
            }

            return true;
        };

        const onSelectFolder = (file: UFile) => {
            const addedFolder: VmFolder = {
                type: "file",
                path: normalize(file.id),
                readOnly: false,
            };

            setFolders(prev => {
                const newFolders = [...prev, addedFolder];
                onFolderAdded?.(newFolders, addedFolder);
                return newFolders;
            });

            dialogStore.success();
        };

        const selection: Selection<UFile> = {
            text: "Attach",
            onClick: onSelectFolder,
            show: showFolder,
        };

        dialogStore.addDialog(
            <FileBrowse
                opts={{
                    initialPath: "",
                    additionalFilters: {filterProvider: providerId},
                    isModal: true,
                    managesLocalProject: true,
                    selection,
                }}
            />,
            doNothing,
            true,
            FilesApi.fileSelectorModalStyle
        );
    }, [folders, onFolderAdded, providerId]);

    return <TabbedCard
        style={{minHeight: "240px"}}
        rightControlsPaddingRight="20px"
        rightControls={
            <TooltipV2 tooltip={"Attach folder"}>
                <button type="button" className={AddFolderControl} onClick={openFolderSelector} aria-label="Attach folder">
                    <Icon name="heroPlus" />
                </button>
            </TooltipV2>
        }
    >
        <TabbedCardTab icon="heroFolderOpen" name="Folders">
            <div className={VmFoldersBody}>
                {folders.length === 0 ? (
                    <Box color="textSecondary">No folders are currently attached to this VM.</Box>
                ) : (
                    <div className={VmFolderList}>
                        {folders.map((folder, idx) => <FolderRow
                            key={`${folder.path}-${idx}`}
                            folder={folder}
                            onRemoveFolder={() => onRemoveFolder(idx)}
                        />)}
                    </div>
                )}
            </div>
        </TabbedCardTab>
    </TabbedCard>;
};

const FolderRow: React.FunctionComponent<{
    folder: compute.AppParameterValueNS.File;
    onRemoveFolder: () => void;
}> = ({folder, onRemoveFolder}) => {
    const path = usePrettyFilePath(folder.path);

    return <div className={VmFolderRow} >
        <Icon name={"ftFolder"} size={"24px"} color={"FtFolderColor"} color2={"FtFolderColor2"} />
        <div className={VmFolderPath} title={path}>{path}</div>
        <TooltipV2 tooltip={"Remove folder"}>
            <button type="button" className={AddFolderControl} onClick={onRemoveFolder} aria-label="Remove folder">
                <Icon name="heroMinus" />
            </button>
        </TooltipV2>
    </div>;
}

function extractFolders(parameters?: Record<string, compute.AppParameterValue>): VmFolder[] {
    if (!parameters) return [];
    return Object.values(parameters)
        .filter((value): value is VmFolder => value?.type === "file");
}

const VmFoldersBody = injectStyle("vm-folders-body", k => `
    ${k} {
        min-height: 165px;
        display: flex;
        flex-direction: column;
        gap: 10px;
    }
`);

const AddFolderControl = injectStyle("add-folder-control", k => `
    ${k} {
        border: 0;
        background: transparent;
        color: var(--textSecondary);
        cursor: pointer;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 28px;
        height: 28px;
        border-radius: 6px;
        transition: background-color 120ms ease-in-out, color 120ms ease-in-out;
    }

    ${k}:hover {
        background: color-mix(in srgb, var(--primaryMain) 12%, transparent);
        color: var(--textPrimary);
    }

    ${k}:focus-visible {
        outline: 2px solid var(--primaryMain);
        outline-offset: 1px;
    }
`);

const VmFolderList = injectStyle("vm-folder-list", k => `
    ${k} {
        display: flex;
        flex-direction: column;
        gap: 8px;
        max-height: 240px;
        overflow-y: auto;
    }
`);

const VmFolderRow = injectStyle("vm-folder-row", k => `
    ${k} {
        display: grid;
        grid-template-columns: auto minmax(0, 1fr) auto;
        gap: 16px;
        align-items: center;
    }
`);

const VmFolderPath = injectStyle("vm-folder-path", k => `
    ${k} {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }
`);
