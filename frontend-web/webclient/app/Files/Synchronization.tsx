import { useCloudAPI, useCloudCommand } from "Authentication/DataHook";
import * as Pagination from "Pagination";
import {snackbarStore} from "Snackbar/SnackbarStore";
import React, { useCallback, useEffect, useRef, useState } from "react";
import { Box, Button, Checkbox, Flex, Icon, Input, Label, SelectableText, SelectableTextWrapper } from "ui-components";
import List, { ListRow } from "ui-components/List";
import { Text } from "ui-components";
import {file, PageV2} from "UCloud";
import sync = file.synchronization;
import { bulkRequestOf, emptyPageV2 } from "DefaultObjects";
import { TextSpan } from "ui-components/Text";
import { copyToClipboard } from "UtilityFunctions";
import { pathComponents } from "Utilities/FileUtilities";

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
    path: string;
}> = ({path}) => {
    const provider = pathComponents(path)[0];

    const [manageDevices, setManageDevices] = useState(false);
    const [devices, fetchDevices] = useCloudAPI<PageV2<file.SynchronizationDevice>>(
        sync.browseDevices({ provider, itemsPerPage: 25, next: undefined }),
        emptyPageV2
    );
    const [folder, fetchFolder] = useCloudAPI<file.SynchronizedFolder|undefined>(
        sync.retrieveFolder({ path: path, provider }),
        undefined
    );

    const loadMore = useCallback(() => {
        if (devices.data.next) {
            fetchDevices(sync.browseDevices({ provider, next: devices.data.next }));
        }
    }, [devices.data.next]);


    const deviceIdRef = useRef<HTMLInputElement>(null);
    const [loading, invokeCommand] = useCloudCommand();
    const [synchronizedFolder, setSynchronizedFolder] = useState<file.SynchronizedFolder|undefined>(undefined);
    const [ucloudDeviceId, setUcloudDeviceId] = useState<string|undefined>(undefined);

    const addDevice = async e => {
        e.preventDefault();
        if (deviceIdRef.current && deviceIdRef.current.value.length > 0) {
            await invokeCommand(sync.addDevice(bulkRequestOf({ id: deviceIdRef.current.value, provider })));
            fetchDevices(sync.browseDevices({ provider, itemsPerPage: 25, next: undefined}), true);
            deviceIdRef.current.value = "";
            snackbarStore.addSuccess("Added device", false);
        } else {
            snackbarStore.addFailure("Device ID cannot be empty", false);
        }
    };

    const removeDevice = async (id: string) => {
        await invokeCommand(sync.removeDevice(bulkRequestOf({ id, provider })));
        fetchDevices(sync.browseDevices({ provider, itemsPerPage: 25, next: undefined }), true);
        snackbarStore.addSuccess("Removed device", false);
    };


    useEffect(() => {
        if (synchronizedFolder) {
            setUcloudDeviceId(synchronizedFolder.device);
        }
    }, [synchronizedFolder]);

    useEffect(() => {
        if (folder.data) {
            setSynchronizedFolder(folder.data);
        }
    }, [folder]);


    const toggleSynchronizeFolder = async () => {
        if (!synchronizedFolder) {
            await invokeCommand(sync.addFolder(bulkRequestOf({ path, provider })));
            fetchFolder(sync.retrieveFolder({ path, provider }), true);
        } else {
            if (folder.data) {
                await invokeCommand(sync.removeFolder(bulkRequestOf({ id: folder.data.id, provider })));
                setUcloudDeviceId(undefined);
                setSynchronizedFolder(undefined);
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
                                                {d.id}
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
                <Text mt="30px">To use the synchronization feature you have to set up a local instance of <a href="https://syncthing.net">Syncthing</a> on your device and add your device ID in Manage devices.</Text>
                <Box mt="30px" mb="30px">
                    <Label>
                        <Checkbox
                            checked={synchronizedFolder !== undefined}
                            onChange={() => toggleSynchronizeFolder()}
                        />
                        <TextSpan fontSize={2}>Add {path} to synchronization</TextSpan>
                    </Label>

                </Box>

                {ucloudDeviceId ? (
                    <>
                        <Text mb="20px">
                            The folder is being synchronized with your devices from Device ID:
                        </Text>
                        <Flex>
                        <Input readOnly={true} value={ucloudDeviceId} />
                        <Button onClick={() => copyToClipboard({
                            value: ucloudDeviceId,
                            message: "Copied " + ucloudDeviceId + " to clipboard"
                        })}>Copy</Button>
                        </Flex>
                        <Text mt="20px">
                            Add this as a remote device to your local instance of Syncthing to begin synchronization.
                        </Text>
                    </>
                ) : (<></>)}
            </>
        )}
    </>
};