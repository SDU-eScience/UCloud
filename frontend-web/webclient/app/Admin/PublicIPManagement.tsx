import {AddressApplication} from "Applications/IPAddresses";
import {useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {Client} from "Authentication/HttpClientInstance";
import format from "date-fns/format";
import {emptyPage} from "DefaultObjects";
import {MainContainer} from "MainContainer/MainContainer";
import {setRefreshFunction} from "Navigation/Redux/HeaderActions";
import {useTitle} from "Navigation/Redux/StatusActions";
import * as React from "react";
import {useDispatch} from "react-redux";
import styled from "styled-components";
import {Button, ButtonGroup, Flex, Icon, List, Text, Truncate} from "ui-components";
import {ListRow} from "ui-components/List";
import {SidebarPages, useSidebarPage} from "ui-components/Sidebar";
import {addStandardDialog} from "UtilityComponents";

const baseContext = "/hpc/ip/";

function listAddressApplicationsForApprovalRequest(payload: PaginationRequest): APICallParameters<PaginationRequest> {
    return {
        path: `${baseContext}review-applications`,
        method: "GET",
        payload
    };
}

function rejectAddress(id: number): APICallParameters<{id: number}> {
    return {
        path: `${baseContext}reject`,
        method: "POST",
        payload: {id}
    };
}

function acceptAddress(id: number): APICallParameters<{id: number}> {
    return {
        path: `${baseContext}reject`,
        method: "POST",
        payload: {id}
    };
}

export function PublicIPManagement(): JSX.Element | null {
    const [ipsForApproval, setParams, params] = useCloudAPI<Page<AddressApplication>>(
        Client.userIsAdmin ? listAddressApplicationsForApprovalRequest({itemsPerPage: 25, page: 0}) : {noop: true},
        emptyPage);

    const [, sendCommand] = useAsyncCommand();
    const dispatch = useDispatch();
    useSidebarPage(SidebarPages.Admin);
    useTitle("IP Management");

    const reload = (): void => setParams({...params});
    React.useEffect(() => {
        dispatch(setRefreshFunction(() => reload()));
    }, [reload]);

    React.useEffect(() => {
        return () => void dispatch(setRefreshFunction());
    }, []);

    if (!Client.userIsAdmin) return null;

    return (<MainContainer
        main={
            <List>
                {ipsForApproval.data.items.map(it =>
                    <ListRow
                        key={it.id}
                        left={
                            <HoverTruncate width={1}>
                                <b>{it.entityId}: </b>{it.application}
                            </HoverTruncate>
                        }
                        icon={null}
                        leftSub={<Flex>
                            <Text fontSize={0} color="gray">
                                <Icon size="10" name="edit" /> Submitted {format(new Date(it.createdAt), "d LLL yyyy HH:mm")}
                            </Text>
                        </Flex>}
                        right={<ButtonGroup ml="-30px" width="150px">
                            <Button color="green" onClick={() => {
                                addStandardDialog({
                                    title: "Approve IP?",
                                    message: "",
                                    onConfirm: async () => {
                                        await sendCommand(acceptAddress(it.id));
                                        reload();
                                    },
                                    confirmText: "Approve"
                                });
                            }}>Approve</Button>
                            <Button color="red" onClick={async () => {
                                addStandardDialog({
                                    title: "Reject IP?",
                                    message: "",
                                    onConfirm: async () => {
                                        await sendCommand(rejectAddress(it.id));
                                        reload();
                                    },
                                    confirmText: "Reject"
                                });
                            }}>Reject</Button>
                        </ButtonGroup>}
                    />
                )}
            </List >
        }
    />);
}

const HoverTruncate = styled(Truncate)`
    font-size: 20;

    &:hover {
        white-space: normal;
    }
`;
