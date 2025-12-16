import * as React from "react";
import {useCloudAPI} from "@/Authentication/DataHook";
import {Box, Button, Card, Divider, Flex, Icon, Input, MainContainer, Select} from "@/ui-components";
import * as Api from "./api";
import ClickableDropdown from "@/ui-components/ClickableDropdown";
import {PeriodStyle} from "@/Accounting/Usage";
import {addDays, addMonths} from "date-fns";
import {injectStyle, makeClassName} from "@/Unstyled";
import {GenericTextArea, GenericTextField, MandatoryField} from "@/UtilityComponents";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {ProviderLogo} from "@/Providers/ProviderLogo";
import {RichSelect, RichSelectProps} from "@/ui-components/RichSelect";
import {ProviderTitle} from "@/Providers/ProviderTitle";
import {ProjectSwitcher} from "@/Project/ProjectSwitcher";

function Add() {
    usePage("Add API token", SidebarTabId.RESOURCES);

    const [options] = useCloudAPI(Api.retrieveOptions(), {byProvider: {}});
    const [date, setDate] = React.useState(new Date());

    const [serviceProvider, setServiceProvider] = React.useState("");

    const permissions = options.data.byProvider;
    const permissionKeys = Object.keys(permissions);
    const serviceProviders = permissionKeys;
    const [projectId, setProjectId] = React.useState<string | undefined>();
    const [activePermissions, setActivePermissions] = React.useState(new Set<string>());

    const API_TOKEN_TITLE_KEY = "api-title";
    const API_TOKEN_DESCRIPTION_KEY = "api-title";

    const mappedServiceProviders = serviceProviders.map(it => ({key: it}));

    const availablePermissions = permissions[serviceProvider]?.availablePermissions ?? [];

    return <MainContainer main={
        <div style={{display: "grid", gap: "18px"}}>
            <div>
                <GenericTextField name={API_TOKEN_TITLE_KEY} title={"Title"} optional={false} />
                <GenericTextArea name={API_TOKEN_DESCRIPTION_KEY} optional title={"Description"} />
            </div>
            <Flex>
                <div className={ServiceProviderSelector} data-has-service-provider={!!serviceProvider}>
                    Service provider <MandatoryField />
                    <RichSelect
                        fullWidth
                        elementHeight={38}
                        RenderSelected={ServiceProviderItem}
                        selected={({key: serviceProvider})}
                        items={mappedServiceProviders}
                        keys={["key"]}
                        RenderRow={ServiceProviderItem}
                        onSelect={el => {
                            setServiceProvider(el.key);
                            setActivePermissions(new Set());
                        }}>
                    </RichSelect>
                </div>
                {serviceProvider !== "" ? null :
                    <div style={{gap: 0}}>
                        Available for <MandatoryField />
                        <ProjectSwitcher managed={{
                            initialProject: projectId,
                            setLocalProject: setProjectId
                        }} />
                    </div>
                }
            </Flex>
            <div>
                Expiration <MandatoryField />
                <div>
                    <ExpirationSelector date={date} onChange={setDate} />
                </div>
            </div>
            {permissionKeys.length == 0 ? null : <div>
                Token permissions <MandatoryField />
                <div className={PermissionWindow} data-has-active={activePermissions.size > 0}>
                    <div className="header">
                        <div>{activePermissions.size} permission(s)</div>
                        <Box ml="auto" width="135px">
                            <ClickableDropdown
                                trigger={"Add permissions"}
                                chevron
                                fullWidth
                            >
                                {availablePermissions.map(p => <Permission onClick={() => {
                                    if (activePermissions.has(p.name)) {
                                        return;
                                    } else {
                                        return setActivePermissions(set => new Set([...set, p.name]))
                                    }
                                }} {...p} />)}
                            </ClickableDropdown>
                        </Box>
                    </div>
                    {activePermissions.size > 0 ? <Divider m={"0px"} /> : null}
                    <div style={{maxHeight: "400px", overflowY: "auto"}}>
                        {[...activePermissions].map(p =>
                            <ActivePermissions clearPermission={() => {
                                setActivePermissions(permissions => {
                                    permissions.delete(p);
                                    return new Set([...permissions]);
                                })
                            }} permission={p} availablePermissions={availablePermissions} />
                        )}
                    </div>
                </div>
            </div>}
            <Button width="150px">Generate token</Button>
        </div >
    } />
}

const ActivePermissionClass = makeClassName("active-permission");
const ActivePermissionDescription = makeClassName("description");
const ActivePermissionTitle = makeClassName("title");
const PermissionWindow = injectStyle("permission-window", cl => `
    ${cl} {
        border-radius: 10px;
        border: var(--defaultCardBorder);
        color: var(--textPrimary);
    }

    ${cl} > div.header {
        display: flex;
        padding: 16px;
        background-color: var(--dialogToolbar);
        border-radius: 10px;
    }

    ${cl}[data-has-active=true] div.header {
        border-bottom-left-radius: 0px;
        border-bottom-right-radius: 0px;
    }

    ${cl} ${ActivePermissionClass.dot} {
        padding: 20px;
    }

    ${cl} ${ActivePermissionClass.dot} ${ActivePermissionTitle.dot} {
        font-size: 18px;
    }

    ${cl} ${ActivePermissionClass.dot} ${ActivePermissionDescription.dot} {
        color: var(--textSecondary);
        font-size: 14px;
    }
`);

function ActivePermissions(props: {clearPermission(): void; permission: string; availablePermissions: Api.ApiTokenPermissionSpecification[]}): React.ReactNode {
    const permissionSpecification = props.availablePermissions.find(it => it.name === props.permission);
    if (!permissionSpecification) return null;

    const actionKeys = Object.keys(permissionSpecification.actions);

    return <Flex data-permission={props.permission} className={ActivePermissionClass.class}>
        <div>
            <div className={ActivePermissionTitle.class}>
                {permissionSpecification.title}
            </div>
            <div className={ActivePermissionDescription.class}>
                {permissionSpecification.description}
            </div>
        </div>
        <Flex ml="auto" my="auto" height="35px">
            <Select ml="auto" width="180px" data-permission={props.permission}>
                {actionKeys.map(key => {
                    const value = permissionSpecification.actions[key];
                    return <option value={value}>{value}</option>
                })}
            </Select>
            <Icon onClick={props.clearPermission} cursor="pointer" name="close" mt="auto" ml="12px" mb="10px" />
        </Flex>
    </Flex>
}



function Permission(props: Api.ApiTokenPermissionSpecification & {
    onClick(): void;
}): React.ReactNode {
    return <Flex key={props.name} onClick={props.onClick} height={"32px"} alignItems={"center"} gap={"8px"}>
        <b>{props.title}</b>
    </Flex>
}

const UCLOUD_CORE = "UCloud/Core";

function ServiceProviderItem(props: RichSelectProps<{key: string}>): React.ReactNode {
    const height = props.dataProps == null ? "31.5px" : "38px";
    const key = props.element?.key;
    if (key == null) return null;
    const serviceProvider = !key ? UCLOUD_CORE : key;
    return <Flex height={height} pl="8px" key={key}  {...props.dataProps} onClick={props.onSelect} alignItems={"center"} gap={"8px"}>
        <ProviderLogo className={"provider-logo"} providerId={serviceProvider} size={24} />
        {!key ? UCLOUD_CORE : <ProviderTitle providerId={key} />}
    </Flex>
}

const ServiceProviderSelector = injectStyle("service-selector", cl => `
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

function ExpirationSelector(props: {date: Date; onChange(d: Date): void}): React.ReactNode {

    function formatTs(ts: number): string {
        const d = new Date(ts);
        return `${d.getFullYear()}-${(d.getMonth() + 1).toString().padStart(2, '0')}-${d.getDate().toString().padStart(2, '0')}`;
    }

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
        props.onChange(date);
    }, []);

    return <ClickableDropdown
        colorOnHover={false}
        paddingControlledByContent
        noYPadding={true}
        trigger={
            <div className={PeriodStyle}>
                <div style={{width: "182px"}}>{formatTs(props.date.getTime())}</div>
                <Icon name="heroChevronDown" size="14px" ml="4px" mt="4px" />
            </div>
        }
    >
        <div className={DateSelector}>
            <div onClick={e => e.stopPropagation()}>
                <b>Specific date</b>
                <Input pl="8px" pr="8px" className={"start"} onChange={onChange} type={"date"} value={formatTs(props.date.getTime())} />
            </div>
            <Divider />
            <div>
                <b>Relative date</b>

                <div onClick={onRelativeUpdated} className={"relative"} data-relative-unit={"day"}
                    data-unit={"7"}>7 days from today
                </div>
                <div onClick={onRelativeUpdated} className={"relative"} data-relative-unit={"day"}
                    data-unit={"30"}>30 days from today
                </div>
                <div onClick={onRelativeUpdated} className={"relative"} data-relative-unit={"day"}
                    data-unit={"90"}>90 days from today
                </div>
                <div onClick={onRelativeUpdated} className={"relative"} data-relative-unit={"month"}
                    data-unit={"6"}>6 months from today
                </div>
                <div onClick={onRelativeUpdated} className={"relative"} data-relative-unit={"month"}
                    data-unit={"12"}>12 months from today
                </div>
                <div onClick={onRelativeUpdated} className={"relative"} data-relative-unit={"month"}
                    data-unit={"36"}>3 years from today
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
        gap: 8px;
        display: grid;
    }

    ${cl} > div:nth-child(3) > div {
        flex-grow: 1;
        display: flex;
        flex-direction: column;
        padding: 8px;
        padding-left: 16px;
    }

    ${cl} > div:nth-child(3) > div:hover {
        cursor: pointer;
        background-color: var(--rowHover);
    }
`);

export default Add;