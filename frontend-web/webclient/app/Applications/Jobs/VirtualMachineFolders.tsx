import * as React from "react";
import {useCallback, useEffect, useMemo, useState} from "react";
import {Box, Icon} from "@/ui-components";
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
import {VirtualMachineRestartReminder} from "./VirtualMachineRestartReminder";
import {VirtualMachineIconButton} from "@/Applications/Jobs/VirtualMachineIconButton";

type VmFolder = compute.AppParameterValueNS.File;

export const VirtualMachineFolders: React.FunctionComponent<{
    jobId: string;
    providerId: string;
    parameters?: Record<string, compute.AppParameterValue>;
    resources?: compute.AppParameterValue[];
    onFolderAdded?: (newFolders: VmFolder[], addedFolder: VmFolder) => Promise<void> | void;
    onFolderRemoved?: (newFolders: VmFolder[], removedFolder: VmFolder) => Promise<void> | void;
    showRestartIndicator?: boolean;
    onRestartRequested?: () => void;
}> = ({jobId, providerId, parameters, resources, onFolderAdded, onFolderRemoved, showRestartIndicator, onRestartRequested}) => {
    const initialFolders = useMemo(() => extractFolders(parameters, resources), [parameters, resources]);
    const [folders, setFolders] = useState<VmFolder[]>(initialFolders);

    useEffect(() => {
        setFolders(initialFolders);
    }, [jobId, initialFolders]);

    const onRemoveFolder = useCallback((idx: number) => {
        const removed = folders[idx];
        if (!removed) return;

        const newFolders = folders.filter((_, i) => i !== idx);
        setFolders(newFolders);

        try {
            const maybePromise = onFolderRemoved?.(newFolders, removed);
            Promise.resolve(maybePromise).catch(() => {
                setFolders(prev => {
                    const alreadyRestored = prev.some(folder => folder.path === removed.path);
                    if (alreadyRestored) return prev;

                    const restored = [...prev];
                    const insertionIndex = Math.min(idx, restored.length);
                    restored.splice(insertionIndex, 0, removed);
                    return restored;
                });
            });
        } catch {
            setFolders(prev => {
                const alreadyRestored = prev.some(folder => folder.path === removed.path);
                if (alreadyRestored) return prev;

                const restored = [...prev];
                const insertionIndex = Math.min(idx, restored.length);
                restored.splice(insertionIndex, 0, removed);
                return restored;
            });
        }
    }, [folders, onFolderRemoved]);

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

            const newFolders = [...folders, addedFolder];
            setFolders(newFolders);

            try {
                const maybePromise = onFolderAdded?.(newFolders, addedFolder);
                Promise.resolve(maybePromise).catch(() => {
                    setFolders(prev => prev.filter(existing => normalize(existing.path) !== addedFolder.path));
                });
            } catch {
                setFolders(prev => prev.filter(existing => normalize(existing.path) !== addedFolder.path));
            }

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
            <div className={VmFolderControls}>
                {!showRestartIndicator ? null : (
                    <VirtualMachineRestartReminder
                        tooltip="Restart the machine for folder changes to take effect"
                        ariaLabel="Restart required for folder changes"
                        onClick={onRestartRequested}
                    />
                )}

                <VirtualMachineIconButton tooltip={"Attach folder"} onClick={openFolderSelector} icon={"heroPlus"} />
            </div>
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
        <VirtualMachineIconButton tooltip={"Remove folder"} onClick={onRemoveFolder} icon={"heroMinus"} />
    </div>;
}

function extractFolders(parameters?: Record<string, compute.AppParameterValue>, resources?: compute.AppParameterValue[]): VmFolder[] {
    return Object.values(parameters ?? {}).concat(resources ?? []).filter((value): value is VmFolder => value?.type === "file");
}

const VmFoldersBody = injectStyle("vm-folders-body", k => `
    ${k} {
        min-height: 165px;
        display: flex;
        flex-direction: column;
        gap: 10px;
    }
`);

const VmFolderControls = injectStyle("vm-folder-controls", k => `
    ${k} {
        display: inline-flex;
        align-items: center;
        gap: 4px;
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
