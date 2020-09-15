import {addToPool, listAssignedAddresses, PublicIP, removeFromPool} from "Applications/IPAddresses";
import {useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {Client} from "Authentication/HttpClientInstance";
import {emptyPage} from "DefaultObjects";
import {MainContainer} from "MainContainer/MainContainer";
import {useTitle} from "Navigation/Redux/StatusActions";
import * as React from "react";
import {Box, Flex, List, Text} from "ui-components";
import {ListRow} from "ui-components/List";
import {SidebarPages, useSidebarPage} from "ui-components/Sidebar";
import {capitalized} from "UtilityFunctions";

export function PublicIPPool(): JSX.Element | null {
    const [assignedAddresses, setParams, params] = useCloudAPI<Page<PublicIP>>(
        listAssignedAddresses({itemsPerPage: 25, page: 0}),
        emptyPage
    );

    const [, sendCommand] = useAsyncCommand();

    useSidebarPage(SidebarPages.Admin);
    useTitle("IP Management");

    // addToPool({addresses: [], exceptions: []});
    // removeFromPool({addresses: [], exceptions: []});

    /*

    Uses addToPool/removeFromPool to update the pool of available IPs
    Uses listAssignedAddresses to view which addresses are currently in use

    */

    assignedAddresses.data.items.forEach(it => it.inUseBy = "foo-bar-baz");

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
        />
    );
}
