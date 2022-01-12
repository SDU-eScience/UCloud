import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {bulkRequestOf, emptyPageV2} from "@/DefaultObjects";
import React from "react";
import {useState} from "react";
import {UFile} from "@/UCloud/FilesApi";
import SyncFolderApi, {
    SyncFolder,
    SyncFolderSupport,
} from "@/UCloud/SyncFolderApi";
import {
    Box,
    Button,
    Flex,
    Input,
    Label,
    SelectableText,
    SelectableTextWrapper,
} from "@/ui-components";
import {TextSpan} from "@/ui-components/Text";
import {copyToClipboard} from "@/UtilityFunctions";
import {useEffect} from "react";
import SyncDeviceApi, {SyncDevice} from "@/UCloud/SyncDeviceApi";
import {PageV2} from "@/UCloud";
import {ResourceBrowse} from "@/Resource/Browse";
import {Toggle} from "@/ui-components/Toggle";
import {Client} from "@/Authentication/HttpClientInstance";
import {ProductSyncFolder} from "@/Accounting";
import {SupportByProvider} from "@/UCloud/ResourceApi";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {BrowseType} from "@/Resource/BrowseType";
import Spinner from "@/LoadingIcon/LoadingIcon";

const Tab: React.FunctionComponent<{
    selected: boolean;
    onClick: () => void;
}> = (props) => {
    return (
        <SelectableText
            mr="1em"
            cursor="pointer"
            selected={props.selected}
            fontSize={3}
            onClick={props.onClick}
        >
            {props.children}
        </SelectableText>
    );
};

export const SynchronizationSettings: React.FunctionComponent<{
    file: UFile;
    provider: string;
    onSuccess: () => void;
    onDeviceSelect?: (selection: SyncDevice) => void;
}> = ({file, provider, onSuccess, onDeviceSelect}) => {
    const [manageDevices, setManageDevices] = useState(false);
    const [folders, fetchFolders] = useCloudAPI<PageV2<SyncFolder>>(
        SyncFolderApi.browse({
            itemsPerPage: 1,
            filterByPath: file.id
        }),
        emptyPageV2
    );

    const [folderProducts, fetchFolderProducts] = useCloudAPI<
        SupportByProvider<ProductSyncFolder, SyncFolderSupport> | undefined
    >(SyncFolderApi.retrieveProducts(), undefined);

    const [syncFolder, setSyncFolder] = useState<SyncFolder | undefined>(
        undefined
    );

    const [loading, invokeCommand] = useCloudCommand();
    const [ucloudDeviceId, setUcloudDeviceId] = useState<string | undefined>(
        undefined
    );

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
            const support = folderProducts.data?.productsByProvider[provider]?.[0].support;
            if (support != null) {
                await invokeCommand(
                    SyncFolderApi.create(
                        bulkRequestOf({
                            path: file.id,
                            product:
                                support.product,
                        })
                    ),
                    {defaultErrorHandler: false}
                );
                fetchFolders(
                    SyncFolderApi.browse({
                        itemsPerPage: 1,
                        filterByPath: file.id,
                    })
                );
            } else {
                snackbarStore.addFailure(
                    "Synchronization not supported by provider",
                    false
                );
            }
        } else {
            if (syncFolder) {
                await invokeCommand(
                    SyncFolderApi.remove(bulkRequestOf({id: syncFolder.id}))
                );
                setUcloudDeviceId(undefined);
                setSyncFolder(undefined);
            }
        }
        onSuccess();
    };

    return (
        <>
            <SelectableTextWrapper>
                <Tab
                    selected={!manageDevices}
                    onClick={() => setManageDevices(false)}
                >
                    Synchronize folder
                </Tab>
                <Tab
                    selected={manageDevices}
                    onClick={() => setManageDevices(true)}
                >
                    Manage devices
                </Tab>
            </SelectableTextWrapper>
            {manageDevices ? (
                <>
                    <ResourceBrowse
                        header={<></>}
                        api={SyncDeviceApi}
                        onSelect={onDeviceSelect}
                        browseType={BrowseType.Embedded}
                        onInlineCreation={(text, product, cb) => ({
                            product: {
                                id: product.name,
                                category: product.category.name,
                                provider: product.category.provider,
                            },
                            deviceId: text,
                        })}
                        showProduct={false}
                    />
                </>
            ) : (
                <>
                    <Box mt="30px">
                        To use the synchronization feature you have to set up a
                        local instance of{" "}
                        <a href="https://syncthing.net">Syncthing</a> on your
                        device and add your device ID in Manage devices.
                    </Box>
                    <Box mt="30px" mb="30px">
                        <Label>
                            <Flex>
                                <Box width={50} height={25} padding={0}>
                                    { loading ? (
                                        <Box mt="-22px">
                                            <Spinner size={25} />
                                        </Box>
                                    ) : (
                                        <Toggle
                                            scale={1.5}
                                            activeColor="--green"
                                            checked={syncFolder !== undefined}
                                            onChange={toggleSyncFolder}
                                        />
                                    )}
                                </Box>
                                <TextSpan ml={20} fontSize={2}>
                                    Add {file.id} to synchronization
                                </TextSpan>
                            </Flex>
                        </Label>
                    </Box>

                    {ucloudDeviceId ? (
                        <>
                            <Box mb="20px">
                                The folder is being synchronized with your
                                devices from Device ID:
                            </Box>
                            <Flex>
                                <Input readOnly value={ucloudDeviceId} />
                                <Button
                                    onClick={() =>
                                        copyToClipboard({
                                            value: ucloudDeviceId,
                                            message: `Copied ${ucloudDeviceId} to clipboard`,
                                        })
                                    }
                                >
                                    Copy
                                </Button>
                            </Flex>
                            <Box mt="20px">
                                Add this as a remote device to your local
                                instance of Syncthing to begin synchronization.
                            </Box>
                        </>
                    ) : null}
                </>
            )}
        </>
    );
};
