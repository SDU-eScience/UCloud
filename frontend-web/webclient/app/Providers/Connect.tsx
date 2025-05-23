import * as React from "react";
import TitledCard from "@/ui-components/HighlightedCard";
import {Text, Button, Icon, List, Link, Flex} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {ListRow} from "@/ui-components/List";
import {apiRetrieve, apiUpdate, callAPI, useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {EventHandler, MouseEvent, useCallback, useEffect} from "react";
import {doNothing} from "@/UtilityFunctions";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {ProviderTitle} from "@/Providers/ProviderTitle";
import {Feature, hasFeature} from "@/Features";
import MainContainer from "@/ui-components/MainContainer";
import {usePage} from "@/Navigation/Redux";
import {Operations, ShortcutKey} from "@/ui-components/Operation";
import Spinner from "@/LoadingIcon/LoadingIcon";
import {connectionState} from "./ConnectionState";
import {useUState} from "@/Utilities/UState";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {injectStyle} from "@/Unstyled";
import {IconName} from "@/ui-components/Icon";
import {TooltipV2} from "@/ui-components/Tooltip";
import {ThemeColor} from "@/ui-components/theme";

const FixedHeightProvider = injectStyle("FixedHeightProvider", k => `
    ${k} {
        height: 55px;
    }
`)

interface ProviderCondition {
    page?: string;
    level: "NORMAL" | "DEGRADED" | "MAINTENANCE" | "DOWN" | "UNKNOWN";
}

const ProviderConditionIcon: React.FunctionComponent<{condition: ProviderCondition}> = props => {
    if (!hasFeature(Feature.PROVIDER_CONDITION)) return null;
    if (props.condition.level === "UNKNOWN") return null;

    let icon: IconName = "heroQuestionMarkCircle";
    let color: ThemeColor = "infoMain";
    let tooltip: string = "infoMain";

    switch (props.condition.level) {
        case "NORMAL":
            icon = "heroCheckCircle";
            color = "successMain";
            tooltip = "Operational"
            break;
        case "DEGRADED":
            icon = "heroExclamationTriangle";
            color = "warningMain";
            tooltip = "Degraded";
            break;
        case "DOWN":
            icon = "heroFire";
            color = "errorMain";
            tooltip = "Unavailable"
            break;
        case "MAINTENANCE":
            icon = "heroWrench";
            color = "textSecondary";
            tooltip = "Down for maintenance"
            break;
    }

    return <Link to={props.condition.page ?? ""} target={"_blank"}>
        <TooltipV2 tooltip={tooltip} contentWidth={120}>
            <Icon name={icon} color={color} size={20} />
        </TooltipV2>
    </Link>;
};

export const Connect: React.FunctionComponent<{embedded?: boolean}> = props => {
    if (!hasFeature(Feature.PROVIDER_CONNECTION)) return null;

    const [providerConditions, setProviderConditions] = React.useState(new Map<string, ProviderCondition>());
    const state = useUState(connectionState);
    const [, invokeCommand] = useCloudCommand();
    const reload = useCallback(() => {
        state.fetchFresh();
    }, []);

    useEffect(reload, [reload]);

    const providers = state.providers;
    const shouldConnect = providers.some(it => state.canConnectToProvider(it.providerTitle));

    useEffect(() => {
        providers.forEach(provider => {
            callAPI(
                apiRetrieve(
                    {provider: provider.provider},
                    "/api/providers/integration",
                    "condition"
                )
            ).then(condition => {
                setProviderConditions((prev) => new Map(prev.set(provider.providerTitle, condition)));
            });
        });
    }, [providers.length]);

    const body = <>
        {!shouldConnect ? null :
            <Text color={"textSecondary"} mb={8}>
                <Icon name={"warning"} color={"warningMain"} mr={"8px"} />
                Connect with the services below to use their resources
            </Text>
        }
        <List>
            {providers.map(it => {
                const providerCondition = providerConditions.get(it.providerTitle);
                const canConnect = state.canConnectToProvider(it.providerTitle);

                const openFn: React.RefObject<(left: number, top: number) => void> = {current: doNothing};
                const onContextMenu: EventHandler<MouseEvent<never>> = e => {
                    e.stopPropagation();
                    e.preventDefault();
                    openFn.current(e.clientX, e.clientY);
                };

                if (it.unmanagedConnection === true && props.embedded === true) {
                    return null;
                }

                return (
                    <ListRow
                        onContextMenu={onContextMenu}
                        key={it.provider}
                        className={FixedHeightProvider}
                        highlightOnHover={false}
                        icon={<ProviderLogo providerId={it.providerTitle} size={30} />}
                        left={
                            <Flex fontSize={"16px"} ml={3} alignItems={"center"} gap="5px">
                                <div><ProviderTitle providerId={it.providerTitle} /></div>
                                {!providerCondition ? null :
                                    <ProviderConditionIcon condition={providerCondition} />
                                }
                            </Flex>
                        }
                        right={!canConnect ?
                            <>
                                <TooltipV2 tooltip={"Connected"} contentWidth={100}>
                                    <Icon name={"heroCheck"} color={"successMain"} size="24" />
                                </TooltipV2>
                                <Operations
                                    location={"IN_ROW"}
                                    operations={[
                                        {
                                            text: "Provider status",
                                            icon: "favIcon",
                                            enabled: () => {return true;},
                                            onClick: () => {
                                                window.open(providerConditions[it.provider]?.page ?? "https://status.cloud.sdu.dk", "_blank")?.focus();
                                            },
                                            shortcut: ShortcutKey.S,
                                        },
                                        {
                                            confirm: true,
                                            color: "errorMain",
                                            text: "Unlink",
                                            icon: "close",
                                            enabled: () => {
                                                // TODO(Dan): Generalize this for more providers
                                                return it.providerTitle !== "ucloud" && it.providerTitle !== "aau";
                                            },
                                            onClick: async () => {
                                                await invokeCommand(
                                                    apiUpdate(
                                                        {provider: it.providerTitle},
                                                        "/api/providers/integration",
                                                        "clearConnection"
                                                    )
                                                );

                                                reload();
                                            },
                                            shortcut: ShortcutKey.U
                                        }
                                    ]}
                                    selected={[]}
                                    extra={null}
                                    entityNameSingular={"Provider"}
                                    row={it}
                                    openFnRef={openFn}
                                    forceEvaluationOnOpen
                                />
                            </> :
                            <Button
                                height={40}
                                onClick={() => state.connectToProvider(it.providerTitle)}
                                disabled={state.loading}
                            >
                                {state.loading ?
                                    <div style={{marginTop: "-8px"}}>
                                        <Spinner size={16} />
                                    </div> :
                                    "Connect"
                                }
                            </Button>
                        }
                    />
                );
            })}
        </List>
    </>;

    if (props.embedded) {
        return <TitledCard
            icon={"heroCloud"}
            title={<Link to={"/providers/connect"}><Heading.h3>Providers<Icon ml="6px" mt="-4px" name="heroArrowTopRightOnSquare" /></Heading.h3></Link>}
            subtitle={<Link to="/providers/overview">View details</Link>}
        >
            {body}
        </TitledCard>;
    } else {
        // NOTE(Dan): You are not meant to swap the embedded property on a mounted component. We should be fine even
        // though we are breaking rules of hooks.
        // NOTE(Jonas): Woohooo, breaking the rules of hooks!
        usePage("Connect to Providers", SidebarTabId.NONE);
        return <MainContainer
            header={
                <Heading.h3 style={{marginLeft: "8px"}}>Provider connections</Heading.h3>
            }
            main={body}
        />;
    }
};

export default Connect;
