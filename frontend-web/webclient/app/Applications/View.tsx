import {SafeLogo} from "@/Applications/AppToolLogo";
import * as React from "react";
import {Box, Flex, Icon, Tooltip} from "@/ui-components";
import Text from "@/ui-components/Text";
import * as Pages from "./Pages";
import {useNavigate} from "react-router";
import {FavoriteToggle} from "@/Applications/FavoriteToggle";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {injectStyle, injectStyleSimple} from "@/Unstyled";
import {Application, ApplicationSummaryWithFavorite, ApplicationWithFavoriteAndTags} from "@/Applications/AppStoreApi";
import {Feature, hasFeature} from "@/Features";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {RichSelect} from "@/ui-components/RichSelect";
import {useMemo} from "react";

const DEFAULT_FLAVOR_NAME = "Default";

export const AppHeader: React.FunctionComponent<{
    application: Application;
    allVersions: string[];
    flavors: Application[];
    title: string;
}> = props => {
    const newestVersion = props.allVersions[0];
    const navigate = useNavigate();
    const close = React.useRef(() => void 0);

    const searchableFlavor: { searchKey: string, app: Application }[] = useMemo(() => {
        return props.flavors.map(app => {
            return { searchKey: app.metadata.flavorName ?? DEFAULT_FLAVOR_NAME, app };
        }).sort((a, b) => {
            return a.searchKey.localeCompare(b.searchKey);
        });
    }, [props.flavors]);

    const searchableVersions: { searchKey: string, version: string }[] = useMemo(() => {
        return props.allVersions.map(version => {
            return { searchKey: version, version }
        });
    }, [props.flavors]);

    return (
        <Flex flexDirection={"row"}>
            <Box mr={16} mt="auto">
                <SafeLogo type={"APPLICATION"} name={props.application.metadata.name} size={"64px"} />
            </Box>
            {/* minWidth=0 is required for the ellipsed text children to work */}
            <Flex flexDirection={"column"} minWidth={0}>
                <Box>
                    <Flex>
                        <Text verticalAlign="center" alignItems="center" fontSize={30} mr="5px">
                            {props.title}
                        </Text>
                        <Box style={{alignSelf: "center", marginRight: "10px"}}>
                            <FavoriteToggle application={props.application} />
                        </Box>
                    </Flex>
                </Box>
                <Flex marginTop="2px" gap={"8px"}>
                    <Box>
                        <RichSelect
                            items={searchableFlavor}
                            keys={["searchKey"]}
                            selected={{searchKey: "", app: props.application}}
                            dropdownWidth={"300px"}
                            RenderRow={p => {
                                return <Box p={"8px"} onClick={p.onSelect} {...p.dataProps}>
                                    {p.element?.app?.metadata?.flavorName ?? DEFAULT_FLAVOR_NAME}
                                </Box>;
                            }}
                            RenderSelected={p => {
                                return <Box p={"8px"} onClick={p.onSelect} {...p.dataProps}>
                                    {p.element?.app?.metadata?.flavorName ?? DEFAULT_FLAVOR_NAME}
                                </Box>;
                            }}
                            onSelect={p => {
                                navigate(Pages.runApplicationWithName(p.app.metadata.name));
                            }}
                        />
                    </Box>

                    <RichSelect
                        items={searchableVersions}
                        keys={["searchKey"]}
                        selected={{searchKey: "", version: props.application.metadata.version}}
                        dropdownWidth={"138px"}
                        RenderRow={p => {
                            return <Box p={"8px"} onClick={p.onSelect} {...p.dataProps}>{p.element?.version}</Box>;
                        }}
                        RenderSelected={p => {
                            return <Box p={"8px"} onClick={p.onSelect} {...p.dataProps}>{p.element?.version}</Box>;
                        }}
                        onSelect={p => {
                            navigate(Pages.runApplication({name: props.application.metadata.name, version: p.version}))
                        }}
                    />
                    {newestVersion !== props.application.metadata.version ?
                        <Tooltip tooltipContentWidth={390} trigger={
                            <div className={TriggerDiv} onClick={e => {
                                e.preventDefault();
                                navigate(Pages.runApplication({name: props.application.metadata.name, version: newestVersion}));
                            }}>
                                New version available.
                            </div>
                        }>
                            <div onClick={e => e.stopPropagation()}>
                                You are not using the newest version of the app.<br />
                                Click to use the newest version.
                            </div>
                        </Tooltip>
                        : null}
                </Flex>
            </Flex>
        </Flex>
    );
};

const TriggerDiv = injectStyleSimple("trigger-div", `
    padding-left: 12px;
    padding-right: 12px;
    text-align: center;
    color: var(--warningContrast);
    background-color: var(--warningMain);
    border-radius: 6px;
    cursor: pointer;
    height: 35px;
    display: flex;
    justify-content: center;
    align-items: center;
`);

const FlavorSelectorClass = injectStyle("flavor-selector", k => `
    ${k} {
        height: 35px;
        border-radius: 8px;
        padding: 0px 10px;
        align-items: center;
        border: 1px solid var(--borderColor);
        margin: auto 0px;
    }
`);
