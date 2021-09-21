import { useCloudAPI, useCloudCommand } from "Authentication/DataHook";
import { bulkRequestOf, placeholderProduct } from "DefaultObjects";
import React from "react";
import { useState } from "react";
import { UFile } from "UCloud/FilesApi";
import SyncFolderApi, { SyncFolder } from "UCloud/SyncFolderApi"
import { Box, Button, Flex, Input, Label, SelectableText, SelectableTextWrapper } from "ui-components";
import { TextSpan } from "ui-components/Text";
import { copyToClipboard } from "UtilityFunctions";
import { useEffect } from "react";
import SyncDeviceApi, { SyncDevice } from "UCloud/SyncDeviceApi";
import { PageV2 } from "UCloud";
import { ResourceBrowse } from "Resource/Browse";
import { Toggle } from "ui-components/Toggle";

const Tab: React.FunctionComponent<{ selected: boolean, onClick: () => void }> = props => {
    return <SelectableText
        mr="1em"
        cursor="pointer"
        selected={props.selected}
        fontSize={3}
        onClick={props.onClick}
    >
        {props.children}
    </SelectableText>
};

export const SynchronizationSettings: React.FunctionComponent<{
    file: UFile;
    onDeviceSelect?: (selection: SyncDevice) => void;
}> = ({file, onDeviceSelect}) => {
    const [manageDevices, setManageDevices] = useState(false);
    const [folders, fetchFolders] = useCloudAPI<PageV2<SyncFolder> | undefined>(
        SyncFolderApi.browse({itemsPerPage: 1, filterByPath: file.id }),
        undefined
    );

    const [folderProducts, fetchFolderProducts] = useCloudAPI(
        SyncFolderApi.retrieveProducts(),
        undefined
    );

    const [syncFolder, setSyncFolder] = useState<SyncFolder|undefined>(undefined);

    const [loading, invokeCommand] = useCloudCommand();
    const [ucloudDeviceId, setUcloudDeviceId] = useState<string|undefined>(undefined);

    useEffect(() => {
        if (syncFolder && syncFolder.status) {
            setUcloudDeviceId(syncFolder.status.deviceId);
        }
    }, [syncFolder]);

    useEffect(() => {
        if (folders.data && folders.data.items.length > 0) {
            setSyncFolder(folders.data.items[0]);
        }
    }, [folders]);

    const toggleSyncFolder = async () => {
        if (!syncFolder) {
            if (folderProducts.data?.productsByProvider["ucloud"][0].support) {
                await invokeCommand(
                    SyncFolderApi.create(
                        bulkRequestOf({ path: file.id, product: folderProducts.data.productsByProvider["ucloud"][0].support.product})
                    ), {defaultErrorHandler: false}
                );
                fetchFolders(SyncFolderApi.browse({ itemsPerPage: 1, filterByPath: file.id }));

            } else {
                await invokeCommand(
                    SyncFolderApi.create(
                        bulkRequestOf({ path: file.id, product: placeholderProduct()})
                    ), {defaultErrorHandler: false}
                );
                fetchFolders(SyncFolderApi.browse({ itemsPerPage: 1, filterByPath: file.id}));
            }

        } else {
            if (syncFolder) {
                console.log(syncFolder.id);
                await invokeCommand(SyncFolderApi.remove(bulkRequestOf({ id: syncFolder.id })));
                setUcloudDeviceId(undefined);
                setSyncFolder(undefined);
            }
        }
    };

    return <>
        <SelectableTextWrapper>
            <Tab selected={!manageDevices} onClick={() => setManageDevices(false)}>Synchronize folder</Tab>
            <Tab selected={manageDevices} onClick={() => setManageDevices(true)}>Manage devices</Tab>
        </SelectableTextWrapper>
        {manageDevices ? (
            <>
                <ResourceBrowse
                    header={<></>}
                    api={SyncDeviceApi}
                    onSelect={onDeviceSelect}
                    embedded={true}
                    onInlineCreation={((text, product, cb) => ({
                            product: {id: product.name, category: product.category.name, provider: product.category.provider},
                            deviceId: text
                        })
                    )}
                />
            </>
        ) : (
            <>
                <Box mt="30px">To use the synchronization feature you have to set up a local instance of <a href="https://syncthing.net">Syncthing</a> on your device and add your device ID in Manage devices.</Box>
                <Box mt="30px" mb="30px">
                    <Label>
                        <Flex>
                            <Toggle
                                scale={1.5}
                                activeColor="--green"
                                checked={syncFolder !== undefined}
                                onChange={() => toggleSyncFolder()}
                            />
                            <TextSpan ml={20} fontSize={2}>Add {file.id} to synchronization</TextSpan>
                        </Flex>
                    </Label>

                </Box>

                {ucloudDeviceId ? (
                    <>
                        <Box mb="20px">
                            The folder is being synchronized with your devices from Device ID:
                        </Box>
                        <Flex>
                        <Input readOnly={true} value={ucloudDeviceId} />
                        <Button onClick={() => copyToClipboard({
                            value: ucloudDeviceId,
                            message: "Copied " + ucloudDeviceId + " to clipboard"
                        })}>Copy</Button>
                        </Flex>
                        <Box mt="20px">
                            Add this as a remote device to your local instance of Syncthing to begin synchronization.
                        </Box>
                    </>
                ) : (<></>)}
            </>
        )}
    </>
}
 