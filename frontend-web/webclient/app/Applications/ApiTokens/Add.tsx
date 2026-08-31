import * as React from "react";
import {callAPI, useCloudAPI} from "@/Authentication/DataHook";
import {Box, Button, Card, Image, Divider, Flex, Icon, Input, MainContainer, Link, Select, TextArea} from "@/ui-components";
import * as Api from "./api";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {addDays, addMonths, formatDistanceToNow, startOfToday} from "date-fns";
import {DataAttributes, injectStyle} from "@/Unstyled";
import {MandatoryField} from "@/UtilityComponents";
import {FieldGroup, FieldRow} from "@/Applications/Jobs/Widgets";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {ProviderLogo, ProviderLogoWrapper} from "@/Providers/ProviderLogo";
import {RichSelect, RichSelectProps, SimpleRichSelect} from "@/ui-components/RichSelect";
import {ProviderTitle} from "@/Providers/ProviderTitle";
import {ProjectSwitcher} from "@/Project/ProjectSwitcher";
import {copyToClipboard, displayErrorMessageOrDefault, doNothing} from "@/UtilityFunctions";
import {ApiToken, ApiTokenStatus} from "./api";
import * as Heading from "@/ui-components/Heading";
import {CopyButton} from "@/ui-components/CopyButton";
import Warning from "@/ui-components/Warning";
import {DocumentTypography} from "@/ui-components/Markdown";
import AppRoutes from "@/Routes";
import Routes from "@/Routes";
import {getStoredProject} from "@/Project/ReduxState";
import {sendFailureNotification} from "@/Notifications";
import {KeyboardNavigation, SubmitShortcut, useSubmitShortcut} from "@/Applications/KeyboardNavigation";

const API_TOKEN_TITLE_KEY = "api-title";
const API_TOKEN_DESCRIPTION_KEY = "api-description";

const ApiTokenCreateHeaderClass = injectStyle("api-token-create-header", key => `
    ${key} {
        display: flex;
        margin: 32px 50px 24px;
    }

    @media (max-width: 600px) {
        ${key} {
            margin: 24px 16px;
        }
    }
`);

const ApiTokenCreateContentClass = injectStyle("api-token-create-content", key => `
    ${key} {
        display: flex;
        flex-direction: column;
        gap: 24px;
        max-width: 960px;
        margin: 0 50px;
    }

    @media (max-width: 600px) {
        ${key} {
            margin: 0 16px;
        }
    }
`);

const ApiTokenResultClass = injectStyle("api-token-result", key => `
    ${key} {
        max-width: 760px;
        margin: 32px 50px;
    }

    ${key} .value {
        display: grid;
        grid-template-columns: 120px minmax(0, 1fr) auto;
        gap: 8px;
        align-items: center;
        padding: 12px 0;
    }

    ${key} code {
        min-width: 0;
        overflow-wrap: anywhere;
    }

    @media (max-width: 600px) {
        ${key} {
            margin: 24px 16px;
        }

        ${key} .value {
            grid-template-columns: minmax(0, 1fr) auto;
        }

        ${key} .value > :first-child {
            grid-column: 1 / -1;
        }
    }
`);

const ApiTokenSubmitClass = injectStyle("api-token-submit", key => `
    ${key} {
        display: flex;
        align-items: center;
        justify-content: flex-end;
        gap: 8px;
        margin: 0 0 48px;
    }

    @media (max-width: 600px) {
        ${key} {
            align-items: stretch;
            flex-direction: column;
        }

        ${key} > div:first-child {
            margin-right: 0 !important;
        }

        ${key} > a, ${key} > button {
            width: 100%;
        }
    }
`);

const ApiTokenFullWidthSelectorClass = injectStyle("api-token-full-width-selector", key => `
    ${key} {
        width: 100%;
    }
`);

const ApiTokenProjectSelectorClass = injectStyle("api-token-project-selector", key => `
    ${key},
    ${key} [data-component="project-switcher"],
    ${key} [data-component="project-switcher"] > [data-tag="dropdown"],
    ${key} [data-dropdown-trigger],
    ${key} [data-dropdown-trigger] > div {
        width: 100%;
    }

    ${key} [data-dropdown-trigger] > div {
        justify-content: space-between;
    }
`);

const ApiTokenExpirationSelectorClass = injectStyle("api-token-expiration-selector", key => `
    ${key} {
        position: relative;
        border: 1px solid var(--borderColor);
        border-radius: 5px;
        display: flex;
        width: 100%;
        height: 33.5px;
    }

    ${key}:hover {
        border-color: var(--borderColorHover);
    }

    ${key} > div:first-child {
        display: flex;
        align-items: center;
        flex-grow: 1;
        width: auto !important;
        height: 31.5px;
        padding-left: 8px;
    }

    ${key} > svg {
        position: absolute;
        bottom: 8px;
        right: 12px;
        height: 16px;
    }
`);

function Add() {
    usePage("Create API token", SidebarTabId.RESOURCES);

    const [options] = useCloudAPI(Api.retrieveOptions(), {byProvider: {}});
    const [date, setDate] = React.useState<Date | null>(addDays(startOfToday(), 30));

    const [serviceProvider, setServiceProvider] = React.useState("");

    const optionsData = options.data.byProvider;
    const serviceProviders = Object.keys(optionsData);
    const [projectId, setProjectId] = React.useState<string | undefined>(getStoredProject() ?? undefined);
    const [activePermissions, setActivePermissions] = React.useState(new Map<string, Set<string>>());
    const [tokenStatus, setTokenStatus] = React.useState<ApiTokenStatus | null>(null);
    const [loading, setLoading] = React.useState(false);

    const mappedServiceProviders = serviceProviders.map(it => ({key: it}));

    const availablePermissions = optionsData[serviceProvider]?.availablePermissions ?? [];
    const selectedService = availablePermissions.find(it => activePermissions.has(it.name));

    const submit = React.useCallback(async () => {
        const titleElement = document.getElementById(API_TOKEN_TITLE_KEY) as HTMLInputElement;
        const title = titleElement.value.replace(/^\.+/, "");
        titleElement.value = title;
        const descriptionElement = document.getElementById(API_TOKEN_DESCRIPTION_KEY) as HTMLInputElement;
        const description = descriptionElement.value;

        const requestedPermissions: Api.ApiTokenPermission[] = [];

        for (const [permission, actions] of activePermissions) {
            for (const action of actions) {
                requestedPermissions.push({name: permission, action});
            }
        }

        if (!title) {
            titleElement.setAttribute("data-error", "true");
            sendFailureNotification("Title is required");
            return;
        }

        if (date == null) {
            sendFailureNotification("Expiration date cannot be empty");
            return;
        }

        if (date.getTime() < new Date().getTime()) {
            sendFailureNotification("Expiration date cannot be in the past");
            return;
        }

        const provider = serviceProvider === "" ? null : serviceProvider;
        const projectOverride = provider == null
            ? projectId ?? ""
            : selectedService?.context === "personal" ? "" : undefined;

        if (provider != null && requestedPermissions.length === 0) {
            sendFailureNotification("Select one service and at least one action");
            return;
        }

        setLoading(true);
        try {
            const result = await callAPI<ApiToken>({
                ...Api.create({
                    title,
                    description,
                    requestedPermissions,
                    expiresAt: date.getTime(),
                    provider: provider,
                    product: {
                        category: "",
                        id: "",
                        provider: ""
                    },
                }),
                projectOverride
            });

            setTokenStatus(result.status);
        } catch (err) {
            displayErrorMessageOrDefault(err, "Failed to generate token.")
        } finally {
            setLoading(false);
        }
    }, [serviceProvider, activePermissions, date, projectId, selectedService]);

    useSubmitShortcut(submit, tokenStatus != null || loading);

    let main: React.ReactNode = null;

    if (tokenStatus != null) {
        main = <DocumentTypography>
            <div className={ApiTokenResultClass}>
                <Heading.h1>API token created</Heading.h1>
                <p>Copy the values below and store them in a secure location.</p>
                <Warning warning="This token will have to be copied now. It will not be shown again." />
                <div className="value">
                    <b>Server</b>
                    <code>{tokenStatus.server}</code>
                    <CopyButton tooltip="Copy server" onClick={() => copyToClipboard(tokenStatus.server)} />
                </div>
                <div className="value">
                    <b>Token</b>
                    <code>{tokenStatus.token}</code>
                    <CopyButton tooltip="Copy token" onClick={() => copyToClipboard(tokenStatus.token ?? "")} />
                </div>
                <Box mt={24}>
                    <Link to={AppRoutes.resources.apiTokens()}><Button>Back to overview</Button></Link>
                </Box>
            </div>
        </DocumentTypography>;
    } else {
        main = <DocumentTypography>
            <div className={ApiTokenCreateHeaderClass}>
                <div>
                    <Heading.h2>New API token</Heading.h2>
                    <div style={{maxWidth: "960px", color: "var(--textSecondary)"}}>
                        Create a token for UCloud or one service at a supported provider. Service-provider tokens
                        contain one service and may include multiple actions for that service.
                    </div>
                </div>
            </div>
            <KeyboardNavigation>
            <div className={ApiTokenCreateContentClass}>
                <Card>
                    <Heading.h3>Token details</Heading.h3>
                    <Box mt="16px">
                        <FieldGroup>
                            <FieldRow
                                title="Title"
                                description={"The title is shown on the overview page to identify the token."}
                                required
                                control={<Input id={API_TOKEN_TITLE_KEY} width="100%" placeholder="My API token" />}
                            />
                            <FieldRow
                                title="Description"
                                description="Optional details about how this token is used."
                                control={<TextArea id={API_TOKEN_DESCRIPTION_KEY} width="100%" rows={5}
                                    placeholder="This token is used in one of my scripts." />}
                            />
                            <FieldRow
                                title="Expiration"
                                description="The token stops working after this date."
                                required
                                control={<ExpirationSelector date={date} onChange={setDate} fullWidth />}
                            />
                        </FieldGroup>
                    </Box>
                </Card>

                <Card>
                    <Heading.h3>Permissions</Heading.h3>
                    <Box mt="16px">
                        <FieldGroup>
                            <FieldRow
                                title="Service provider"
                                description="Choose UCloud or a connected service provider."
                                required
                                control={<ServiceProviderSelector serviceProvider={serviceProvider}
                                    serviceProviders={mappedServiceProviders} showLabel={false} reserveLabelSpace={false} onSelect={el => {
                                        setServiceProvider(el.key);
                                        setActivePermissions(new Map());
                                    }} />}
                            />
                            {serviceProvider !== "" ? null :
                                <FieldRow
                                    title="Available for"
                                    description="Choose the project scope for this UCloud token."
                                    required
                                    control={<div className={ApiTokenProjectSelectorClass}>
                                         <ProjectSwitcher managed={{
                                             initialProject: projectId,
                                             setLocalProject: setProjectId
                                        }} focusable />
                                    </div>}
                                />
                            }
                            {serviceProviders.length === 0 || availablePermissions.length === 0 ? null :
                                <FieldRow
                                    title="Service"
                                    description={selectedService == null
                                        ? "Choose the service that this token can access."
                                        : selectedService.description}
                                    required
                                    onClear={selectedService == null ? undefined : () => setActivePermissions(new Map())}
                                    control={<div className={ApiTokenFullWidthSelectorClass}>
                                        <RichSelect
                                            fullWidth
                                            items={availablePermissions}
                                            keys={["title"]}
                                            selected={selectedService}
                                            RenderSelected={p => p.element == null ? <Flex height={"31.5px"} alignItems="center" pl={"8px"}>
                                                Select service
                                            </Flex> : <Permission {...p.element} dataProps={p.dataProps} onClick={p.onSelect} />}
                                            RenderRow={p => p.element == null ? null : <Permission
                                                {...p.element}
                                                dataProps={p.dataProps}
                                                onClick={p.onSelect}
                                            />}
                                            onSelect={p => {
                                                const firstAction = Object.keys(p.actions)[0];
                                                setActivePermissions(new Map([
                                                    [p.name, firstAction == null ? new Set() : new Set([firstAction])]
                                                ]));
                                            }}
                                            elementHeight={38}
                                            chevronPlacement={{position: "absolute", bottom: "8px", right: "12px", height: "16px"}}
                                        />
                                    </div>}
                                />
                            }
                            {selectedService == null ? null : Object.entries(selectedService.actions).map(([action, actionTitle]) =>
                                <FieldRow
                                    key={action}
                                    title={actionTitle}
                                    control={<Select
                                        value={activePermissions.get(selectedService.name)?.has(action) ? "yes" : "no"}
                                        onChange={event => {
                                            const enabled = event.target.value === "yes";
                                            setActivePermissions(current => {
                                                const next = new Map(current);
                                                const selectedActions = new Set(next.get(selectedService.name) ?? []);
                                                if (enabled) {
                                                    selectedActions.add(action);
                                                } else {
                                                    selectedActions.delete(action);
                                                }
                                                next.set(selectedService.name, selectedActions);
                                                return next;
                                            });
                                        }}
                                    >
                                        <option value="yes">Yes</option>
                                        <option value="no">No</option>
                                    </Select>}
                                />
                            )}
                        </FieldGroup>
                    </Box>
                </Card>

                <div className={ApiTokenSubmitClass}>
                    <Link to={Routes.resources.apiTokens()}>
                        <Button onClick={doNothing} color={"secondaryMain"}>Cancel</Button>
                    </Link>
                    <Button onClick={submit} color={"successMain"} disabled={loading}>Generate token<SubmitShortcut /></Button>
                </div>
            </div>
            </KeyboardNavigation>
        </DocumentTypography>;
    }

    return <MainContainer main={main} />;
}

function Permission(props: Api.ApiTokenPermissionSpecification & {
    onClick(): void;
    dataProps?: Record<string, string>;
}): React.ReactNode {
    const height = props.dataProps == null ? "31.5px" : "38px";
    return <Flex {...props.dataProps} onClick={props.onClick} height={height} alignItems={"center"} gap={"8px"} pl={"8px"}>
        <b>{props.title}</b>
        <span style={{fontSize: "12px"}}>
            ({props.context === "personal" ? "All projects" : "Current project"})
        </span>
    </Flex>
}

const UCLOUD_CORE = "UCloud";

export function ServiceProviderItem(props: RichSelectProps<{key: string}>): React.ReactNode {
    const height = props.dataProps == null ? "31.5px" : "38px";
    const key = props.element?.key;
    if (key == null) return null;
    const serviceProvider = !key ? UCLOUD_CORE : key;
    return <Flex height={height} pl="8px" key={key}  {...props.dataProps} onClick={props.onSelect} alignItems={"center"}
        gap={"8px"}>
        {!key ?
            <>
                <ProviderLogoWrapper size={24} className="provider-logo" tooltip={UCLOUD_CORE}>
                    <Image src={"/Images/ucloud.png"} alt={`Logo for ${UCLOUD_CORE}`} />
                </ProviderLogoWrapper>
                {UCLOUD_CORE}
            </> : <>
                <ProviderLogo className={"provider-logo"} providerId={serviceProvider} size={24} />
                <ProviderTitle providerId={key} />
            </>}
    </Flex>
}

export function ServiceProviderSelector({
    onSelect,
    serviceProvider,
    serviceProviders,
    renderRow = ServiceProviderItem,
    renderSelectedRow = ServiceProviderItem,
    showLabel = true,
    reserveLabelSpace = true,
    ...dataAttributes
}: {
    onSelect: (el: {key: string}) => void;
    serviceProvider: string;
    serviceProviders: {key: string}[];
    renderRow?: (props: RichSelectProps<{key: string}>) => React.ReactNode
    renderSelectedRow?: (props: RichSelectProps<{key: string}>) => React.ReactNode
    showLabel?: boolean;
    reserveLabelSpace?: boolean;
} & DataAttributes) {
    return <div className={ServiceProviderSelectorStyle} data-has-service-provider={!!serviceProvider}>
        {showLabel ? <>Service provider <MandatoryField /></> : reserveLabelSpace ? <Box width={"300px"} /> : null}
        <RichSelect
            fullWidth
            elementHeight={38}
            RenderSelected={renderSelectedRow}
            selected={({key: serviceProvider})}
            items={serviceProviders}
            keys={["key"]}
            {...dataAttributes}
            RenderRow={renderRow}
            onSelect={onSelect}>
        </RichSelect>
    </div>
}

const ServiceProviderSelectorStyle = injectStyle("service-selector", cl => `
    ${cl} {
        width: 100%;
    }

    ${cl}[data-has-service-provider=false] {
        margin-right: 12px;
    }

    ${cl} svg {
        bottom: 8px;
        right: 12px;
    }
`)

export function formatTs(ts: number): string {
    const d = new Date(ts);
    const baseFormat = `${d.getFullYear()}-${(d.getMonth() + 1).toString().padStart(2, '0')}-${d.getDate().toString().padStart(2, '0')}`;
    if (ts - Date.now() > 1000 * 60 * 60 * 24 * 365) {
        return baseFormat;
    } else {
        return `${formatDistanceToNow(d)} (${baseFormat})`;
    }
}

function formatDateInput(date: Date): string {
    return `${date.getFullYear()}-${(date.getMonth() + 1).toString().padStart(2, "0")}-${date.getDate().toString().padStart(2, "0")}`;
}

function ExpirationSelector(props: {date: Date | null; onChange(d: Date): void; fullWidth?: boolean}): React.ReactNode {
    const closeFn = React.useRef<() => void>(() => undefined);

    const onRelativeUpdated = React.useCallback((ev: React.SyntheticEvent) => {
        let today = new Date();
        today.setHours(0);
        today.setMinutes(0);
        today.setSeconds(0);

        const t = ev.target as HTMLElement;
        const distance = parseInt(t.getAttribute("data-unit") ?? "0", 10);
        const unit = t.getAttribute("data-relative-unit") as "month" | "day";

        switch (unit) {
            case "day":
                today = addDays(today, distance);
                break;
            case "month":
                today = addMonths(today, distance);
                break;
        }

        props.onChange(today);
    }, []);

    const onChange = React.useCallback((ev: React.SyntheticEvent) => {
        const target = ev.target as HTMLInputElement;
        if (!target) return;
        const date = target.valueAsDate;
        if (!date) return;
        date.setHours(0);
        date.setMinutes(0);
        date.setSeconds(0);
        props.onChange(date);
    }, []);

    return <ClickableDropdown
        colorOnHover={false}
        paddingControlledByContent
        noYPadding={true}
        fullWidth={props.fullWidth}
        contentWidth="max-content"
        trigger={
            <div className={ApiTokenExpirationSelectorClass}>
                <div>{props.date == null ? null : formatTs(props.date.getTime())}</div>
                <Icon name="heroChevronDown" />
            </div>
        }
        arrowkeyNavigationKey="data-active"
        hoverColor="rowHover"
        focusable
        closeFnRef={closeFn}
        onSelect={element => {
            if (element?.hasAttribute("data-custom-date")) {
                closeFn.current();
                return;
            }
            if (element instanceof HTMLElement) element.click();
        }}
    >
        <div className={DateSelector}>
            <div onClick={e => e.stopPropagation()} data-active="false" data-custom-date="true">
                <b>Specific date</b>
                <Input autoFocus pl="8px" pr="8px" className={"start"} onChange={onChange} type={"date"}
                    value={props.date == null ? undefined : formatDateInput(props.date)} />
            </div>
            <Divider />
            <div>
                <b>Relative date</b>

                <div onClick={onRelativeUpdated} className={"relative"} data-active="false" data-relative-unit={"day"}
                    data-unit={"7"}>7 days from today
                </div>
                <div onClick={onRelativeUpdated} className={"relative"} data-active="false" data-relative-unit={"day"}
                    data-unit={"30"}>30 days from today
                </div>
                <div onClick={onRelativeUpdated} className={"relative"} data-active="false" data-relative-unit={"day"}
                    data-unit={"90"}>90 days from today
                </div>
                <div onClick={onRelativeUpdated} className={"relative"} data-active="false" data-relative-unit={"month"}
                    data-unit={"6"}>6 months from today
                </div>
                <div onClick={onRelativeUpdated} className={"relative"} data-active="false" data-relative-unit={"month"}
                    data-unit={"12"}>12 months from today
                </div>
            </div>
        </div>
    </ClickableDropdown>;
};

const DateSelector = injectStyle("date-selector", cl => `
    ${cl} {
        margin-top: 8px;
        width: 350px;
    }

    ${cl} input {
        margin-left: 8px;
        margin-right: 8px;
        width: calc(100% - 16px);
    }

    ${cl} b {
        padding-left: 8px;
    }

    ${cl} > div:nth-child(3) {
        padding-top: 0px;
        gap: 0;
        display: grid;
    }

    ${cl} > div:nth-child(3) > div {
        height: 38px;
        display: flex;
        align-items: center;
        padding: 0 8px;
    }

    ${cl} > div:nth-child(3) > div:hover {
        cursor: pointer;
        background-color: var(--rowHover);
    }
`);

export default Add;
