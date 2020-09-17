import {addToPool, listAssignedAddresses, PublicIP, removeFromPool} from "Applications/IPAddresses";
import {useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {Client} from "Authentication/HttpClientInstance";
import {emptyPage} from "DefaultObjects";
import {RemoveButton} from "Files/FileInputSelector";
import {MainContainer} from "MainContainer/MainContainer";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {useTitle} from "Navigation/Redux/StatusActions";
import * as React from "react";
import * as ReactModal from "react-modal";
import {useDispatch} from "react-redux";
import {snackbarStore} from "Snackbar/SnackbarStore";
import styled from "styled-components";
import {Box, Button, Flex, Input, List, Text} from "ui-components";
import {ListRow} from "ui-components/List";
import {SidebarPages, useSidebarPage} from "ui-components/Sidebar";
import {defaultModalStyle} from "Utilities/ModalUtilities";
import {capitalized, errorMessageOrDefault} from "UtilityFunctions";

// https://stackoverflow.com/questions/49306970/correct-input-type-for-ip-address
const cidrRegex =
    /^((\d{1,2}|1\d\d|2[0-4]\d|25[0-5])\.){3}(\d{1,2}|1\d\d|2[0-4]\d|25[0-5])(\/([0-9]|[0-2][0-9]|[3][0-2]))??$/;

enum PoolManagement {
    CLOSED,
    ADD,
    REMOVE
}


export function PublicIPPool(): JSX.Element | null {
    const [assignedAddresses, setParams, params] = useCloudAPI<Page<PublicIP>>(
        listAssignedAddresses({itemsPerPage: 25, page: 0}),
        emptyPage
    );

    const [management, setManagement] = React.useState(PoolManagement.CLOSED);

    useSidebarPage(SidebarPages.Admin);
    useTitle("IP Management");

    const dispatch = useDispatch();

    const refresh = (): void => setParams({...params});

    React.useEffect(() => {
        dispatch(setRefreshFunction(refresh));
    }, [refresh]);

    React.useEffect(() => {
        return () => void dispatch(setRefreshFunction());
    }, []);

    if (!Client.userIsAdmin) return null;
    return (
        <MainContainer
            main={<List>
                {assignedAddresses.data.items.map(address =>
                    <ListRow
                        key={address.id}
                        left={<Flex>
                            <Text>{address.ownerEntity}</Text>
                            {address.inUseBy ? <Text color="gray" ml="8px"> In use by: {address.inUseBy}</Text> : null}
                        </Flex>}
                        leftSub={<Text fontSize={0} color="gray">{capitalized(address.entityType)}</Text>}
                        right={<>
                            {address.openPorts.map(port =>
                                <Box key={port.protocol + port.port}>
                                    {port.protocol}://{address.ipAddress}:{port.port}
                                </Box>
                            )}
                        </>}
                    />
                )}
            </List>}
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
