import * as React from "react";
import {useCallback, useEffect, useState} from "react";
import {useNavigate, useParams} from "react-router-dom";
import {callAPI, useCloudAPI} from "@/Authentication/DataHook";
import {useLoading, usePage} from "@/Navigation/Redux";
import {useSetRefreshFunction} from "@/Utilities/ReduxUtilities";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import MainContainer from "@/ui-components/MainContainer";
import {Box, Button, Card, Flex, Icon} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import TabbedCard, {TabbedCardTab} from "@/ui-components/TabbedCard";
import {HeroHeaderCard, HeroHeaderGrid, HeroMetric} from "@/Applications/Jobs/HeroHeader";
import {PrivateNetworkReservations} from "./Reservations";
import {ActiveNetworkJobs} from "./ActiveNetworkJobs";
import {PredicatedPermissionsTable} from "@/Resource/Properties";
import PrivateNetworkApi, {PrivateNetwork} from "@/UCloud/PrivateNetworkApi";
import {placeholderProduct} from "@/UCloud/ResourceApi";
import {dateToString} from "@/Utilities/DateUtilities";
import {bulkRequestOf, displayErrorMessageOrDefault} from "@/UtilityFunctions";
import {ProviderTitle} from "@/Providers/ProviderTitle";
import AppRoutes from "@/Routes";
import {injectStyle} from "@/Unstyled";
import {useProjectId} from "@/Project/Api";
import {ConfirmationButton} from "@/ui-components/ConfirmationAction";

const Layout = injectStyle("private-network-layout", k => `
    ${k} {
        display: grid;
        gap: 20px;
        max-width: 1500px;
        margin: 0 auto;
    }
    ${k} .secondary {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(min(100%, 360px), 1fr));
        gap: 20px;
        align-items: stretch;
    }
`);

const EmptyPermissionsCard = injectStyle("private-network-empty-permissions", k => `
    ${k}, ${k} > div {
        display: flex;
        flex-direction: column;
    }
    ${k} > div, ${k} [data-tab-name] {
        flex: 1;
    }
    ${k} .empty-permissions {
        min-height: 160px;
        height: 100%;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 12px;
        text-align: center;
    }
`);

export function PrivateNetworkProperties(): React.ReactNode {
    const {id} = useParams<{id: string}>();
    const navigate = useNavigate();
    const projectId = useProjectId();
    const [networkState, fetchNetwork] = useCloudAPI<PrivateNetwork | null>({noop: true}, null);
    const [reservationCount, setReservationCount] = useState<number | null>(null);

    usePage("Private network", SidebarTabId.RESOURCES);
    useLoading(networkState.loading);

    const refresh = useCallback(() => {
        if (id) fetchNetwork(PrivateNetworkApi.retrieve({id, includeOthers: true, includeSupport: true}));
    }, [id, fetchNetwork]);

    useEffect(() => refresh(), [refresh]);
    useSetRefreshFunction(refresh);

    const network = !networkState.data ? null : {
        ...networkState.data,
        specification: {
            ...networkState.data.specification,
            product: networkState.data.specification.product ?? placeholderProduct(),
        },
    };
    const members = network?.status.members ?? [];
    const deleteNetwork = async () => {
        if (!network) return;
        try {
            await callAPI(PrivateNetworkApi.remove(bulkRequestOf({id: network.id})));
            navigate(AppRoutes.resources.privateNetworks());
        } catch (e) {
            displayErrorMessageOrDefault(e, "Failed to delete private network");
        }
    };

    return <MainContainer main={!network ? <Box p="24px">
        {networkState.loading ? "Loading private network..." : networkState.error?.why ?? "Private network not found."}
    </Box> : <div className={Layout}>
        <Card p="24px" className={HeroHeaderCard}>
            <Flex alignItems="center" gap="16px" flexWrap="wrap">
                <Icon name="heroCloud" size={36} />
                <Box>
                    <Heading.h2>{network.specification.name || network.id}</Heading.h2>
                </Box>
                <Box flexGrow={1} />
                <Button onClick={refresh}><Icon name="heroArrowPath" mr="8px" />Refresh</Button>
                <ConfirmationButton color="errorMain" icon="trash" actionText="Delete network" onAction={deleteNetwork}
                    disabled={!network.permissions.myself.includes("ADMIN")} />
            </Flex>
            <div className={HeroHeaderGrid}>
                <HeroMetric title="Active jobs">{members.length}</HeroMetric>
                <HeroMetric title="Reserved IPs">{reservationCount ?? "—"}</HeroMetric>
                <HeroMetric title="Network status">{network.status.cidrBlock ? "Ready" : "Provisioning"}</HeroMetric>
                <HeroMetric title="Provider"><ProviderTitle providerId={network.specification.product.provider} /></HeroMetric>
            </div>
        </Card>

        <TabbedCard>
            <TabbedCardTab icon="heroServerStack" name="Active jobs">
                <ActiveNetworkJobs key={network.id} members={members} networkId={network.id} />
            </TabbedCardTab>
        </TabbedCard>

        <div className="secondary">
            <TabbedCard>
                <TabbedCardTab icon="heroInformationCircle" name="Details">
                    <div className={HeroHeaderGrid}>
                        <HeroMetric title="Subdomain">{network.specification.subdomain}</HeroMetric>
                        <HeroMetric title="Address range">{network.status.cidrBlock ?? "Pending allocation"}</HeroMetric>
                        <HeroMetric title="Requested range">{network.specification.cidr ?? "Automatic"}</HeroMetric>
                        <HeroMetric title="Created by">{network.owner.createdBy}</HeroMetric>
                        <HeroMetric title="Created at">{dateToString(network.createdAt)}</HeroMetric>
                        <HeroMetric title="Network ID">{network.id}</HeroMetric>
                    </div>
                </TabbedCardTab>
            </TabbedCard>
            {projectId ? <PredicatedPermissionsTable show api={PrivateNetworkApi} res={network} /> :
                <TabbedCard className={EmptyPermissionsCard}>
                    <TabbedCardTab icon="heroShare" name="Permissions">
                        <div className="empty-permissions">
                            <Icon name="heroUserGroup" size={32} color="textSecondary" />
                            <Box color="textSecondary" maxWidth="320px">
                                Permissions cannot be set in a personal workspace. Switch to a project to manage access to its private networks.
                            </Box>
                        </div>
                    </TabbedCardTab>
                </TabbedCard>}
        </div>

        <PrivateNetworkReservations key={network.id} resource={network} reloadKey={networkState.data!} onCountChange={setReservationCount} />
    </div>} />;
}
