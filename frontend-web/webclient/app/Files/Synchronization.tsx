import { apiRetrieve, useCloudAPI, useCloudCommand } from "Authentication/DataHook";
import { bulkRequestOf, emptyPageV2, placeholderProduct } from "DefaultObjects";
import React, { useCallback, useRef } from "react";
import { useState } from "react";
import { UFile } from "UCloud/FilesApi";
import { SupportByProvider } from "UCloud/ResourceApi";
import SyncFolderApi, { SyncFolder } from "UCloud/SyncFolderApi"
import { Box, Button, Checkbox, Flex, Icon, Input, Label, List, SelectableText, SelectableTextWrapper } from "ui-components";
import {PageRenderer} from "Pagination/PaginationV2";
import * as Pagination from "Pagination";
import { TextSpan } from "ui-components/Text";
import { copyToClipboard } from "UtilityFunctions";
import { useEffect } from "react";
import SyncDeviceApi from "UCloud/SyncDeviceApi";
import { Product, ProductSyncFolder } from "Accounting";
import { snackbarStore } from "Snackbar/SnackbarStore";
import { ListRow } from "ui-components/List";
import { BulkResponse, PageV2 } from "UCloud";

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
}> = ({file}) => {
    const [manageDevices, setManageDevices] = useState(false);
    const [devicePage, setDevicePage] = useState(0);
    const [folder, fetchFolder] = useCloudAPI<PageV2<SyncFolder> | undefined>(
        SyncFolderApi.browse({itemsPerPage: 1, filterByPath: file.id }),
        undefined
    );

    const [folderProducts, fetchFolderProducts] = useCloudAPI(
        SyncFolderApi.retrieveProducts(),
        undefined
    );

    const [deviceProducts, fetchDeviceProducts] = useCloudAPI(
        SyncDeviceApi.retrieveProducts(),
        undefined
    );

    const [devices, fetchDevices] = useCloudAPI(
        SyncDeviceApi.browse({itemsPerPage: 25}),
        emptyPageV2
    );

    const [syncFolder, setSyncFolder] = useState<SyncFolder|undefined>(undefined);

    const loadMore = useCallback(() => {
        if (devices.data?.next) {
            fetchDevices(SyncDeviceApi.browse({ itemsPerPage: 25, next: devices.data.next }));
        }
    }, [devices.data?.next]);


    const deviceIdRef = useRef<HTMLInputElement>(null);
    const [loading, invokeCommand] = useCloudCommand();
    const [ucloudDeviceId, setUcloudDeviceId] = useState<string|undefined>(undefined);
    const addDevice = async e => {
        e.preventDefault();
        if (deviceIdRef.current && deviceIdRef.current.value.length > 0) {
            if (deviceProducts.data?.productsByProvider["ucloud"][0].support) {
                await invokeCommand(
                    SyncDeviceApi.create(
                        bulkRequestOf({
                            deviceId: deviceIdRef.current.value,
                            product: deviceProducts.data?. productsByProvider["ucloud"][0].support.product
                        })
                ), {defaultErrorHandler: false});
            }
            SyncDeviceApi
            fetchDevices(SyncDeviceApi.browse({ itemsPerPage: 25, next: undefined}));
            deviceIdRef.current.value = "";
            snackbarStore.addSuccess("Added device", false);
        } else {
            snackbarStore.addFailure("Device ID cannot be empty", false);
        }
    };

    const removeDevice = async (id: string) => {
        await invokeCommand(SyncDeviceApi.remove(bulkRequestOf({  id })));
        fetchDevices(SyncDeviceApi.browse({ itemsPerPage: 25, next: undefined}));
        snackbarStore.addSuccess("Removed device", false);
    };

    useEffect(() => {
        if (syncFolder && syncFolder.status) {
            setUcloudDeviceId(syncFolder.status.deviceId);
        }
    }, [syncFolder]);

    useEffect(() => {
        if (folder.data && folder.data.items.length == 1) {
            setSyncFolder(folder.data.items[0]);
        }
    }, [folder]);

    const toggleSynchronizeFolder = async () => {
        if (!syncFolder) {
            if (folderProducts.data?.productsByProvider["ucloud"][0].support) {
                await invokeCommand(
                    SyncFolderApi.create(
                        bulkRequestOf({ path: file.id, product: folderProducts.data.productsByProvider["ucloud"][0].support.product})
                    ), {defaultErrorHandler: false}
                );
                fetchFolder(SyncFolderApi.browse({ itemsPerPage: 1, filterByPath: file.id }));

            } else {
                await invokeCommand(
                    SyncFolderApi.create(
                        bulkRequestOf({ path: file.id, product: placeholderProduct()})
                    ), {defaultErrorHandler: false}
                );
                fetchFolder(SyncFolderApi.browse({ itemsPerPage: 1, filterByPath: file.id}));
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
                <form onSubmit={addDevice}>
                    <Flex mt={10} mb={10}>
                        <Input ref={deviceIdRef} placeholder="Device ID" />
                        <Button color="green" width="160px">Add device</Button>
                    </Flex>
                </form>
                <Pagination.ListV2
                    page={devices.data}
                    loading={devices.loading}
                    onLoadMore={loadMore}
                    customEmptyPage={"No Syncthing devices added. Add the device ID of Syncthing installed on your device to start synchronization."}
                    pageRenderer={(items) =>
                        <List>
                            {items.map(d => {
                                return (
                                    <ListRow
                                        key={d.id}
                                        left={
                                            <>
                                                {d.specification.deviceId}
                                            </>
                                        } 
                                        right={
                                            <>
                                                <Button color="red" onClick={() => removeDevice(d.id)}>
                                                    <Icon name="trash" size="16px"/>
                                                </Button>
                                            </>
                                        }
                                    />
                                )
                            })}
                        </List>
                    }
                />
            </>
        ) : (
            <>
                <Box mt="30px">To use the synchronization feature you have to set up a local instance of <a href="https://syncthing.net">Syncthing</a> on your device and add your device ID in Manage devices.</Box>
                <Box mt="30px" mb="30px">
                    <Label>
                        <Checkbox
                            checked={syncFolder !== undefined}
                            onChange={() => toggleSynchronizeFolder()}
                        />
                        <TextSpan fontSize={2}>Add {file.id} to synchronization</TextSpan>
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
 