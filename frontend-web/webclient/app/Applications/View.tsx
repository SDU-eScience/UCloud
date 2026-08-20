import {SafeLogo} from "@/Applications/AppToolLogo";
import * as React from "react";
import {Box, Flex, Label, Tooltip, Truncate} from "@/ui-components";
import Text from "@/ui-components/Text";
import * as Pages from "./Pages";
import {useNavigate} from "react-router-dom";
import {FavoriteToggle} from "@/Applications/FavoriteToggle";
import {classConcat, injectStyleSimple} from "@/Unstyled";
import {Application} from "@/Applications/AppStoreApi";
import {RichSelect} from "@/ui-components/RichSelect";
import {useMemo} from "react";
import {FlavorRefresh, openFlavorManagement} from "@/Applications/FlavorManagement";
import {dialogStore} from "@/Dialog/DialogStore";
import {Client} from "@/Authentication/HttpClientInstance";
import {checkIsWorkspaceAdmin} from "@/ui-components/ResourceBrowser";
import Warning from "@/ui-components/Warning";
import BaseLink from "@/ui-components/BaseLink";

const DEFAULT_FLAVOR_NAME = "Default";

export const AppHeader: React.FunctionComponent<{
    application: Application;
    allVersions: string[];
    flavors: Application[];
    title: string;
    showSelectors?: boolean;
    description?: React.ReactNode;
    responsiveDescription?: boolean;
}> = props => {
    return (
        <Flex className={props.responsiveDescription ? ResponsiveAppHeaderClass : undefined} flexDirection={"row"}>
            <Box className="app-header-logo" mr={16} mt={props.description ? "4px" : "auto"}>
                <SafeLogo type={"APPLICATION"} name={props.application.metadata.name} size={"64px"} />
            </Box>
            {/* minWidth=0 is required for the ellipsed text children to work */}
            <Flex className="app-header-content" flexDirection={"column"} minWidth={0}>
                <Box className="app-header-title">
                    <Flex>
                        <Text verticalAlign="center" alignItems="center" fontSize={30} mr="5px">
                            {props.title}
                        </Text>
                        <Box style={{alignSelf: "center", marginRight: "10px"}}>
                            <FavoriteToggle application={props.application} />
                        </Box>
                    </Flex>
                </Box>
                {!props.description ? null : <Box className="app-header-description" mt="8px" maxWidth="800px">{props.description}</Box>}
                {props.showSelectors === false ? null : <ApplicationSelector {...props} />}
            </Flex>
        </Flex>
    );
};

const ResponsiveAppHeaderClass = injectStyleSimple("responsive-app-header", `
    @media (max-width: 600px) {
        display: grid;
        grid-template-columns: 64px minmax(0, 1fr);
        column-gap: 16px;
        row-gap: 12px;
        width: 100%;

        > .app-header-logo {
            margin: 0 !important;
        }

        > .app-header-content {
            display: contents;
        }

        .app-header-title {
            align-self: center;
            min-width: 0;
        }

        .app-header-description {
            grid-column: 1 / -1;
            margin-top: 0 !important;
        }
    }
`);

enum FlavorGrouping {
    UCloudManaged = "UCloud managed flavors",
    YourFlavors = "Your flavors"
}

interface FlavorOption {
    app: Application;
    group: FlavorGrouping;
    latestVersion: string;
    searchKey: string;
}

export const ApplicationSelector: React.FunctionComponent<{
    application: Application;
    allVersions: string[];
    flavors: Application[];
    showLabels?: boolean;
    jobCreateLayout?: boolean;
    fieldNavigation?: boolean;
    autoFocusFlavor?: boolean;
    reloadFlavors?: FlavorRefresh;
    onApplicationChange?: () => void;
}> = props => {
    const newestVersion = props.allVersions[0];
    const navigate = useNavigate();
    const searchableFlavor = useMemo<FlavorOption[]>(() => props.flavors.map(app => {
        const variant = app.metadata.variant;
        const group: FlavorOption["group"] = variant ? FlavorGrouping.YourFlavors : FlavorGrouping.UCloudManaged;
        return {
            app,
            group,
            latestVersion: app.versions?.[0] ?? app.metadata.version,
            searchKey: variant?.title ?? app.metadata.flavorName ?? DEFAULT_FLAVOR_NAME,
        };
    }).sort((a, b) => a.group.localeCompare(b.group) || a.searchKey.localeCompare(b.searchKey)), [props.flavors]);
    const selectedFlavor = searchableFlavor.find(it => it.app.metadata.name === props.application.metadata.name) ?? {
        app: props.application,
        group: props.application.metadata.variant ? FlavorGrouping.YourFlavors : FlavorGrouping.UCloudManaged,
        latestVersion: newestVersion ?? props.application.metadata.version,
        searchKey: props.application.metadata.variant?.title ?? props.application.metadata.flavorName ?? DEFAULT_FLAVOR_NAME,
    } as FlavorOption;
    const searchableVersions = useMemo(() => props.allVersions.map(version => ({searchKey: version, version})),
        [props.allVersions]);
    const customFlavors = props.flavors.filter(app => app.metadata.variant &&
        (app.metadata.variant.createdBy === Client.username || checkIsWorkspaceAdmin()));
    const baseLatestVersion = props.application.metadata.variant ?
        props.flavors.find(app => app.metadata.name === props.application.metadata.variant?.baseApplication.name)?.versions?.[0] :
        undefined;

    const caretPlacement: React.CSSProperties = {position: "absolute", right: "12px", top: "50%", transform: "translateY(-50%)"};

    return <div className={classConcat(ApplicationSelectorClass, props.jobCreateLayout ? JobCreateApplicationSelectorClass : undefined)}>
        <Label style={{minWidth: 0}}>
            {!props.showLabels ? null : <Flex mb="8px" gap="6px">
                <Box>Flavor</Box>

                {customFlavors.length === 0 ? null : <BaseLink onClick={event => {
                    event.preventDefault();
                    openFlavorManagement(customFlavors, props.reloadFlavors ?? (() => undefined), variant => {
                        if (props.application.metadata.variant?.id !== variant.id) return;
                        dialogStore.success();
                        props.onApplicationChange?.();
                        navigate(Pages.runApplication(variant.baseApplication));
                    });
                }}>(Manage your flavors)</BaseLink>}
            </Flex>}
            <RichSelect
                items={searchableFlavor}
                keys={["searchKey", "latestVersion"]}
                selected={selectedFlavor}
                fullWidth
                matchTriggerWidth={false}
                dropdownWidth="min(380px, calc(100vw - 40px))"
                elementHeight={37}
                groupBy={item => item.group}
                showSearchField
                focusable={props.fieldNavigation}
                autoFocus={props.autoFocusFlavor}
                data-job-info-field={props.fieldNavigation ? "flavor" : undefined}
                data-card-first-field={props.fieldNavigation || undefined}
                chevronPlacement={caretPlacement}
                RenderRow={p => <Flex p="8px" alignItems="center" onClick={p.onSelect} {...p.dataProps}>
                    <Truncate title={p.element?.searchKey}>{p.element?.searchKey}</Truncate>
                    <Text ml="auto" color="textSecondary" fontSize="12px">Latest: {p.element?.latestVersion}</Text>
                </Flex>}
                RenderSelected={p => <Flex p="7px" pr="48px" alignItems="center" {...p.dataProps}>
                    <Truncate title={p.element?.searchKey}>{p.element?.searchKey}</Truncate>
                </Flex>}
                onSelect={p => {
                    props.onApplicationChange?.();
                    navigate(Pages.runApplicationWithName(p.app.metadata.name));
                }}
            />
        </Label>

        <Label style={{minWidth: 0}}>
            {!props.showLabels ? null : <Box mb="8px">Version</Box>}
            <RichSelect
                items={searchableVersions}
                keys={["searchKey"]}
                selected={{searchKey: props.application.metadata.version, version: props.application.metadata.version}}
                fullWidth
                matchTriggerWidth={false}
                dropdownWidth="min(220px, calc(100vw - 40px))"
                focusable={props.fieldNavigation}
                data-job-info-field={props.fieldNavigation ? "version" : undefined}
                chevronPlacement={caretPlacement}
                RenderRow={p => <Truncate title={p.element?.version} p="8px" onClick={p.onSelect} {...p.dataProps}>{p.element?.version}</Truncate>}
                RenderSelected={p => <Truncate title={p.element?.version} p="8px" pr="48px" {...p.dataProps}>{p.element?.version}</Truncate>}
                onSelect={p => {
                    props.onApplicationChange?.();
                    navigate(Pages.runApplication({name: props.application.metadata.name, version: p.version}));
                }}
            />
        </Label>
        {newestVersion !== props.application.metadata.version ?
            <Box style={{gridColumn: "1 / -1"}}><Tooltip tooltipContentWidth={390} trigger={
                <div className={TriggerDiv} onClick={e => {
                    e.preventDefault();
                    props.onApplicationChange?.();
                    navigate(Pages.runApplication({name: props.application.metadata.name, version: newestVersion}));
                }}>
                    New version available.
                </div>
            }>
                <div onClick={e => e.stopPropagation()}>
                    You are not using the newest version of the app.<br />
                    Click to use the newest version.
                </div>
            </Tooltip></Box>
            : null}
        {!props.jobCreateLayout || !props.application.metadata.variant || !baseLatestVersion ||
        baseLatestVersion === props.application.metadata.variant.baseApplication.version ? null :
            <Box style={{gridColumn: "1 / -1"}}>
                <Warning mb={"0"}>
                    This flavor uses base application version {props.application.metadata.variant.baseApplication.version}.
                    The latest version is {baseLatestVersion}.
                </Warning>
            </Box>
        }
    </div>;
};

const ApplicationSelectorClass = injectStyleSimple("application-selector", `
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(180px, 240px);
    gap: 8px;
    width: 100%;
    min-width: min(600px, calc(100vw - 120px));

    @media (max-width: 600px) {
        grid-template-columns: minmax(0, 1fr);
        min-width: min(100%, calc(100vw - 40px));
    }
`);

const JobCreateApplicationSelectorClass = injectStyleSimple("job-create-application-selector", `
    grid-template-columns: minmax(0, 1fr) 244px;
    gap: 15px;
    min-width: 0;

    @media (max-width: 600px) {
        grid-template-columns: minmax(0, 1fr);
    }
`);

const TriggerDiv = injectStyleSimple("trigger-div", `
    padding-left: 12px;
    padding-right: 12px;
    text-align: center;
    color: var(--warningContrast);
    background-color: var(--warningMain);
    border-radius: 6px;
    cursor: pointer;
    height: 39px;
    display: flex;
    justify-content: center;
    align-items: center;
`);
