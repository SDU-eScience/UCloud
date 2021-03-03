import * as React from "react";
import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import * as UCloud from "UCloud";
import {accounting, compute, PageV2} from "UCloud";
import {callAPI, InvokeCommand, useCloudAPI, useCloudCommand} from "Authentication/DataHook";
import {useProjectId} from "Project";
import {emptyPageV2} from "DefaultObjects";
import List, {ListRow, ListRowStat, ListStatContainer} from "ui-components/List";
import {Box, Button, Text} from "ui-components";
import {doNothing, prettierString, shortUUID} from "UtilityFunctions";
import HexSpin from "LoadingIcon/LoadingIcon";
import * as Heading from "ui-components/Heading";
import {Link} from "react-router-dom";
import {useLoading, useTitle} from "Navigation/Redux/StatusActions";
import {SidebarPages, useSidebarPage} from "ui-components/Sidebar";
import ProductNS = accounting.ProductNS;
import networkApi = UCloud.compute.networkips;
import {useRefreshFunction} from "Navigation/Redux/HeaderActions";
import MainContainer from "MainContainer/MainContainer";
import {NoResultsCardBody} from "Dashboard/Dashboard";
import {OperationEnabled, Operation, Operations} from "ui-components/Operation";
import {useToggleSet} from "Utilities/ToggleSet";
import {useHistory} from "react-router";
import {History} from 'history';
import {ProjectStatus, useProjectStatus} from "Project/cache";
import {Client} from "Authentication/HttpClientInstance";
import {isAdminOrPI} from "Utilities/ProjectUtilities";
import {StickyBox} from "ui-components/StickyBox";
import {useScrollStatus} from "Utilities/ScrollStatus";
import NetworkIP = compute.NetworkIP;
import Create from "Applications/NetworkIP/Create";
import {creditFormatter} from "Project/ProjectUsage";
import Inspect from "Applications/NetworkIP/Inspect";
import {entityName} from ".";

export const Browse: React.FunctionComponent<{
    provider?: string;
    onUse?: (instance: UCloud.compute.NetworkIP) => void;
    standalone?: boolean;
}> = ({provider, onUse, standalone}) => {
    const projectId = useProjectId();
    const projectStatus = useProjectStatus();
    const history = useHistory();
    const [networkIps, fetchNetworkIps] = useCloudAPI<PageV2<NetworkIP>>({noop: true}, emptyPageV2);
    const [products, setProducts] = useState<ProductNS.NetworkIP[]>([]);
    const [loadingProducts, setLoadingProducts] = useState(false);
    const [commandLoading, invokeCommand] = useCloudCommand();
    const scrollingContainerRef = useRef<HTMLDivElement>(null);
    const scrollStatus = useScrollStatus(scrollingContainerRef, true);
    const [inspecting, setInspecting] = useState<NetworkIP | null>(null);
    const [isCreating, setIsCreating] = useState(false);
    const toggleSet = useToggleSet(networkIps.data.items);
    const loading = networkIps.loading || loadingProducts || commandLoading;

    const reload = useCallback(() => {
        fetchNetworkIps(UCloud.compute.networkips.browse({
            itemsPerPage: 250,
            includeAcl: true,
            includeUpdates: true,
            includeProduct: true
        }));
        toggleSet.uncheckAll();
    }, [projectId]);

    useEffect(reload, [projectId]);

    const inspect = useCallback((networkIp: NetworkIP) => {
        const product = products.find(it =>
            it.category.id === networkIp.specification.product.category &&
            it.category.provider === networkIp.specification.product.provider &&
            it.id === networkIp.specification.product.id
        );

        if (!product) {
            console.warn("Could not find product", networkIp, product, products);
        } else {
            toggleSet.uncheckAll();
            setInspecting(networkIp);
        }
    }, [setInspecting, products]);

    const startCreation = useCallback(() => {
        setIsCreating(true);
    }, []);

    useEffect(() => {
        let cancel = false;
        if (inspecting !== null) {
            const newInspecting = networkIps.data.items.find(it => it.id === inspecting.id);
            if (newInspecting) {
                // Don't navigate away if it has been deleted in the background
                setInspecting(newInspecting);
            } else {
                (async () => {
                    const retrievedInspecting = await callAPI(networkApi.retrieve({
                        id: inspecting.id,
                        includeProduct: true,
                        includeAcl: true,
                        includeUpdates: true
                    }));

                    if (!cancel) setInspecting(retrievedInspecting);
                })();
            }
        }

        return () => {
            cancel = true;
        }
    }, [networkIps]);

    const callbacks: OpCallback = useMemo(() => {
        return {
            commandLoading, invokeCommand, reload, history, onUse: onUse ?? doNothing,
            standalone: standalone === true, projectStatus, projectId, inspect,
            startCreation
        }
    }, [commandLoading, invokeCommand, reload, onUse, projectStatus, projectId, inspect]);

    if (standalone === true) {
        // NOTE(Dan): Technically breaking rules of hooks. Please don't switch around the standalone prop after
        // mounting it.

        useTitle("Public IP Addresses");
        useSidebarPage(SidebarPages.AppStore);
        useLoading(loading);
        useRefreshFunction(reload);
    }

    /*
     * Effects
     */
    useEffect(() => {
        // Fetch all products when the available types change
        let didCancel = false;
        setLoadingProducts(true);

        (async () => {
            const res = await callAPI(
                UCloud.accounting.products.browse({
                    filterProvider: provider,
                    filterUsable: true,
                    filterArea: "NETWORK_IP",
                    itemsPerPage: 250,
                    includeBalance: true
                })
            );

            const allProducts: ProductNS.NetworkIP[] = res.items
                .filter(it => it.type === "network_ip")
                .map(it => it as ProductNS.NetworkIP)

            if (!didCancel) {
                setLoadingProducts(false);
                setProducts(allProducts);
            }
        })();

        return () => {
            didCancel = true;
        };
    }, [projectId]);

    let main: JSX.Element;
    if (isCreating) {
        main = <>
            <Create computeProvider={provider} onCreateFinished={(ip) => {
                setInspecting(ip);
                setIsCreating(false);
            }}/>
        </>
    } else if (inspecting === null) {
        main = <>
            {loading && networkIps.data.items.length === 0 ? <HexSpin/> : null}
            {networkIps.data.items.length === 0 && !loading ?
                <>
                    <NoResultsCardBody title={"You don't have any public IPs available"}>
                        <Text>
                            Public IP addresses allow you to exposes the services of your application via the public
                            Internet. You can apply to use a public IP when you apply for resources.
                        </Text>
                    </NoResultsCardBody>
                </> : null
            }
            <List childPadding={"8px"} bordered={false}>
                {networkIps.data.items.map(g => (
                    <React.Fragment key={g.id}>
                        <ListRow
                            isSelected={toggleSet.checked.has(g)}
                            select={() => toggleSet.toggle(g)}
                            left={<Text>{g.status.ipAddress ?? "No address"} ({shortUUID(g.id)})</Text>}
                            leftSub={
                                <ListStatContainer>
                                    <ListRowStat icon={"id"}>
                                        {g.specification.product.provider} / {g.specification.product.category}
                                    </ListRowStat>
                                    <ListRowStat icon={"hashtag"}>{prettierString(g.status.state)}</ListRowStat>
                                    {!g.status.boundTo ? null :
                                        <ListRowStat icon={"apps"}>Bound to {shortUUID(g.status.boundTo)}</ListRowStat>}
                                    <ListRowStat icon={"grant"}>
                                        {creditFormatter(g.billing.pricePerUnit)}
                                    </ListRowStat>
                                    <ListRowStat
                                        icon={"info"}
                                        textColor={(g.specification.firewall?.openPorts?.length ?? 0) > 0 ? undefined : "red"}
                                    >
                                        {g.specification.firewall?.openPorts?.length ?? 0} firewall rules
                                    </ListRowStat>
                                    {(g.acl?.length ?? 0) === 0 ?
                                        <ListRowStat icon={"warning"} color={"red"} textColor={"red"}
                                                     cursor={"pointer"} onClick={() => setInspecting(g)}>
                                            Usable only by project admins
                                        </ListRowStat> :
                                        null
                                    }
                                </ListStatContainer>
                            }
                            right={
                                <>
                                    <Operations location={"IN_ROW"} operations={operations}
                                                selected={toggleSet.checked.items} extra={callbacks}
                                                entityNameSingular={entityName} row={g}/>
                                </>
                            }
                        />
                    </React.Fragment>
                ))}
            </List>
        </>;
    } else {
        main = <Inspect inspecting={inspecting} close={() => setInspecting(null)} reload={reload} />;
    }

    if (standalone) {
        return <MainContainer
            header={<Heading.h2>{entityName}</Heading.h2>}
            main={main}
            sidebar={
                <>
                    <Operations
                        location={"SIDEBAR"}
                        operations={operations}
                        selected={toggleSet.checked.items}
                        extra={callbacks}
                        entityNameSingular={entityName}
                    />
                </>
            }
        />;
    } else {
        return <Box ref={scrollingContainerRef}>
            <StickyBox shadow={!scrollStatus.isAtTheTop} normalMarginX={"20px"}>
                <Operations selected={toggleSet.checked.items} location={"TOPBAR"} entityNameSingular={entityName}
                            extra={callbacks} operations={operations}/>
            </StickyBox>

            {main}
        </Box>;
    }
};

const BrowseStandalone: React.FunctionComponent = () => {
    return <Browse standalone/>
};

interface OpCallback {
    commandLoading: boolean;
    invokeCommand: InvokeCommand;
    reload: () => void;
    history: History<unknown>;
    onUse: (entity: NetworkIP) => void;
    standalone: boolean;
    projectStatus: ProjectStatus;
    projectId?: string;
    inspect: (entity: NetworkIP) => void;
    startCreation: () => void;
}

function canUse(projectStatus: ProjectStatus, entity: NetworkIP): OperationEnabled {
    if (entity.owner.project === null && entity.owner.createdBy === Client.username) return true;
    const status = projectStatus.fetch();
    const isAdmin = status.membership.some(membership => membership.projectId === entity.owner.project &&
        isAdminOrPI(membership.whoami.role))
    if (isAdmin) return true;

    const isInAcl = entity.acl?.some(entry => {
        if (!entry.permissions.some(p => p === "USE")) return false;
        return status.groups.some(group => {
            return group.project === entry.entity.projectId && group.group === entry.entity.group;
        });
    }) === true;

    if (isInAcl) return true;

    return `You do not have permission to use this ${entityName}. Contact your PI to request access.`;
}

function isEnabled(projectStatus: ProjectStatus, entity: NetworkIP): OperationEnabled {
    const hasPermission = canUse(projectStatus, entity);
    if (hasPermission !== true) return hasPermission;

    if (entity.status.boundTo) return "This IP address is currently in use";

    switch (entity.status.state) {
        case "PREPARING":
            return `Your ${entityName} is currently being prepared. It should be available for use soon.`;
        case "READY":
            return true;
        case "UNAVAILABLE": {
            const allUnavailableUpdates = entity.updates.filter(it => it.state === "UNAVAILABLE");
            if (allUnavailableUpdates.length === 0) {
                return `Your ${entityName} is currently unavailable`;
            } else {
                return allUnavailableUpdates[allUnavailableUpdates.length - 1].status ??
                    `Your ${entityName} is currently unavailable`;
            }
        }
    }
}

function hasPermissionToEdit(cb: OpCallback): OperationEnabled {
    if (cb.projectId) {
        const status = cb.projectStatus.fetch();
        const isAdmin = status.membership.some(membership => membership.projectId === cb.projectId &&
            isAdminOrPI(membership.whoami.role))
        if (!isAdmin) return `Only PIs of a project can change a ${entityName}`;
    }
    return true;
}

const operations: Operation<NetworkIP, OpCallback>[] = [
    {
        text: "Allocate IP address",
        icon: "upload",
        color: "blue",
        primary: true,
        canAppearInLocation: (loc) => loc !== "IN_ROW",
        enabled: (selected) => {
            return selected.length === 0;
        },
        onClick: (selected, cb) => {
            cb.startCreation();
        }
    },
    {
        text: "Use",
        primary: true,
        enabled: (selected, cb) => {
            if (cb.standalone) return false;
            if (selected.length !== 1) return false;
            return isEnabled(cb.projectStatus, selected[0]);
        },
        onClick: (selected, cb) => {
            cb.onUse(selected[0]);
        }
    },
    {
        icon: "trash",
        text: "Delete",
        color: "red",
        confirm: true,
        enabled(selected, cb) {
            if (selected.length <= 0) return false;
            return hasPermissionToEdit(cb);
        },
        onClick: async (selected, cb) => {
            if (cb.commandLoading) return;

            await cb.invokeCommand(networkApi.remove({
                type: "bulk",
                items: selected.map(entity => ({
                    id: entity.id
                }))
            }));

            cb.reload();
        }
    },
    {
        text: "Properties",
        icon: "properties",
        enabled: selected => selected.length === 1,
        onClick: (selected, cb) => {
            cb.inspect(selected[0]);
        }
    }
];

export default BrowseStandalone;
