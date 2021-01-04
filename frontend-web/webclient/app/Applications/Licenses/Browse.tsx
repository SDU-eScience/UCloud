import * as React from "react";
import {useCallback, useEffect, useMemo, useState} from "react";
import * as UCloud from "UCloud";
import {accounting, compute, PageV2} from "UCloud";
import {callAPI, InvokeCommand, useCloudAPI, useCloudCommand} from "Authentication/DataHook";
import {groupSummaryRequest, useProjectId} from "Project";
import {emptyPage, emptyPageV2} from "DefaultObjects";
import List, {ListRow, ListRowStat, ListStatContainer} from "ui-components/List";
import {Box, Button, Flex, Icon, RadioTile, RadioTilesContainer, Text, Truncate} from "ui-components";
import {doNothing, prettierString, shortUUID} from "UtilityFunctions";
import {creditFormatter} from "Project/ProjectUsage";
import HexSpin from "LoadingIcon/LoadingIcon";
import * as Heading from "ui-components/Heading";
import {Link} from "react-router-dom";
import {useLoading, useTitle} from "Navigation/Redux/StatusActions";
import {SidebarPages, useSidebarPage} from "ui-components/Sidebar";
import ProductNS = accounting.ProductNS;
import licenseApi = UCloud.compute.licenses;
import {useRefreshFunction} from "Navigation/Redux/HeaderActions";
import MainContainer from "MainContainer/MainContainer";
import {NoResultsCardBody} from "Dashboard/Dashboard";
import License = compute.License;
import {OperationEnabled, Operation, Operations} from "ui-components/Operation";
import {ToggleSet} from "Utilities/ToggleSet";
import equal from "fast-deep-equal";
import {AppToolLogo} from "Applications/AppToolLogo";
import {useHistory} from "react-router";
import {History} from 'history';
import {dateToString} from "Utilities/DateUtilities";
import {addStandardDialog, ShakingBox} from "UtilityComponents";
import {ProjectStatus, useProjectStatus} from "Project/cache";
import {Client} from "Authentication/HttpClientInstance";
import {isAdminOrPI} from "Utilities/ProjectUtilities";
import {GroupWithSummary} from "Project/GroupList";
import * as Pagination from "Pagination";
import {AclPermission} from "Applications/Licenses/index";
import {TextSpan} from "ui-components/Text";
import LicenseAclEntry = compute.LicenseAclEntry;

interface LicenseGroup {
    product: ProductNS.License;
    instances: UCloud.compute.License[];
}

export const Browse: React.FunctionComponent<{
    provider?: string;
    onUse?: (instance: UCloud.compute.License) => void;
    tagged?: string[];
    standalone?: boolean;
}> = ({provider, tagged, onUse, standalone}) => {
    const projectId = useProjectId();
    const projectStatus = useProjectStatus();
    const history = useHistory();
    const [wallets, fetchWallets] = useCloudAPI<UCloud.accounting.RetrieveBalanceResponse>({noop: true}, {wallets: []});
    const [licenses, fetchLicenses] = useCloudAPI<PageV2<License>>({noop: true}, emptyPageV2);
    const [products, setProducts] = useState<ProductNS.License[]>([]);
    const [loadingProducts, setLoadingProducts] = useState(false);
    const [commandLoading, invokeCommand] = useCloudCommand();
    const [inspecting, setInspecting] = useState<{ product: ProductNS.License, license: License } | null>(null);
    const [selected, setSelected] = useState<{ groups: ToggleSet<LicenseGroup>, instances: ToggleSet<License> }>(
        {groups: new ToggleSet(), instances: new ToggleSet()}
    );
    const loading = wallets.loading || licenses.loading || loadingProducts || commandLoading;

    /*
     * Callbacks
     */
    const selectGroup = useCallback((group: LicenseGroup) => {
        if (!standalone) return;
        selected.groups.toggle(group);
        selected.instances.clear();
        setSelected({groups: selected.groups, instances: selected.instances});
    }, [selected, standalone]);

    const selectInstance = useCallback((instance: License) => {
        if (!standalone) return;
        const shouldClear = selected.instances.items
            .some(it => !equal(it.product, instance.product));
        if (shouldClear) selected.instances.clear();

        selected.instances.toggle(instance);
        selected.groups.clear();
        setSelected({groups: selected.groups, instances: selected.instances});
    }, [selected, standalone]);

    // TODO(Dan): Interface breaks down if you have more than 250 instances
    const reload = useCallback(() => {
        selected.groups.clear();
        selected.instances.clear();
        setSelected({...selected});
        fetchWallets(UCloud.accounting.wallets.retrieveBalance({}));
        fetchLicenses(UCloud.compute.licenses.browse({itemsPerPage: 250, includeAcl: true, includeUpdates: true}));
    }, [projectId, selected]);

    useEffect(reload, [projectId]);

    const inspect = useCallback((license: License) => {
        const product = products.find(it =>
            it.category.id === license.product.category &&
            it.category.provider === license.product.provider &&
            it.id === license.product.id
        );

        if (!product) {
            console.warn("Could not find product", license, product, products);
        } else {
            selected.instances.clear();
            selected.groups.clear();
            setInspecting({product, license});
        }
    }, [setInspecting, products]);

    const callbacks: LicenseOpCallback = useMemo(() => {
        return {
            commandLoading, invokeCommand, reload, history, onUse: onUse ?? doNothing,
            standalone: standalone === true, projectStatus, projectId, inspect
        }
    }, [commandLoading, invokeCommand, reload, onUse, projectStatus, projectId, inspect]);

    if (standalone === true) {
        // NOTE(Dan): Technically breaking rules of hooks. Please don't switch around the standalone prop after
        // mounting it.

        useTitle("Software licenses");
        useSidebarPage(SidebarPages.AppStore);
        useLoading(loading);
        useRefreshFunction(reload);
    }

    /*
     * Effects
     */
    const availableProductTypes = useMemo(
        () => wallets.data.wallets
            .filter(it => it.area === "LICENSE" &&
                (provider === undefined || it.wallet.paysFor.provider === provider))
            .map(it => it.wallet.paysFor),
        [wallets.data]
    );

    useEffect(() => {
        // Fetch all products when the available types change
        let didCancel = false;
        setLoadingProducts(true);

        (async () => {
            const allProducts: ProductNS.License[] = [];
            const providers = new Set<string>();
            for (const product of availableProductTypes) {
                providers.add(product.provider);
            }

            for (const provider of providers) {
                if (didCancel) break;
                const res = await callAPI(UCloud.accounting.products.retrieveAllFromProvider({provider}));
                res
                    .filter(it => it.type === "license")
                    .map(it => it as ProductNS.License)
                    .filter(product => {
                        return availableProductTypes.find(type => type.id === product.category.id) !== undefined;
                    })
                    .filter(it => !tagged ? true : tagged.some(tag => it.tags.indexOf(tag) !== -1))
                    .forEach(it => allProducts.push(it));
            }

            if (!didCancel) {
                setLoadingProducts(false);
                setProducts(allProducts);
            }
        })();

        return () => {
            didCancel = true;
        };
    }, [availableProductTypes, tagged]);

    const groups: LicenseGroup[] = products.map(product => ({product, instances: []}));
    for (const license of licenses.data.items) {
        const group = groups.find(it =>
            it.product.id === license.product.id &&
            it.product.category.id === license.product.category &&
            license.product.provider === it.product.category.provider
        );
        if (group) {
            group.instances.push(license);
        }
    }

    let main: JSX.Element;
    if (inspecting === null) {
        main = <>
            {loading && groups.length === 0 ? <HexSpin/> : null}
            {groups.length === 0 && !loading ?
                <>
                    <NoResultsCardBody title={"You don't have any software licenses available"}>
                        <Text>
                            A software license is required to run certain software. Every license is associated with
                            your
                            active project. You can apply to use a software license when you apply for resources.
                            <Link to={"/project/grants-landing"}><Button fullWidth>Request a license</Button></Link>
                        </Text>
                    </NoResultsCardBody>
                </> : null
            }
            <List childPadding={"8px"} bordered={false}>
                {groups.map(g => (
                    <React.Fragment key={g.product.id}>
                        <ListRow
                            isSelected={selected.groups.has(g)}
                            select={() => selectGroup(g)}
                            icon={
                                <AppToolLogo
                                    name={g.product.tags.length > 1 ? g.product.tags[0] : g.product.id}
                                    type={"TOOL"}
                                    size={"32px"}
                                />
                            }
                            left={<Text>{g.product.id}</Text>}
                            leftSub={
                                <ListStatContainer>
                                    <ListRowStat icon={"id"}>{g.product.category.provider}</ListRowStat>
                                    <ListRowStat icon={"grant"}>Unit
                                        price: {creditFormatter(g.product.pricePerUnit, 0)}</ListRowStat>
                                    {g.instances.length !== 1 ? null : (
                                        <InstanceStats instance={g.instances[0]}/>
                                    )}
                                </ListStatContainer>
                            }
                            right={
                                <>
                                    <Operations dropdown operations={licenseOperations} selected={selected.groups.items}
                                                extra={callbacks} entityNameSingular={"license"} row={g}/>
                                </>
                            }
                        />
                        {g.instances.length <= 1 ? null : g.instances.map(instance => (
                            <Box ml={"45px"} key={instance.id}>
                                <ListRow
                                    fontSize={17}
                                    isSelected={selected.instances.has(instance)}
                                    select={() => selectInstance(instance)}
                                    left={<Text>{shortUUID(instance.id)}</Text>}
                                    leftSub={
                                        <ListStatContainer>
                                            <InstanceStats instance={instance}/>
                                        </ListStatContainer>
                                    }
                                    right={
                                        <Operations dropdown operations={licenseInstanceOperations}
                                                    selected={selected.instances.items} extra={callbacks}
                                                    entityNameSingular={"license"} row={instance}/>
                                    }
                                />
                            </Box>
                        ))}
                    </React.Fragment>
                ))}
            </List>
        </>;
    } else {
        const product = inspecting.product;
        const license = inspecting.license;
        main = <>
            <Icon name={"arrowDown"} rotation={90} size={"32px"} cursor={"pointer"}
                  onClick={() => setInspecting(null)}/>
            <Box width={"400px"} margin={"0 auto"} marginTop={"-32px"}>
                <Box textAlign={"center"}>
                    <AppToolLogo
                        name={product.tags.length > 1 ? product.tags[0] : product.id}
                        type={"TOOL"}
                        size={"128px"}
                    />
                </Box>

                <Flex>
                    <Heading.h4 flexGrow={1}>ID</Heading.h4>
                    {shortUUID(license.id)}
                </Flex>

                <Flex>
                    <Heading.h4 flexGrow={1}>License</Heading.h4>
                    {product.id}
                </Flex>

                <Flex>
                    <Heading.h4 flexGrow={1}>State</Heading.h4>
                    {prettierString(license.status.state)}
                </Flex>

                {license.owner.project === undefined ? null : (
                    <Box mt={"32px"}>
                        <Heading.h4>Permissions</Heading.h4>
                        <LicensePermissions license={license} reload={reload}/>
                    </Box>
                )}

                <Box mt={"32px"}>
                    <Heading.h4>Updates</Heading.h4>
                    <ul>
                        {license.updates.map((update, idx) => {
                            return <li key={idx}>
                                {dateToString(update.timestamp)}
                                <br/>
                                {update.status ? <TextSpan mr={"10px"}>{update.status}</TextSpan> : null}
                                {!update.state ? null : <><Icon name={"hashtag"} size={"12px"} color={"gray"} /> {prettierString(update.state)}</>}
                            </li>
                        })}
                    </ul>
                </Box>
            </Box>
        </>;
    }

    if (standalone) {
        return <MainContainer
            header={<Heading.h2>Software licenses</Heading.h2>}
            main={main}
            sidebar={
                <>
                    <Link to={"/project/grants-landing"}><Button fullWidth>Request a license</Button></Link>
                    <Operations
                        dropdown={false}
                        operations={licenseOperations}
                        selected={selected.groups.items}
                        extra={callbacks}
                        entityNameSingular={"license"}
                    />
                    <Operations
                        dropdown={false}
                        operations={licenseInstanceOperations}
                        selected={selected.instances.items}
                        extra={callbacks}
                        entityNameSingular={"license"}
                    />
                </>
            }
        />;
    } else {
        return main;
    }
};

const InstanceStats: React.FunctionComponent<{ instance: License }> = ({instance}) => {
    return <>
        <ListRowStat icon={"hashtag"}>
            {prettierString(instance.status.state)}
        </ListRowStat>
        <ListRowStat icon={"grant"}>
            Credits charged: {creditFormatter(instance.billing.creditsCharged, 0)}
        </ListRowStat>
        <ListRowStat icon={"calendar"}>
            Created at: {dateToString(instance.createdAt)}
        </ListRowStat>
    </>;
}

const BrowseStandalone: React.FunctionComponent = () => {
    return <Browse standalone/>
};

interface LicenseOpCallback {
    commandLoading: boolean;
    invokeCommand: InvokeCommand;
    reload: () => void;
    history: History<unknown>;
    onUse: (License) => void;
    standalone: boolean;
    projectStatus: ProjectStatus;
    projectId?: string;
    inspect: (license: License) => void;
}

async function licenseOpActivate(selected: LicenseGroup[], cb: LicenseOpCallback) {
    if (cb.commandLoading) return;
    await cb.invokeCommand(
        licenseApi.create({
            type: "bulk",
            items: selected.map(g => ({
                product: {
                    id: g.product.id,
                    provider: g.product.category.provider,
                    category: g.product.category.id
                }
            }))
        })
    );
    cb.reload();
}

function hasPermissionsToUseLicense(projectStatus: ProjectStatus, instance: License): OperationEnabled {
    if (instance.owner.project === null && instance.owner.username === Client.username) return true;
    const status = projectStatus.fetch();
    const isAdmin = status.membership.some(membership => membership.projectId === instance.owner.project &&
        isAdminOrPI(membership.whoami.role))
    if (isAdmin) return true;

    const isInAcl = instance.acl?.some(entry => {
        if (!entry.permissions.some(p => p === "USE")) return false;
        return status.groups.some(group => {
            return group.project === entry.entity.group && group.project === entry.entity.projectId;
        });
    }) === true;

    if (isInAcl) return true;

    return "You do not have permission to use this license. Contact your PI to request access.";
}

function isLicenseEnabled(projectStatus: ProjectStatus, instance: License): OperationEnabled {
    const hasPermission = hasPermissionsToUseLicense(projectStatus, instance);
    if (hasPermission !== true) return hasPermission;

    switch (instance.status.state) {
        case "PREPARING":
            return "Your license is currently being prepared. It should be available for use soon.";
        case "READY":
            return true;
        case "UNAVAILABLE": {
            const allUnavailableUpdates = instance.updates.filter(it => it.state === "UNAVAILABLE");
            if (allUnavailableUpdates.length === 0) {
                return "Your license is currently unavailable";
            } else {
                return allUnavailableUpdates[allUnavailableUpdates.length - 1].status ??
                    "Your license is currently unavailable";
            }
        }
    }
}

function hasPermissionToEdit(cb: LicenseOpCallback): OperationEnabled {
    if (cb.projectId) {
        const status = cb.projectStatus.fetch();
        const isAdmin = status.membership.some(membership => membership.projectId === cb.projectId &&
            isAdminOrPI(membership.whoami.role))
        if (!isAdmin) return "Only PIs of a project can change a license";
    }
    return true;
}

const licenseOperations: Operation<LicenseGroup, LicenseOpCallback>[] = [
    {
        text: "Activate",
        primary: true,
        enabled: (selected, cb) => {
            if (!(selected.length > 0 && selected.every(it => it.instances.length === 0))) {
                return false;
            }
            return hasPermissionToEdit(cb);
        },
        onClick: licenseOpActivate
    },
    {
        icon: "check",
        text: "Activate copy",
        primary: false,
        enabled: (selected, cb) => {
            if (!(selected.length > 0 && selected.some(it => it.instances.length !== 0))) {
                return false;
            }
            return hasPermissionToEdit(cb);
        },
        onClick: licenseOpActivate
    },
    {
        text: "Use",
        primary: true,
        enabled: (selected, cb) => {
            if (cb.standalone) return false;
            if (selected.length !== 1) return false;
            if (selected[0].instances.length !== 1) return false;
            return isLicenseEnabled(cb.projectStatus, selected[0].instances[0]);
        },
        onClick: (selected, cb) => {
            cb.onUse(selected[0].instances[0]);
        }
    },
    {
        icon: "trash",
        text: "Delete",
        color: "red",
        confirm: true,
        enabled(selected, cb) {
            if (!(cb.standalone && selected.length > 0 && selected.every(it => it.instances.length === 1))) {
                return false;
            }
            return hasPermissionToEdit(cb);
        },
        onClick: async (selected, cb) => {
            if (cb.commandLoading) return;

            await cb.invokeCommand(licenseApi.remove({
                type: "bulk",
                items: selected.map(license => ({
                    id: license.instances[0].id
                }))
            }));

            cb.reload();
        }
    },
    {
        text: "Properties",
        icon: "properties",
        enabled: selected => selected.length === 1 && selected[0].instances.length === 1,
        onClick: (selected, cb) => {
            cb.inspect(selected[0].instances[0]);
        }
    }
];
const licenseInstanceOperations: Operation<License, LicenseOpCallback>[] = [
    {
        icon: "trash",
        text: "Delete",
        color: "red",
        enabled: (selected, cb) => {
            if (!(selected.length > 0 && cb.standalone)) return false;
            return hasPermissionToEdit(cb);
        },
        onClick: (selected, cb) => {
            if (cb.commandLoading) return;
            addStandardDialog({
                title: `Deletion of licenses`,
                addToFront: true,
                confirmText: "Delete",
                cancelText: "Cancel",
                confirmButtonColor: "red",
                cancelButtonColor: "green",
                message: `Are you sure you wish to delete ${selected.length} licenses?`,
                onConfirm: async () => {
                    await cb.invokeCommand(licenseApi.remove({
                        type: "bulk",
                        items: selected.map(license => ({
                            id: license.id
                        }))
                    }));

                    cb.reload();
                }
            });
        }
    },
    {
        text: "Use",
        primary: true,
        enabled: (selected, cb) => {
            if (cb.standalone) return false;
            if (selected.length !== 1) return false;
            return isLicenseEnabled(cb.projectStatus, selected[0]);
        },
        onClick: (selected, cb) => {
            cb.onUse(selected[0]);
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

const LicensePermissions: React.FunctionComponent<{license: License, reload: () => void}> = ({license, reload}) => {
    const projectId = useProjectId();
    const [projectGroups, fetchProjectGroups, groupParams] =
        useCloudAPI<Page<GroupWithSummary>>({noop: true}, emptyPage);

    const [commandLoading, invokeCommand] = useCloudCommand();

    const [acl, setAcl] = useState<LicenseAclEntry[]>(license.acl ?? []);

    useEffect(() => {
        fetchProjectGroups(UCloud.project.group.listGroupsWithSummary({itemsPerPage: 50, page: 0}));
    }, [projectId]);

    useEffect(() => {
        setAcl(license.acl ?? []);
    }, [license]);

    const updateAcl = useCallback(async (group: string, permissions: AclPermission[]) => {
        if (!projectId) return;
        if (commandLoading) return;

        const newAcl = acl
            .filter(it => !(it.entity.projectId === projectId && it.entity.group === group));
        newAcl.push({entity: {projectId, group, type: "project_group"}, permissions});

        setAcl(newAcl);

        await invokeCommand(licenseApi.updateAcl({acl: newAcl, id: license.id}))
        reload();
    }, [acl, projectId, commandLoading]);

    const anyGroupHasPermission = acl.some(it => it.permissions.indexOf("USE") !== -1);

    return <Pagination.List
        loading={projectGroups.loading}
        page={projectGroups.data}
        onPageChanged={(page) => fetchProjectGroups(groupSummaryRequest({
            ...groupParams.parameters,
            page
        }))}
        customEmptyPage={(
            <Flex width={"100%"} height={"100%"} alignItems={"center"} justifyContent={"center"}
                  flexDirection={"column"}>
                <ShakingBox shaking mb={"10px"}>
                    No groups exist for this project.{" "}
                    <TextSpan bold>As a result, this license can only be used by project admins!</TextSpan>
                </ShakingBox>

                <Link to={"/project/members"} target={"_blank"}><Button fullWidth>Create group</Button></Link>
            </Flex>
        )}
        pageRenderer={() => (
            <>
                {anyGroupHasPermission ? null :
                    <ShakingBox shaking mb={16}>
                        <Text bold>This license can only be used by project admins</Text>
                        <Text>
                            You must assign permissions to one or more group, if your collaborators need access to this
                            software.
                        </Text>
                    </ShakingBox>
                }
                {projectGroups.data.items.map(summary => {
                    const g = summary.groupId;
                    const permissions = acl.find(it =>
                        it.entity.group === g &&
                        it.entity.projectId === projectId
                    )?.permissions ?? [];

                    return (
                        <Flex key={g} alignItems={"center"} mb={16}>
                            <Truncate width={"300px"} mr={16} title={summary.groupTitle}>
                                {summary.groupTitle}
                            </Truncate>

                            <RadioTilesContainer>
                                <RadioTile
                                    label={"None"}
                                    onChange={() => updateAcl(g, [])}
                                    icon={"close"}
                                    name={summary.groupId}
                                    checked={permissions.length === 0}
                                    height={40}
                                    fontSize={"0.5em"}
                                />
                                <RadioTile
                                    label={"Use"}
                                    onChange={() => updateAcl(g, ["USE"])}
                                    icon={"search"}
                                    name={summary.groupId}
                                    checked={permissions.indexOf("USE") !== -1 && permissions.length === 1}
                                    height={40}
                                    fontSize={"0.5em"}
                                />
                            </RadioTilesContainer>
                        </Flex>
                    );
                })}
            </>
        )}
    />;
};

export default BrowseStandalone;
