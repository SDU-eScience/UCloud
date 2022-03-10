import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {bulkRequestOf, emptyPageV2} from "@/DefaultObjects";
import React, {useRef} from "react";
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
    Image,
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
import {ProductSyncFolder} from "@/Accounting";
import {SupportByProvider} from "@/UCloud/ResourceApi";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {BrowseType} from "@/Resource/BrowseType";
import Spinner from "@/LoadingIcon/LoadingIcon";
import {FileCollection} from "@/UCloud/FileCollectionsApi";
import {pathComponents} from "@/Utilities/FileUtilities";
import * as Heading from "@/ui-components/Heading";
import styled from "styled-components";

import syncthingScreen1 from "@/Assets/Images/syncthing/syncthing-1.png";
import syncthingScreen2 from "@/Assets/Images/syncthing/syncthing-2.png";
import syncthingScreen3 from "@/Assets/Images/syncthing/syncthing-3.png";

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
    collection: FileCollection | undefined;
    provider: string;
    onSuccess: () => void;
    onDeviceSelect?: (selection: SyncDevice) => void;
}> = ({file, collection, provider, onSuccess, onDeviceSelect}) => {
    enum SynchronizationTab {
        Folder,
        Devices,
        Tutorial
    }

    const [activeTab, setActiveTab] = useState(SynchronizationTab.Folder);
    const [tutorialStep, setTutorialStep] = useState(0);
    const tutorialDeviceIdRef = useRef<HTMLInputElement>(null);
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

    const subPathComponents = pathComponents(file.id);
    const fullPath = collection?.specification.title + "/" + subPathComponents.slice(1).join('/');


    const [syncFolder, setSyncFolder] = useState<SyncFolder | undefined>(
        undefined
    );

    const [loading, invokeCommand] = useCloudCommand();
    const [ucloudDeviceId, setUcloudDeviceId] = useState<string | undefined>(
        undefined
    );

    const [devices, fetchDevices] = useCloudAPI<PageV2<SyncDevice>>(
        SyncDeviceApi.browse({
            itemsPerPage: 1
        }),
        emptyPageV2
    );

    function closeTutorial() {
        setTutorialStep(0);
        setActiveTab(SynchronizationTab.Folder);
    }

    async function tutorialNext() {
        if (tutorialStep === 1) {
            if (!tutorialDeviceIdRef.current) {
                return;
            }

            const support = folderProducts.data?.productsByProvider[provider]?.[0].support;

            if (support != null) {
                const addDeviceResult = await invokeCommand(
                    SyncDeviceApi.create(
                        bulkRequestOf({
                            deviceId: tutorialDeviceIdRef.current.value,
                            product:
                                support.product,
                        })
                    )
                );

                if (!addDeviceResult) {
                    return;
                }
            }
        }

        if (tutorialStep > 1) {
            closeTutorial();
        } else {
            setTutorialStep(tutorialStep + 1);
        }
    }

    useEffect(() => {
        if (syncFolder && syncFolder.status) {
            setUcloudDeviceId(syncFolder.status.remoteDevice);
        }
    }, [syncFolder]);

    useEffect(() => {
        if (folders.data && folders.data.items.length > 0) {
            setSyncFolder(folders.data.items[0]);
        }
    }, [folders]);

    useEffect(() => {
        if (devices.data && !devices.loading && devices.data.items.length < 1) {
            setActiveTab(SynchronizationTab.Tutorial);
        }
    }, [devices]);

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

    const TabContent: React.FunctionComponent<{}> = () => {
        switch (activeTab) {
            case SynchronizationTab.Folder: {
                return (
                    <>
                        <Box borderRadius="6px" backgroundColor="orange" color="white" p={16} mt={16}>
                        The synchronization feature is experimental, and should be used at your own risk. Please report any errors through the Support Form.
                        </Box>
                        <Box mt="30px">
                            The synchronization feature requires you to set up a
                            local instance of{" "}
                            <a target="blank" href="https://syncthing.net">Syncthing</a> on your
                            device. See the tutorial for instructions.
                        </Box>
                        <Box mt="30px" mb="30px">
                            <Label fontWeight={"normal"}>
                                <Flex>
                                    <Box width={50} height={25} padding={0}>
                                        {loading ? (
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
                                        Add <b>{fullPath}</b> to synchronization
                                    </TextSpan>
                                </Flex>
                            </Label>
                        </Box>

                        {ucloudDeviceId ? (
                            <>
                                <p>
                                    Go to <a target="blank" href="http://localhost:8384/">http://localhost:8384/</a> to add this folder to your device.
                                </p>
                                <p>
                                    UCloud will share this folder from Syncthing Device ID:
                                </p>
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
                            </>
                        ) : null}
                    </>
                );
            }
            case SynchronizationTab.Devices: {
                return (
                    <Box mt={10}>
                        <ResourceBrowse
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
                    </Box>
                );
            }
            case SynchronizationTab.Tutorial: {
                var tutorialContent;
                switch (tutorialStep) {
                    case 0: {
                        tutorialContent = (
                            <>
                                <Heading.h3>Synchronization Tutorial - Installing Syncthing</Heading.h3>
                                <Box borderRadius="6px" backgroundColor="orange" color="white" p={16} mt={16}>
                                The synchronization feature is experimental, and should be used at your own risk. Please report any errors through the Support Form.
                                </Box>
                                <p>The synchronization feature allows you to synchronize folders between UCloud and your devices.</p>
                                <p>For synchronization to work, you need to install the 3rd-party tool <a target="blank" href="https://syncthing.net">Syncthing</a>.</p>
                                <TutorialList>
                                    <li>Download Syncthing for your platform from <a target="blank" href="https://syncthing.net/downloads/">https://syncthing.net/downloads/</a> or from the package manager on your system.</li>
                                    <li>Install Syncthing on your device. See the <a target="blank" href="https://docs.cloud.sdu.dk/guide/syncthing.html">UCloud documentation</a> for details.</li>
                                    <li>Once Syncthing is installed and running on your device, you should be able to access the Syncthing user interface from <a target="blank" href="http://localhost:8384/">http://localhost:8384/</a>.</li>
                                </TutorialList>
                            </>
                        );
                        break;
                    }
                    case 1: {

                        tutorialContent = (
                            <>
                                <Heading.h3>Synchronization Tutorial - Adding devices</Heading.h3>

                                <p>Now that Syncthing is installed on your device, UCloud need to know the Device ID generated by Syncthing to be able to synchronize your files.</p>
                                <Flex>
                                    <TutorialList>
                                        <li>Go to the Syncthing user interface:<a target="blank" href="http://localhost:8384/">http://localhost:8384/</a>.</li>
                                        <li>
                                            In the <i>Actions</i> menu in the top-right corner, click the <i>Show ID</i> button:
                                        </li>
                                        <li>
                                            A window containing your Device ID, as well as a QR code will appear.<br />
                                            Copy the Device ID and paste it into the field below:
                                        </li>
                                    </TutorialList>

                                    <Box ml={"auto"}>
                                        <Image src={syncthingScreen1} />
                                    </Box>
                                </Flex>

                                <Label>
                                    My Device ID
                                    <Input ref={tutorialDeviceIdRef} />
                                </Label>
                            </>
                        );
                        break;

                    }
                    case 2: {
                        tutorialContent = (
                            <>
                                <Heading.h3>Synchronization Tutorial - Synchronize folder</Heading.h3>
                                <p>Now that UCloud knows about your device, you will be able to add folders on UCloud to synchronization.</p>
                                <TutorialList>
                                    <li>Toggle synchronization of your folder
                                        <Box mt="20px">
                                            <Label fontWeight={"normal"}>
                                                <Flex>
                                                    <Box width={50} height={25} padding={0}>
                                                        {loading ? (
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
                                                    <TextSpan ml={20} fontSize={2} lineHeight={2}>
                                                        Add <b>{fullPath}</b> to synchronization
                                                    </TextSpan>
                                                </Flex>
                                            </Label>
                                        </Box>
                                    </li>
                                    <li>
                                        <Flex>
                                            <p>
                                                Go to the Syncthing User Interface
                                                at <a target="blank" href="http://localhost:8384/">http://localhost:8384/</a>.
                                                A pop-up will appear, saying that UCloud wants to connect. Click the <i>Add
                                                    device</i> button, then <i>Save</i> in the window that appears.
                                            </p>
                                            <Box ml={"auto"} pl={40} width="80%">
                                                <Image src={syncthingScreen2} />
                                            </Box>
                                        </Flex>
                                    </li>

                                    <li>
                                        <Flex>
                                            <p>
                                                A new pop-up will appear, saying that UCloud wants to
                                                share a folder with you. Click <i>Add</i>, then select
                                                where you want Syncthing to synchronize the files to
                                                on your machine by changing <i>Folder Path</i>, then
                                                press <i>Save</i>.
                                            </p>
                                            <Box ml={"auto"} pl={40} width="100%">
                                                <Image src={syncthingScreen3} />
                                            </Box>
                                        </Flex>
                                    </li>
                                </TutorialList>

                                <p>Synchronization should start within a few seconds and changes to files in the folder will be synchronized automatically.</p>

                                <p>For more details see the <a target="blank" href="https://docs.cloud.sdu.dk/guide/syncthing.html">UCloud documentation</a>.</p>
                            </>
                        );
                        break;
                    }
                }

                return (
                    <>
                        {tutorialContent}
                        <Flex mt={30}>
                            <Button onClick={closeTutorial}>Skip tutorial</Button>
                            <Box marginLeft={"auto"}>
                                {tutorialStep < 1 ? null : (
                                    <Button onClick={() => setTutorialStep(tutorialStep - 1)}>Previous step</Button>
                                )}
                                <Button marginLeft={10} onClick={tutorialNext}>
                                    {tutorialStep === 2 ? "Done" : "Next step"}
                                </Button>
                            </Box>
                        </Flex>
                    </>
                );
            }
        }
    };

    return (
        <>
            {activeTab === SynchronizationTab.Tutorial ? null : (
                <SelectableTextWrapper>
                    <Tab
                        selected={activeTab === SynchronizationTab.Folder}
                        onClick={() => setActiveTab(SynchronizationTab.Folder)}
                    >
                        Synchronize folder
                    </Tab>
                    <Tab
                        selected={activeTab === SynchronizationTab.Devices}
                        onClick={() => setActiveTab(SynchronizationTab.Devices)}
                    >
                        Manage devices
                    </Tab>
                    <Tab
                        selected={false}
                        onClick={() => setActiveTab(SynchronizationTab.Tutorial)}
                    >
                        Tutorial
                    </Tab>
                </SelectableTextWrapper>
            )}
            <TabContent />
        </>
    );
};


const TutorialList = styled.ol`
    padding-top: 0.5em;

    & > li {
        padding: 0 0 1.5em 0.5em;
        max-width: 1000px;
    }
`