import {addToPool, listAssignedAddresses, listAvailableAddresses, PublicIP, removeFromPool} from "Applications/IPAddresses";
import {useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {Client} from "Authentication/HttpClientInstance";
import {emptyPage} from "DefaultObjects";
import {RemoveButton} from "Files/FileInputSelector";
import {MainContainer} from "MainContainer/MainContainer";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {useTitle} from "Navigation/Redux/StatusActions";
import * as React from "react";
import * as Heading from "ui-components/Heading";
import * as ReactModal from "react-modal";
import {useDispatch} from "react-redux";
import {snackbarStore} from "Snackbar/SnackbarStore";
import styled from "styled-components";
import {Box, Button, Flex, Icon, Input, List, Text} from "ui-components";
import {ListRow} from "ui-components/List";
import {SidebarPages, useSidebarPage} from "ui-components/Sidebar";
import {defaultModalStyle} from "Utilities/ModalUtilities";
import {capitalized, errorMessageOrDefault} from "UtilityFunctions";
import { dialogStore } from "Dialog/DialogStore";
import { addStandardDialog } from "UtilityComponents";

// https://stackoverflow.com/questions/49306970/correct-input-type-for-ip-address
const cidrRegex =
    /^((\d{1,2}|1\d\d|2[0-4]\d|25[0-5])\.){3}(\d{1,2}|1\d\d|2[0-4]\d|25[0-5])(\/([0-9]|[0-2][0-9]|[3][0-2]))??$/;

enum PoolManagement {
    CLOSED,
    ADD,
    REMOVE
}

const Wrapper = styled.div`
    width: 800px;
    height: 80vh;
`;

const InnerWrapper = styled.div`
    height: calc(100% - 90px);
    overflow-y: auto;
    overflow-x: hidden;
`;



export function PublicIPPool(): JSX.Element | null {
    const [assignedAddresses, setAssignedAddressesParams, assignedAddressesParams] = useCloudAPI<Page<PublicIP>>(
        listAssignedAddresses({itemsPerPage: 25, page: 0}),
        emptyPage
    );

    const [availableAddresses, setAvailableAddressesParams, availableAddressesParams] = useCloudAPI<Page<string>>(
        listAvailableAddresses({itemsPerPage: 25, page: 0}),
        emptyPage
    );

    const [management, setManagement] = React.useState(PoolManagement.CLOSED);
    const [selectedIp, setSelectedIp] = React.useState<number>(0);

    const [, sendCommand] = useAsyncCommand();

    useSidebarPage(SidebarPages.Admin);
    useTitle("IP Management");

    const dispatch = useDispatch();

    const refresh = (): void => {
        setAssignedAddressesParams({...assignedAddressesParams});
        setAvailableAddressesParams({...availableAddressesParams});
    }

    React.useEffect(() => {
        dispatch(setRefreshFunction(refresh));
    }, [refresh]);

    React.useEffect(() => {
        return () => void dispatch(setRefreshFunction());
    }, []);

    if (!Client.userIsAdmin) return null;
    return (
        <MainContainer
            header={
                <Heading.h2>Public IP Pool</Heading.h2>
            }
            main={
                <Box maxWidth={1000} ml="auto" mr="auto">
                    <Heading.h3>Assigned</Heading.h3>
                    <List>
                        {assignedAddresses.data.items.map(address =>
                            <>
                                <ListRow
                                    key={address.id}
                                    navigate={() =>
                                        setSelectedIp(address.id)
                                    }
                                    left={
                                        <Flex>
                                            <Text>{address.ipAddress}</Text>
                                        </Flex>
                                    }
                                    select={() => 
                                        setSelectedIp(address.id)
                                    }
                                    leftSub={
                                        <>
                                            <Text fontSize={1} color="gray" pr={1}>{capitalized(address.entityType)}:</Text>
                                            <Text fontSize={1}>{address.ownerEntity}</Text>
                                        </>
                                    }
                                    right={
                                        <Text color="gray" ml="8px">{address.inUseBy ? `In use by: ${address.inUseBy}` : "Not in use"}</Text>
                                    }
                                    isSelected={selectedIp === address.id}
                                />
                                <ReactModal
                                    isOpen={selectedIp === address.id}
                                    onRequestClose={() => setSelectedIp(0)}
                                    shouldCloseOnEsc
                                    shouldCloseOnOverlayClick
                                    ariaHideApp
                                    style={{content: {...defaultModalStyle.content, height: "auto", maxHeight: undefined}}}
                                >
                                    <Wrapper>
                                        <Heading.h3>Ports open for {address.ipAddress} ({address.openPorts.length})</Heading.h3>
                                        <InnerWrapper>
                                                {address.openPorts.length > 0 ? (
                                                    <List mt={20}>
                                                        {address.openPorts.map(port => 
                                                            <ListRow
                                                                key={port.protocol + port.port}
                                                                left={port.port}
                                                                right={port.protocol}
                                                            />
                                                        )}
                                                    </List>                                          
                                                ) : (
                                                    <Text mt={40} textAlign="center">No open ports</Text>
                                                )}
                                        </InnerWrapper>
                                        <Button onClick={() => setSelectedIp(0)} width="100%" mt={20}>Close</Button>
                                    </Wrapper>
                                </ReactModal>

                            </>
                        )}
                    </List>
                    <Heading.h3 mt={40}>Available</Heading.h3>
                    <List>
                        {availableAddresses.data.items.map(address =>
                            <>
                                <ListRow
                                    key={address}
                                    left={
                                        <Flex>
                                            <Text>{address}</Text>
                                        </Flex>
                                    }
                                    right={
                                        <Button
                                            onClick={() => {
                                                addStandardDialog({
                                                    title: "Are you sure?",
                                                    message: `Remove ${address} from IP Pool?`,
                                                    onConfirm: async () => {
                                                        try {
                                                            await sendCommand(removeFromPool({addresses: [address], exceptions: []}));
                                                            refresh();
                                                        } catch (e) {
                                                            snackbarStore.addFailure(errorMessageOrDefault(e, "Failed to remove IP"), false);
                                                        }

                                                        refresh();
                                                    },
                                                });
                                            }}
                                            color={"red"}
                                            pl={2}
                                            pr={2}
                                        >
                                            <Icon name="trash" size="16" />
                                        </Button>
                                    }
                                />
                            </>
                        )}
                    </List>

                </Box>
            }
            sidebar={<Box>
                <Button width={1} my="8px" onClick={() => setManagement(PoolManagement.ADD)} color="green">
                    Add IPs
                </Button>
                <Button width={1} my="8px" onClick={() => setManagement(PoolManagement.REMOVE)} color="red">
                    Remove IPs
                </Button>
            </Box>}
            additional={<IPModal state={management} refresh={refresh} closeModal={() => setManagement(PoolManagement.CLOSED)} />}
        />
    );
}

function IPModal(props: {state: PoolManagement; closeModal: () => void; refresh: () => void;}): JSX.Element {
    const [ips, setIps] = React.useState([React.createRef<HTMLInputElement>()]);
    const [exclusions, setExclusions] = React.useState([React.createRef<HTMLInputElement>()]);
    const add = props.state === PoolManagement.ADD;
    const [, sendCommand] = useAsyncCommand();

    async function addIps(): Promise<void> {
        const regex = RegExp(cidrRegex);
        const validIPs = ips
            .filter(it => regex.test(it.current?.value ?? "")).map(it => it.current?.value ?? "").filter(it => it);
        const validCIDRs = exclusions
            .filter(it => regex.test(it.current?.value ?? "")).map(it => it.current?.value ?? "").filter(it => it);

        try {
            await sendCommand(addToPool({addresses: validIPs, exceptions: validCIDRs}));
            props.refresh();
            props.closeModal();
        } catch (e) {
            snackbarStore.addFailure(errorMessageOrDefault(e, "Failed to add IPs"), false);
        }
    }

    async function removeIps(): Promise<void> {
        const regex = RegExp(cidrRegex);
        const validIPs = ips
            .filter(it => regex.test(it.current?.value ?? "")).map(it => it.current?.value ?? "").filter(it => it);
        const matchCIDR = RegExp(cidrRegex);
        const validCIDRs = exclusions
            .filter(it => matchCIDR.test(it.current?.value ?? "")).map(it => it.current?.value ?? "").filter(it => it);
        try {
            await sendCommand(removeFromPool({addresses: validIPs, exceptions: validCIDRs}));
            props.refresh();
            props.closeModal();
        } catch (e) {
            snackbarStore.addFailure(errorMessageOrDefault(e, "Failed to remove IPs"), false);
        }
    }

    return (
        <ReactModal
            style={defaultModalStyle}
            shouldCloseOnEsc
            shouldCloseOnOverlayClick
            isOpen={props.state === PoolManagement.ADD || props.state === PoolManagement.REMOVE}
            onRequestClose={props.closeModal}
        >
            <Flex>
                <Text>Adresses</Text>
                <Button
                    ml="8px"
                    height="22px"
                    width="44px"
                    onClick={() => {ips.push(React.createRef()); setIps([...ips]);}}
                >New</Button>
            </Flex>
            <IPAddress>
                {ips.map((ref, index) =>
                    <Flex my="6px" key={index}>
                        <Input
                            type="text"
                            minLength={7}
                            required
                            maxLength={18}
                            size={18}
                            ref={ref}
                            placeholder="###.###.###.###/##"
                            autoComplete="off"
                            pattern={cidrRegex.source}
                        />
                        <RemoveButton onClick={() => ref.current != null ? ref.current.value = "" : undefined} />
                    </Flex>
                )}
            </IPAddress>
            <Flex>
                <Text>Exclusions</Text>
                <Button
                    ml="8px"
                    height="22px"
                    width="44px"
                    onClick={() => {exclusions.push(React.createRef()); setExclusions([...exclusions]);}}
                >New</Button>
            </Flex>
            <IPAddress>
                {exclusions.map((ref, index) =>
                    <Flex my="6px" key={index}>
                        <Input
                            type="text"
                            minLength={7}
                            required
                            maxLength={18}
                            size={18}
                            ref={ref}
                            placeholder="###.###.###.###/##"
                            autoComplete="off"
                            pattern={cidrRegex.source}
                        />
                        <RemoveButton onClick={() => ref.current != null ? ref.current.value = "" : undefined} />
                    </Flex>)}
            </IPAddress>
            <Button onClick={() => add ? addIps() : removeIps()} color={add ? "green" : "red"}>
                {add ? "Add" : "Remove"}
            </Button>
        </ReactModal>
    );
}

const IPAddress = styled(Box)`
    & > ${Flex} > input {
        text-align: right;
    }
`;
