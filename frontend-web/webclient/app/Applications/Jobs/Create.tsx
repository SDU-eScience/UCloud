import * as React from "react";
import {JSX, useCallback, useEffect, useMemo, useState} from "react";
import {callAPI, InvokeCommand, useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {useLocation, useNavigate} from "react-router-dom";
import {MainContainer} from "@/ui-components/MainContainer";
import {ApplicationSelector, AppHeader} from "@/Applications/View";
import {
    Box,
    Button,
    Card,
    ExternalLink,
    Flex,
    Grid,
    Icon,
    Input,
    Label,
    Link,
    Markdown,
    Select,
} from "@/ui-components";
import {clearWidgetValue, FieldGroup, FieldRow, findElement, setWidgetValues, validateWidgets, Widget} from "@/Applications/Jobs/Widgets";
import * as Heading from "@/ui-components/Heading";
import {FolderResource, folderResourceAllowed} from "@/Applications/Jobs/Resources/Folders";
import {ingressResourceAllowed} from "@/Applications/Jobs/Resources/Ingress";
import {peerResourceAllowed} from "@/Applications/Jobs/Resources/Peers";
import {createSpaceForLoadedResources, injectResources, ResourceHook, useResource} from "@/Applications/Jobs/Resources";
import {
    awaitReservationMount,
    getReservationValues,
    ReservationErrors,
    ReservationParameter,
    setReservation,
    validateReservation
} from "@/Applications/Jobs/Widgets/Reservation";
import {
    bulkRequestOf,
    displayErrorMessageOrDefault,
    extractErrorCode,
    prettierString,
    createKeyboardShortcut,
    isLikelyMac,
    useDidMount
} from "@/UtilityFunctions";
import {addStandardDialog, OverallocationLink, WalletWarning} from "@/UtilityComponents";
import {ImportMessages, ImportMessage, ImportParameters} from "@/Applications/Jobs/Widgets/ImportParameters";
import LoadingIcon from "@/LoadingIcon/LoadingIcon";
import {usePage} from "@/Navigation/Redux";
import {networkIPResourceAllowed} from "@/Applications/Jobs/Resources/NetworkIPs";
import {getQueryParam} from "@/Utilities/URIUtilities";
import {default as JobsApi, DynamicParameters, JobSpecification} from "@/UCloud/JobsApi";
import {BulkResponse, compute, FindByStringId, mail} from "@/UCloud";
import {balanceToStringFromUnit, calculateProductCost, explainUnit, explainWallet, priceToString, ProductV2, ProductV2Compute, UNABLE_TO_USE_FULL_ALLOC_MESSAGE, WalletV2} from "@/Accounting";
import {SshWidget} from "@/Applications/Jobs/Widgets/Ssh";
import {connectionState} from "@/Providers/ConnectionState";
import {useUState} from "@/Utilities/UState";
import {injectStyle} from "@/Unstyled";
import {UtilityBar} from "@/Navigation/UtilityBar";
import {validateMachineReservation} from "@/Applications/Jobs/Widgets/Machines";
import {Resource} from "@/UCloud/ResourceApi";
import {getProviderTitle} from "@/Providers/ProviderTitle";
import * as AppStore from "@/Applications/AppStoreApi";
import {Application, ApplicationGroup, ApplicationParameter} from "@/Applications/AppStoreApi";
import {TooltipV2} from "@/ui-components/Tooltip";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {defaultEmailSettings, UserDetailsState} from "@/UserSettings/ChangeEmailSettings";
import retrieveEmailSettings = mail.retrieveEmailSettings;
import toggleEmailSettings = mail.toggleEmailSettings;
import {useDiscovery} from "@/Applications/Hooks";
import {sendFailureNotification, sendSuccessNotification} from "@/Notifications";
import {CreateUcxJob} from "@/Applications/Jobs/CreateUcx";
import * as ApiTokens from "@/Applications/ApiTokens/api";
import Warning from "@/ui-components/Warning";
import {ShortcutClass} from "@/ui-components/ResourceBrowserStyle";
import {CompactResourceRowsContent} from "@/Applications/Jobs/Resources/CompactResourceRows";
import {stupidPluralize} from "@/Utilities/TextUtilities";
import {UcxSpinner} from "@/UCX/UcxView";
import {
    closeOpenDropdown,
    FIELD_NAVIGATION_SELECTOR,
    isDisabledNavigationTarget,
    KeyboardNavigation,
    SubmitShortcut,
    useSubmitShortcut,
} from "@/Applications/KeyboardNavigation";
import {useProjectId} from "@/Project/Api";
import AppRoutes from "@/Routes";
import {ApplicationForkAction} from "@/Applications/Creator/ForkAction";
import {Client} from "@/Authentication/HttpClientInstance";
import {Feature, hasFeature} from "@/Features";

interface InsufficientFunds {
    why?: string;
    errorCode?: string;
}

function JobCardHeading({children, shortcut, shortcutsVisible, action}: React.PropsWithChildren<{
    shortcut: string;
    shortcutsVisible: boolean;
    action?: React.ReactNode;
}>): React.ReactNode {
    return <Flex alignItems="center" gap="8px" mb={"16px"}>
        <Heading.h4>{children}</Heading.h4>
        {!shortcutsVisible ? null : (
            <span className={ShortcutClass}>{createKeyboardShortcut(shortcut, ["ctrl", "alt"])}</span>
        )}
        {!action ? null : <Box ml="auto">{action}</Box>}
    </Flex>;
}

function removeDisplayedUnit(value: string, unit: string): string {
    const suffix = ` ${unit}`;
    return value.endsWith(suffix) ? value.slice(0, -suffix.length) : value;
}

const EstimatesContainerClass = injectStyle("estimates-container", k => `
    ${k} {
        margin-top: 0;
    }
    
    ${k} table {
        width: 100%;
    }
    
    ${k} th {
        text-align: left;
        padding-right: 10px;
    }
    
    ${k} td {
        font-variant-numeric: tabular-nums;
        text-align: right;
    }

    ${k} .cost-unit {
        color: var(--textSecondary);
        font-size: 12px;
        font-weight: 400;
        text-align: right;
        padding-right: 0;
    }
`);

const JobCreateLayoutClass = injectStyle("job-create-layout", key => `
    ${key} {
        display: grid;
        grid-template-areas: "main sidebar";
        grid-template-columns: minmax(0, 1fr) 300px;
        gap: 24px;
        width: 100%;
        align-items: start;
    }

    @media (max-width: 1000px) {
        ${key} {
            grid-template-areas: "sidebar" "main";
            grid-template-columns: minmax(0, 1fr);
            padding-bottom: 96px;
        }
    }
`);

const JobCreateMainClass = injectStyle("job-create-main", key => `
    ${key} {
        grid-area: main;
        min-width: 0;
    }
`);

const JobCreateHeaderClass = injectStyle("job-create-header", key => `
    ${key} {
        display: flex;
        margin: 32px 50px 24px;
    }

    @media (max-width: 600px) {
        ${key} {
            display: grid;
            grid-template-columns: minmax(0, 1fr);
            gap: 16px;
            margin: 24px 16px 24px;
        }

        ${key} .job-create-header-spacer {
            display: none;
        }
    }
`);

const JobCreateHeaderActionsClass = injectStyle("job-create-header-actions", key => `
    ${key} {
        display: flex;
        flex-direction: column;
        align-items: flex-end;
        gap: 8px;
        min-width: 0;
    }

    @media (max-width: 600px) {
        ${key} {
            align-items: stretch;
            width: 100%;
        }

        ${key} .job-create-documentation {
            display: none;
        }
    }
`);

const KeyboardNavigationGuideClass = injectStyle("keyboard-navigation-guide", key => `
    ${key} {
        color: var(--textSecondary);
        font-size: 12px;
        display: flex;
        flex-direction: column;
        gap: 16px;
        margin-top: 24px;
    }

    ${key} .keyboard-navigation-row {
        display: grid;
        grid-template-columns: minmax(0, 1fr) auto;
        align-items: center;
        gap: 12px;
    }

    ${key} .keyboard-navigation-action {
        text-align: right;
    }

    @media (max-width: 1000px) {
        ${key} {
            display: none;
        }
    }
`);

const JobCreateContentClass = injectStyle("job-create-content", key => `
    ${key} {
        display: flex;
        flex-direction: column;
        align-items: center;
        margin: 0 50px;
    }

    @media (max-width: 600px) {
        ${key} {
            margin: 0 16px;
        }
    }
`);

const JobSubmissionSidebarClass = injectStyle("job-submission-sidebar", key => `
    ${key} {
        grid-area: sidebar;
        position: sticky;
        top: 20px;
        min-width: 0;
    }

    @media (max-width: 1000px) {
        ${key} {
            position: static;
        }
    }
`);

const JobSubmissionSecondaryClass = injectStyle("job-submission-secondary", key => `
    ${key} > *:first-child {
        width: 100%;
    }
`);

const JobSubmissionSummaryClass = injectStyle("job-submission-summary", key => `
    ${key} {
        border-top: 1px solid var(--borderColor);
        margin-top: 20px;
        padding-top: 16px;
    }

    @media (max-width: 1000px) {
        ${key} {
            position: fixed;
            left: calc(var(--sidebarBlockWidth, var(--sidebarWidth)) + 16px);
            right: 16px;
            bottom: 16px;
            z-index: 1000;
            display: grid;
            grid-template-columns: minmax(0, 1fr) auto;
            gap: 16px;
            align-items: center;
            margin: 0;
            padding: 12px 16px;
            border: 1px solid var(--borderColor);
            border-radius: 8px;
            background: var(--backgroundDefault);
            box-shadow: var(--defaultShadow);
        }

        ${key} .desktop-cost-row {
            display: none;
        }
    }
`);

const JobSubmitButtonClass = injectStyle("job-submit-button", key => `
    ${key} {
        margin-top: 24px;
    }

    ${key} button {
        width: 100%;
    }

    @media (max-width: 1000px) {
        ${key} {
            margin-top: 0;
        }

        ${key} button {
            width: auto;
            white-space: nowrap;
        }
    }
`);

const PARAMETER_TYPE_FILTER = ["input_directory", "input_file", "ingress", "peer", "license_server", "network_ip"];

const initialState: UserDetailsState = {
    settings: defaultEmailSettings
};

function filterUnsetOptionalParameters(
    parameters: ApplicationParameter[],
    values: Record<string, compute.AppParameterValue>,
    activeOptionalParameters: string[],
): Record<string, compute.AppParameterValue> {
    const active = new Set(activeOptionalParameters);
    return Object.fromEntries(Object.entries(values).filter(([name]) => {
        const parameter = parameters.find(it => it.name === name);
        return parameter?.optional !== true || active.has(name);
    }));
}

function getLicense(app: Application): string | undefined {
    return app.invocation.tool.tool?.description.license
}

export interface JobCreateProps {
    previewApplication?: Application;
    previewMode?: boolean;
    previewRendering?: boolean;
    onPreviewScript?: (request: JobSpecification) => Promise<void>;
    onPreviewParametersChange?: (parameters: ApplicationParameter[]) => void;
    onPreviewMachinesChange?: (machines: ProductV2Compute[]) => void;
    onPreviewDataReady?: (ready: boolean) => void;
}

export const Create: React.FunctionComponent<JobCreateProps> = props => {
    const [emailNotifications, setEmailNotifications] = React.useState<UserDetailsState>(initialState);
    const [jobEmailNotifications, setJobEmailNotifications] = useState<"never" | "start" | "ends" | "start_or_ends">("never");
    const [shortcutsVisible, setShortcutsVisible] = useState(false);
    const navigate = useNavigate();
    const location = useLocation();
    const previewMode = props.previewMode === true;
    const projectId = useProjectId();
    const appName = props.previewApplication?.metadata.name ?? getQueryParam(location.search, "app");
    const appVersion = getQueryParam(location.search, "version") ?? props.previewApplication?.metadata.version;

    if (!appName) {
        if (previewMode) return null;
        // Note: This is incorrect use of hooks, but this case should be unreachable
        navigate("/");
        return null;
    }

    const isInitialMount = !useDidMount();
    const [isLoading, invokeCommand] = useCloudCommand();
    const [applicationResp, fetchApplication] = useCloudAPI<ApplicationGroup | null>(
        {noop: true},
        null
    );
    const [flavors, setFlavors] = useState<Application[]>([]);
    const [injectedParameters, fetchInjectedParameters] = useCloudAPI<DynamicParameters | null>(
        {noop: true},
        null
    );
    const [machineSupport, fetchMachineSupport] = useCloudAPI<compute.JobsRetrieveProductsResponse>(
        {noop: true},
        {productsByProvider: {}}
    );
    const [workflowInjectedParameters, setWorkflowInjectParameters] = useState<ApplicationParameter[]>([]);
    const [dynamicParametersLoadedFor, setDynamicParametersLoadedFor] = useState<string | null>(null);
    const previewParameterTypes = React.useRef<Record<string, ApplicationParameter["type"]> | null>(null);

    const application = props.previewApplication ?? applicationResp?.data?.status?.applications?.find(it => it.metadata.name === appName);
    const [canEditCustomVersion, setCanEditCustomVersion] = useState(false);

    if (application) {
        usePage(`${application.metadata.title} ${application.metadata.version ?? ""}`, SidebarTabId.APPLICATIONS);
    } else {
        usePage(`${appName} ${appVersion ?? ""}`, SidebarTabId.APPLICATIONS);
    }

    const [estimatedCost, setEstimatedCost] = useState<{
        durationInMinutes: number,
        numberOfNodes: number,
        wallet: WalletV2 | null,
        product: ProductV2 | null
    }>({
        durationInMinutes: 0,
        wallet: null,
        numberOfNodes: 1,
        product: null
    });
    const [reloadHack, setReloadHack] = useState<{importFrom: Partial<JobSpecification>, count: number} | null>(null);
    const [insufficientFunds, setInsufficientFunds] = useState<InsufficientFunds | null>(null);
    const [errors, setErrors] = useState<Record<string, string>>({});
    const [initialSshEnabled, setInitialSshEnabled] = useState<boolean | undefined>(undefined);
    const [sshEnabled, setSshEnabled] = useState(false);
    const [sshValid, setSshValid] = useState(true);
    const [bindLinkToPort, setBindLinkToPort] = useState(false);
    const displayWallet = useMemo(() => {
        const wallet = estimatedCost.wallet;
        if (wallet === null) return null;
        return explainWallet(wallet);
    }, [estimatedCost.wallet]);
    const costUnit = displayWallet?.usageAndQuota.raw.unit ??
        (estimatedCost.product ? explainUnit(estimatedCost.product.category).name : "");
    const [discovery] = useDiscovery();

    useEffect(() => {
        setFlavors(applicationResp?.data?.status?.applications ?? []);
    }, [applicationResp?.data]);

    useEffect(() => {
        setCanEditCustomVersion(false);
        if (previewMode || application?.metadata.origin !== "CUSTOM") return;
        const provider = application.invocation.tool.tool?.description.supportedProviders?.[0];
        if (!provider) return;

        let cancelled = false;
        callAPI(AppStore.retrieveEditorSource({
            kind: "CUSTOM",
            name: application.metadata.name.replace(/^custom-/, ""),
            version: application.metadata.version,
            serviceProvider: provider,
            intent: "EDIT",
        })).then(() => {
            if (!cancelled) setCanEditCustomVersion(true);
        }).catch(() => {
            if (!cancelled) setCanEditCustomVersion(false);
        });
        return () => {
            cancelled = true;
        };
    }, [application?.metadata.name, application?.metadata.version, application?.metadata.origin, projectId, previewMode]);

    const reloadFlavors = useCallback(async () => {
        const group = await callAPI(AppStore.findGroupByApplication({
            appName,
            appVersion: appVersion ?? undefined,
            flags: {
                includeApplications: true,
                includeInvocation: true,
                includeStars: true,
                includeVersions: true,
            },
            ...discovery,
        }));
        const updatedFlavors = group.status.applications ?? [];
        setFlavors(updatedFlavors);
        return updatedFlavors;
    }, [appName, appVersion, discovery]);

    const dnsHostnameSeed = React.useRef((Math.floor(Math.random() * 9000) + 1000).toString());
    const [jobName, setJobName] = useState("");
    const [dnsHostname, setDnsHostname] = useState("");
    const [hasCustomDnsHostname, setHasCustomDnsHostname] = useState(false);

    const defaultDnsHostname = useMemo(() => {
        const fallbackJobName = `${application?.metadata.title ?? appName}-${dnsHostnameSeed.current}`;
        return toDnsSafeHostname(jobName || fallbackJobName);
    }, [application?.metadata.title, appName, jobName]);

    useEffect(() => {
        if (!hasCustomDnsHostname) {
            setDnsHostname(defaultDnsHostname);
        }
    }, [defaultDnsHostname, hasCustomDnsHostname]);

    const onDnsHostnameChange = useCallback((ev: React.SyntheticEvent) => {
        const elem = ev.target as HTMLInputElement;
        const sanitized = toDnsSafeHostname(elem.value);
        setHasCustomDnsHostname(true);
        setDnsHostname(sanitized);
    }, []);

    useEffect(() => {
        const product = estimatedCost.product;
        const backend = application?.invocation.tool.tool?.description?.backend ?? "DOCKER";
        if (!product || product.productType !== "COMPUTE") {
            setBindLinkToPort(backend === "VIRTUAL_MACHINE");
            return;
        }

        fetchMachineSupport(compute.jobs.retrieveProducts({
            providers: product.category.provider,
        }));
    }, [application, estimatedCost.product, fetchMachineSupport]);

    useEffect(() => {
        const product = estimatedCost.product;
        const backend = application?.invocation.tool.tool?.description?.backend ?? "DOCKER";
        if (!product || product.productType !== "COMPUTE") {
            setBindLinkToPort(backend === "VIRTUAL_MACHINE");
            return;
        }

        const providerProducts = machineSupport.data.productsByProvider[product.category.provider] ?? [];
        const selectedSupport = providerProducts.find(item =>
            item.product.category.provider === product.category.provider &&
            item.product.category.name === product.category.name &&
            item.product.name === product.name
        )?.support;

        if (!selectedSupport) {
            setBindLinkToPort(backend === "VIRTUAL_MACHINE");
            return;
        }

        switch (backend) {
            case "DOCKER":
                setBindLinkToPort(selectedSupport.docker.bindLinkToPort === true);
                break;
            case "NATIVE":
                setBindLinkToPort(selectedSupport.native.bindLinkToPort === true);
                break;
            case "VIRTUAL_MACHINE":
                setBindLinkToPort(selectedSupport.virtualMachine.bindLinkToPort === true);
                break;
            default:
                setBindLinkToPort(false);
                break;
        }
    }, [estimatedCost.product, machineSupport.data, application]);

    const provider = getProviderField();

    const networks = useResource("network", provider,
        (name) => ({type: "network_ip", description: "", title: "", optional: true, name}));
    const ingress = useResource("ingress", provider,
        (name) => ({type: "ingress", description: "", title: "", optional: true, name}));
    const folders = useResource("resourceFolder", provider,
        (name) => ({type: "input_directory", description: "", title: "", optional: true, name}));
    const peers = useResource("resourcePeer", provider,
        (name) => ({type: "peer", description: "", title: "", optional: true, name}));
    const privateNetworks = useResource("resourcePrivateNetwork", provider,
        (name) => ({type: "private_network", description: "", title: "", optional: true, name}));

    const [activeOptParams, setActiveOptParams] = useState<string[]>([]);
    const [cacheInitScript, setCacheInitScript] = useState(false);
    const [reservationErrors, setReservationErrors] = useState<ReservationErrors>({});

    const [importDialogOpen, setImportDialogOpen] = useState(false);
    const [importMessages, setImportMessages] = useState<ImportMessage[]>([]);
    const closeImportDialog = useCallback(() => setImportDialogOpen(false), []);

    const retrieveEmailNotificationSettings = useCallback(async () => {
        const emailSettings = await invokeCommand(
            retrieveEmailSettings({}),
            {defaultErrorHandler: false}
        );

        setEmailNotifications({
            settings: emailSettings?.settings ?? defaultEmailSettings
        });
    }, []);

    useEffect(() => {
        const jobStarted = emailNotifications.settings.jobStarted;
        const jobStopped = emailNotifications.settings.jobStopped;

        if (jobStarted && jobStopped) {
            setJobEmailNotifications("start_or_ends");
        } else if (jobStarted && !jobStopped) {
            setJobEmailNotifications("start");
        } else if (!jobStarted && jobStopped) {
            setJobEmailNotifications("ends");
        } else {
            setJobEmailNotifications("never");
        }
    }, [emailNotifications]);

    useEffect(() => {
        if (previewMode) return;
        retrieveEmailNotificationSettings();
    }, [previewMode, retrieveEmailNotificationSettings]);

    const onChangeJobEmailNotification = useCallback(async (ev: React.SyntheticEvent) => {
        ev.stopPropagation();
        const elem = ev.target as HTMLSelectElement;
        const value = elem.value;
        switch (value) {
            case "never": {
                emailNotifications.settings.jobStarted = false;
                emailNotifications.settings.jobStopped = false;
                break;
            }
            case "start": {
                emailNotifications.settings.jobStarted = true;
                emailNotifications.settings.jobStopped = false;
                break;
            }
            case "ends": {
                emailNotifications.settings.jobStarted = false;
                emailNotifications.settings.jobStopped = true;
                break;
            }
            case "start_or_ends": {
                emailNotifications.settings.jobStarted = true;
                emailNotifications.settings.jobStopped = true;
                break;
            }
        }

        setEmailNotifications(emailNotifications);

        const wasSuccessful = await invokeCommand(toggleEmailSettings(bulkRequestOf({
            settings: emailNotifications.settings
        }))) !== null;

        if (!wasSuccessful) {
            sendFailureNotification("Failed to update user email settings");
        } else {
            sendSuccessNotification("User email settings updated");
        }

    }, [emailNotifications]);

    useUState(connectionState);

    const appParams = React.useRef<{siteVersion: 3; request: Partial<JobSpecification>} | null>(null);
    // NOTE(Jonas): Not entirely sure a ref is strictly needed, but should be more consistent.
    const sshEnabledRef = React.useRef(false);
    sshEnabledRef.current = sshEnabled;
    useEffect(() => {
        if (previewMode) return;
        if (appName === "syncthing" && !localStorage.getItem("syncthingRedirect")) {
            navigate("/drives");
        }

        fetchApplication(
            AppStore.findGroupByApplication({
                appName,
                appVersion: appVersion ?? undefined,
                flags: {
                    includeApplications: true,
                    includeInvocation: true,
                    includeStars: true,
                    includeVersions: true
                },
                ...discovery,
            })
        );
    }, [appName, appVersion, discovery, previewMode]);

    useEffect(() => {
        if (!application) return;
        const applicationKey = `${application.metadata.name}:${application.metadata.version}`;
        fetchInjectedParameters(JobsApi.requestDynamicParameters({
            application: {name: application.metadata.name, version: application.metadata.version}
        })).then(() => {
            setDynamicParametersLoadedFor(applicationKey);
        });
    }, [application, previewMode]);

    useEffect(() => {
        if (!previewMode || !application) return;
        const nextParameters = Object.fromEntries(
            (application.invocation.parameters ?? []).map(parameter => [parameter.name, parameter.type]),
        );
        const previousParameters = previewParameterTypes.current;
        if (previousParameters) {
            const resetParameters = Object.keys(previousParameters).filter(name =>
                nextParameters[name] === undefined || nextParameters[name] !== previousParameters[name],
            );
            if (resetParameters.length > 0) {
                for (const parameter of application.invocation.parameters ?? []) {
                    if (resetParameters.includes(parameter.name)) clearWidgetValue(parameter);
                }
            }
        }
        previewParameterTypes.current = nextParameters;
    }, [application, previewMode]);

    const dynamicParametersLoaded = application != null &&
        dynamicParametersLoadedFor === `${application.metadata.name}:${application.metadata.version}`;
    const canImportParameters = dynamicParametersLoaded && injectedParameters.data !== null;

    useEffect(() => {
        if (!canImportParameters) setImportMessages([]);
    }, [canImportParameters]);

    useEffect(() => {
        if (!previewMode) return;
        props.onPreviewDataReady?.(dynamicParametersLoaded);
    }, [dynamicParametersLoaded, previewMode, props.onPreviewDataReady]);

    const parameters = useMemo(() => {
        let injected: ApplicationParameter[] = [];
        const injectedData = injectedParameters.data;
        if (injectedData) {
            const parametersByProvider = injectedData.parametersByProvider;
            if (estimatedCost.product) {
                const provider = estimatedCost.product.category.provider;
                injected = parametersByProvider[provider] ?? [];
            } else {
                const providerLists = Object.values(parametersByProvider);
                if (providerLists.length > 0) {
                    const [firstProviderList] = providerLists;
                    const occurrences = new Map<string, number>();

                    for (const providerList of providerLists) {
                        const seenInProvider = new Set<string>();
                        for (const parameter of providerList) {
                            const key = `${parameter.name}::${parameter.type}`;
                            if (seenInProvider.has(key)) continue;
                            seenInProvider.add(key);
                            occurrences.set(key, (occurrences.get(key) ?? 0) + 1);
                        }
                    }

                    injected = firstProviderList.filter(parameter => {
                        const key = `${parameter.name}::${parameter.type}`;
                        return occurrences.get(key) === providerLists.length;
                    });
                }
            }
        }
        const fromApp = application?.invocation?.parameters ?? [];
        return [...injected, ...fromApp, ...workflowInjectedParameters];
    }, [application, injectedParameters, workflowInjectedParameters, estimatedCost.product]);

    useEffect(() => {
        if (!previewMode) return;
        props.onPreviewParametersChange?.(parameters);
    }, [parameters, previewMode, props.onPreviewParametersChange]);

    const saveParameters = () => {
        if (!application) return;

        const reservationOptions = getReservationValues();
        const {values} = validateWidgets(parameters);
        const parameterValues = filterUnsetOptionalParameters(parameters, values, activeOptParams);
        const foldersResources = validateWidgets(folders.params);
        // TODO(Jonas): This should preferably not validate, but just get the values (e.g. one field could be missing)
        const peersResources = validateWidgets(peers.params);
        const networkResources = validateWidgets(networks.params);
        const ingressResources = validateWidgets(ingress.params);
        const privateNetworkResources = validateWidgets(privateNetworks.params);
        for (const err of [
            ...Object.values(foldersResources.errors).map(it => "Folders: " + it),
            ...Object.values(peersResources.errors).map(it => "Connected jobs: " + it),
            ...Object.values(networkResources.errors).map(it => "IPs: " + it),
            ...Object.values(ingressResources.errors).map(it => "Public link: " + it),
            ...Object.values(privateNetworkResources.errors).map(it => "Private network: " + it)
        ]) {
            sendFailureNotification(err);
        }

        appParams.current = {
            siteVersion: 3,
            request: {
                ...reservationOptions,
                parameters: parameterValues,
                resources: Object.values(foldersResources.values)
                    .concat(Object.values(peersResources.values))
                    .concat(Object.values(ingressResources.values))
                    .concat(Object.values(networkResources.values))
                    .concat(Object.values(privateNetworkResources.values)),
                sshEnabled: sshEnabledRef.current
            }
        };
    };


    React.useEffect(() => {
        if (application && provider) {
            const params = parameters.filter(it =>
                PARAMETER_TYPE_FILTER.includes(it.type)
            );

            findProviderMismatches(
                provider, {errors, params, setErrors}, networks, folders, peers, ingress, privateNetworks
            );
        }
    }, [provider, application]);

    const doLoadParameters = useCallback(async (importedJob: Partial<JobSpecification>, initialImport?: boolean) => {
        if (application == null) return;
        const values = importedJob.parameters ?? {};
        const importedCacheSetting = values.ucCacheInitScript;
        setCacheInitScript(importedCacheSetting?.type === "boolean" && importedCacheSetting.value === true);
        const resources = importedJob.resources ?? [];

        if (initialImport) {
            setActiveOptParams([]);
        }

        {
            // Find optional parameters and make sure the widgets are initialized
            const optionalParameters: string[] = [];
            let needsToRenderParams = false;
            for (const param of parameters) {
                if (param.optional && values[param.name]) {
                    optionalParameters.push(param.name);
                    if (activeOptParams.indexOf(param.name) === -1 || initialImport) {
                        needsToRenderParams = true;
                    }
                }
            }

            if (needsToRenderParams && initialImport) {
                // Not all widgets have been initialized. Trigger an initialization and start over after render.
                setActiveOptParams(() => optionalParameters);
            }
        }

        // Load reservation
        try {
            await awaitReservationMount();
            setReservation(importedJob);
            setJobName(importedJob.name ?? "");
        } catch (e) {
            console.warn(e);
        }

        // Load parameters
        for (const param of parameters) {
            const value = values[param.name];
            if (value) {
                try {
                    setWidgetValues([{param, value}]);
                } catch (e) {}
            }
        }

        // Load SSH
        const sshEnabled = importedJob.sshEnabled;
        if (sshEnabled != undefined) {
            setInitialSshEnabled(sshEnabled);
        }

        // Load resources
        // Note(Jonas): An older version could have run with one of these resources while a newer might not allow them.
        // Therefore, check to see if allowed!
        // Note(Jonas) Pt. II: The actual injection of resources should happen after the function terminates, as React will re-render the component,
        // and only then will the required input-fields be present. The setTimeout-callback will then be called to fill in the newly created input-fields.
        if (folderResourceAllowed(application)) {
            const newSpace = createSpaceForLoadedResources(folders, resources, "file", true);
            setTimeout(() => injectResources(newSpace, resources, "file"), 0);
        }
        if (peerResourceAllowed(application)) {
            const newSpace = createSpaceForLoadedResources(peers, resources, "peer");
            setTimeout(() => injectResources(newSpace, resources, "peer"), 0);
        }
        if (ingressResourceAllowed(application, bindLinkToPort)) {
            const newSpace = createSpaceForLoadedResources(ingress, resources, "ingress", true);
            setTimeout(() => injectResources(newSpace, resources, "ingress"), 0);
        }
        if (networkIPResourceAllowed(application)) {
            const newSpace = createSpaceForLoadedResources(networks, resources, "network", true);
            setTimeout(() => injectResources(newSpace, resources, "network"), 0);
        }
        const newSpace = createSpaceForLoadedResources(privateNetworks, resources, "private_network", true);
        setTimeout(() => injectResources(newSpace, resources, "private_network"), 0);

        folders.setErrors({});
        ingress.setErrors({});
        networks.setErrors({});
        peers.setErrors({});
        privateNetworks.setErrors({});
        setErrors({});
        setReservationErrors({});
    }, [application, activeOptParams, folders, peers, networks, ingress, privateNetworks, parameters, bindLinkToPort]);

    const reloadCount = 3;
    const onLoadParameters = useCallback((importedJob: Partial<JobSpecification>) => {
        setReloadHack({importFrom: importedJob, count: reloadCount});
    }, []);

    useEffect(() => {
        if (reloadHack) {
            doLoadParameters(reloadHack.importFrom, reloadHack.count === reloadCount);
            const newCount = reloadHack.count - 1;
            if (newCount > 0) {
                setReloadHack({importFrom: reloadHack.importFrom, count: newCount});
            } else {
                appParams.current = null;
            }
        }
    }, [onLoadParameters, reloadHack]);

    const submitJob = useCallback(async (allowDuplicateJob: boolean) => {
        if (!application) return;

        const {errors, values} = validateWidgets(parameters!);
        const parameterValues = filterUnsetOptionalParameters(parameters!, values, activeOptParams);
        setErrors(errors)

        const reservationValidation = validateReservation();
        setReservationErrors(reservationValidation.errors);

        const foldersValidation = validateWidgets(folders.params);
        folders.setErrors(foldersValidation.errors);

        const peersValidation = validateWidgets(peers.params);
        peers.setErrors(peersValidation.errors);

        const networkValidation = validateWidgets(networks.params);
        networks.setErrors(networkValidation.errors);

        const ingressValidation = validateWidgets(ingress.params);
        ingress.setErrors(ingressValidation.errors);

        const privateNetworkValidation = validateWidgets(privateNetworks.params);
        privateNetworks.setErrors(privateNetworkValidation.errors);

        if (Object.keys(errors).length === 0 &&
            reservationValidation.options !== undefined &&
            Object.keys(foldersValidation.errors).length === 0 &&
            Object.keys(peersValidation.errors).length === 0 &&
            Object.keys(networkValidation.errors).length === 0 &&
            Object.keys(ingressValidation.errors).length === 0 &&
            Object.keys(privateNetworkValidation.errors).length === 0
        ) {
            const dnsHostnameForSubmission = hasCustomDnsHostname
                ? toDnsSafeHostname(dnsHostname)
                : toDnsSafeHostname(
                    reservationValidation.options.name || `${application.metadata.title}-${dnsHostnameSeed.current}`
                );

            const request: JobSpecification = {
                ...reservationValidation.options,
                application: application?.metadata,
                parameters: parameterValues,
                resources: Object.values(foldersValidation.values)
                    .concat(Object.values(peersValidation.values))
                    .concat(Object.values(ingressValidation.values))
                    .concat(Object.values(networkValidation.values))
                    .concat(Object.values(privateNetworkValidation.values)),
                sshEnabled,
                allowDuplicateJob,
                hostname: dnsHostnameForSubmission,
            };

            try {
                if (previewMode) {
                    await props.onPreviewScript?.(request);
                    return;
                }
                request.resources = (request.resources ?? []).concat(await createInferenceApiServerResources(application, invokeCommand));

                const response = await invokeCommand<BulkResponse<FindByStringId | null>>(
                    JobsApi.create(bulkRequestOf(request)),
                    {defaultErrorHandler: false}
                );

                const ids = response?.responses;
                if (!ids || ids.length === 0) {
                    sendFailureNotification("UCloud failed to submit the job");
                    return;
                }

                navigate(`/jobs/properties/${ids[0]?.id}?app=${application.metadata.name}`);
            } catch (e: any) {
                const code = extractErrorCode(e);
                if (code === 409) {
                    addStandardDialog({
                        title: "Job with same parameters already running",
                        message: "You might be trying to run a duplicate job. Would you like to proceed?",
                        cancelText: "No",
                        confirmText: "Yes",
                        onConfirm: () => {
                            submitJob(true);
                        },
                    });
                } else if (code == 402) {
                    const why = e?.response?.why;
                    const errorCode = e?.response?.errorCode;
                    setInsufficientFunds({why, errorCode});
                } else {
                    displayErrorMessageOrDefault(e, "An error occurred while submitting the job");
                }
            }
        }
    }, [application, folders, peers, ingress, networks, privateNetworks, navigate, hasCustomDnsHostname, dnsHostname,
        parameters, activeOptParams, previewMode, props.onPreviewScript]);

    const isMissingConnection = estimatedCost.product != null &&
        connectionState.canConnectToProvider(estimatedCost.product.category.provider);
    const errorCount = countMandatoryAndOptionalErrors(parameters.filter(it =>
        PARAMETER_TYPE_FILTER.includes(it.type)
    ).map(it => it.name), errors) + countErrors(folders.errors, ingress.errors, networks.errors, peers.errors, privateNetworks.errors);
    const anyError = errorCount > 0;

    useSubmitShortcut(() => submitJob(false), anyError || isLoading || !sshValid || isMissingConnection);

    useEffect(() => {
        const cardIds: Record<string, string> = {
            KeyJ: "job-card-information",
            KeyS: "job-card-storage",
            KeyT: "job-card-script",
            KeyI: "job-card-readme",
            KeyM: "job-card-modules",
            KeyC: "job-card-connectivity",
            KeyP: "job-card-parameters",
        };
        const primaryPressed = (event: KeyboardEvent) => isLikelyMac ? event.metaKey : event.ctrlKey;
        const focusCard = (id: string) => {
            const card = document.getElementById(id);
            if (!card) return;
            if (document.activeElement instanceof HTMLElement) closeOpenDropdown(document.activeElement);
            const navigationTargets = Array.from(card.querySelectorAll<HTMLElement>(FIELD_NAVIGATION_SELECTOR))
                .filter(element => !isDisabledNavigationTarget(element));
            const focusTarget = navigationTargets.find(element => element.hasAttribute("data-card-first-field")) ??
                navigationTargets[0];
            card.scrollIntoView({block: "nearest"});
            focusTarget?.focus();
        };
        const onKeyDown = (event: KeyboardEvent) => {
            if (document.querySelector(".ReactModal__Overlay")) return;
            if (event.defaultPrevented) return;
            if ((event.key === "ArrowUp" || event.key === "ArrowDown") &&
                !event.metaKey && !event.ctrlKey && !event.altKey &&
                !document.activeElement?.closest(FIELD_NAVIGATION_SELECTOR)) {
                const focusTarget = Array.from(document.querySelectorAll<HTMLElement>(FIELD_NAVIGATION_SELECTOR))
                    .find(element => element.offsetParent !== null && !isDisabledNavigationTarget(element));
                if (focusTarget) {
                    event.preventDefault();
                    focusTarget.focus();
                    focusTarget.scrollIntoView({block: "nearest"});
                }
                return;
            }
            if (!event.altKey || !primaryPressed(event)) return;
            setShortcutsVisible(true);
            const cardId = cardIds[event.code];
            if (!cardId) return;
            event.preventDefault();
            focusCard(cardId);
        };
        const onModifierChange = (event: KeyboardEvent) => {
            setShortcutsVisible(event.altKey && primaryPressed(event));
        };
        const hideShortcuts = () => setShortcutsVisible(false);

        document.addEventListener("keydown", onKeyDown);
        document.addEventListener("keyup", onModifierChange);
        window.addEventListener("blur", hideShortcuts);
        return () => {
            document.removeEventListener("keydown", onKeyDown);
            document.removeEventListener("keyup", onModifierChange);
            window.removeEventListener("blur", hideShortcuts);
        };
    }, [anyError, isLoading, isMissingConnection, sshValid, submitJob]);

    if ((!previewMode && applicationResp.loading) || (!previewMode && isInitialMount)) {
        return <MainContainer main={<LoadingIcon size={36} />} />;
    }

    if (application == null) {
        return (
            <MainContainer
                main={<Heading.h3>Unable to find application &apos;{appName}&apos;</Heading.h3>}
            />
        );
    }

    if (application.invocation.tool.tool?.description.backend === "UCX") {
        return <CreateUcxJob application={application} appGroup={applicationResp?.data ?? null} />;
    }

    let workflowParams = parameters.filter(it => it.type === "workflow");
    if (workflowParams.length > 1) workflowParams = [workflowParams[0]];

    let modulesParam = parameters.filter(it => it.type === "modules");
    if (modulesParam.length > 0) modulesParam = [modulesParam[0]];

    let readmeParams = parameters.filter(it => it.type === "readme");

    const applicationDefinesCacheParameter = (application.invocation.parameters ?? []).some(it => it.name === "ucCacheInitScript");
    const cacheInitScriptParameter = applicationDefinesCacheParameter ? undefined : parameters.find(it =>
        it.name === "ucCacheInitScript" && it.type === "boolean"
    );
    const displayedParameters = parameters.filter(it => it !== cacheInitScriptParameter);

    const mandatoryParameters = displayedParameters.filter(it =>
        !it.optional && it.type !== "workflow" && it.type !== "modules" && it.type !== "readme"
    );

    const optionalParameters = displayedParameters.filter(it =>
        it.optional && it.type !== "workflow" && it.type !== "modules" && it.type !== "readme"
    );

    const appGroup = applicationResp?.data;
    const license = getLicense(application);
    const hasParameters = mandatoryParameters.length + optionalParameters.length > 0;
    const sshMode = application.invocation.ssh?.mode ?? "DISABLED";
    const hasConnectivity = sshMode !== "DISABLED" ||
        ingressResourceAllowed(application, bindLinkToPort) || peerResourceAllowed(application) ||
        networkIPResourceAllowed(application);
    const connectivityEnabled = sshEnabled || ingress.params.length > 1 || privateNetworks.params.length > 1 ||
        networks.params.length > 1;
    const sectionShortcuts = [{shortcut: "J", label: "Jump to job information"}];
    if (folderResourceAllowed(application)) sectionShortcuts.push({shortcut: "S", label: "Jump to storage"});
    if (workflowParams.length > 0) sectionShortcuts.push({shortcut: "T", label: "Jump to script"});
    if (readmeParams.length > 0) sectionShortcuts.push({shortcut: "I", label: "Jump to information"});
    if (modulesParam.length > 0) sectionShortcuts.push({shortcut: "M", label: "Jump to modules"});
    if (hasConnectivity) sectionShortcuts.push({shortcut: "C", label: "Jump to connectivity"});
    if (hasParameters) sectionShortcuts.push({shortcut: "P", label: "Jump to parameters"});

    const walletUsage = displayWallet == null ? 0 : Math.max(0,
        displayWallet.usageAndQuota.raw.usage -
        (displayWallet.usageAndQuota.raw.retiredAmountStillCounts ? 0 : displayWallet.usageAndQuota.raw.retiredAmount)
    );
    const costDisplayValues = [
        estimatedCost.product == null || estimatedCost.product.category.freeToUse ? 0 : calculateProductCost(
            estimatedCost.product,
            estimatedCost.numberOfNodes,
            estimatedCost.durationInMinutes,
        ),
        displayWallet == null ? 0 : displayWallet.usageAndQuota.raw.quota - walletUsage,
        displayWallet?.usageAndQuota.display.displayOverallocationWarning ? displayWallet.usageAndQuota.raw.maxUsable : 0,
    ].map(Math.abs).filter(value => value > 0);
    const costDisplayReference = costDisplayValues.length === 0 ? undefined : Math.min(...costDisplayValues);
    const formatWalletBalance = (balance: number) => balanceToStringFromUnit(
        displayWallet!.usageAndQuota.raw.type,
        costUnit,
        balance,
        {precision: 2, referenceBalance: costDisplayReference},
    );


    let submitControl: JSX.Element;
    {
        const buttonControl = <>
            <Button
                type="button"
                color="successMain"
                disabled={previewMode
                    ? anyError || isLoading || !sshValid || isMissingConnection || props.previewRendering === true
                    : anyError}
                onClick={() => void submitJob(false)}
            >
                {previewMode && props.previewRendering ? <UcxSpinner size={16} color="white" margin="0 8px 0 0" /> : (
                    <Icon name={previewMode ? "heroEye" : "heroPlay"} mr={8} />
                )}
                <span>{previewMode ? "Preview script" : "Submit"}</span>
                {previewMode ? null : <SubmitShortcut />}
            </Button>
        </>;

        if (anyError) {
            submitControl = <TooltipV2 tooltip={`${errorCount} parameter ${stupidPluralize(errorCount, "error")} to resolve before submitting.`}>
                {buttonControl}
            </TooltipV2>
        } else {
            submitControl = buttonControl;
        }
    }

    const jobForm = (
            <>
                <div className={JobCreateHeaderClass}>
                    <AppHeader
                        title={appGroup?.specification?.title ?? application.metadata.title}
                        application={application}
                        flavors={flavors}
                        allVersions={application.versions ?? []}
                        showSelectors={false}
                        responsiveDescription
                        description={<div className={MarkdownWrapper}>
                            <Markdown unwrapDisallowed disallowedElements={["image", "heading"]}>
                                {application.metadata.description}
                            </Markdown>
                        </div>}
                    />
                    <Box className="job-create-header-spacer" flexGrow={1} />

                    <div className={JobCreateHeaderActionsClass}>
                        <UtilityBar responsive leading={<>
                            {!previewMode ? <ApplicationForkAction application={application} /> : null}
                            {!canEditCustomVersion || (!Client.userIsAdmin && !hasFeature(Feature.CONTAINER_REPOSITORIES)) ? null : (
                                <Button height="25px" onClick={() => {
                                    const provider = application.invocation.tool.tool?.description.supportedProviders?.[0];
                                    if (!provider) return;
                                    navigate(AppRoutes.apps.creator({
                                        operation: "newVersion",
                                        applicationKind: "custom",
                                        workspace: projectId ?? "personal",
                                        name: application.metadata.name.replace(/^custom-/, ""),
                                        version: application.metadata.version,
                                        provider,
                                        returnTo: location.pathname + location.search,
                                    }));
                                }}>
                                    Create new version
                                </Button>
                            )}
                            {!application.metadata.website ? null : (
                                <ExternalLink className="job-create-documentation" href={application.metadata.website}>
                                    <Button>
                                        <Icon name="heroArrowTopRightOnSquare" color="primaryContrast" />
                                        <div>Documentation</div>
                                    </Button>
                                </ExternalLink>
                            )}
                            {license ? <TooltipV2 tooltip={`License: ${license}`}><Icon size="24" name="fileSignatureSolid" /></TooltipV2> : null}
                        </>} />
                    </div>
                </div>
                <div className={JobCreateContentClass}>
                    <div className={JobCreateLayoutClass}>
                    <KeyboardNavigation className={JobCreateMainClass} horizontalSelector="[data-job-info-field]">
                    <Grid gridTemplateColumns="1fr" gap="24px" width="100%" mb="24px">
                        {insufficientFunds ? <WalletWarning errorCode={insufficientFunds.errorCode} /> : null}
                        {!isMissingConnection ? null : <Box mt={32}>
                            <Link to="/providers/connect">
                                <Icon name="warning" color="warningMain" mx={8} />
                                Connection required!
                            </Link>
                        </Box>}
                        <Card id="job-card-information">
                            <JobCardHeading shortcut="J" shortcutsVisible={shortcutsVisible} action={
                                !canImportParameters ? null :
                                <ImportParameters application={application} dynamicParameters={injectedParameters.data}
                                    onImport={onLoadParameters}
                                    automaticImport={appParams.current}
                                    importDialogOpen={importDialogOpen}
                                    setImportDialogOpen={setImportDialogOpen}
                                    onMessagesChange={setImportMessages}
                                    onImportDialogClose={closeImportDialog} />
                            }>
                                Job information
                            </JobCardHeading>
                            <div>
                                {!canImportParameters ? null : <ImportMessages messages={importMessages} onClear={() => setImportMessages([])} />}
                                {previewMode ? null : (
                                    <Box mt="16px" mb="20px">
                                        <ApplicationSelector
                                            application={application}
                                            flavors={flavors}
                                            allVersions={application.versions ?? []}
                                            showLabels
                                            jobCreateLayout
                                            fieldNavigation
                                            autoFocusFlavor
                                            reloadFlavors={reloadFlavors}
                                            onApplicationChange={saveParameters}
                                        />
                                    </Box>
                                )}
                                <ReservationParameter
                                    application={application}
                                    errors={reservationErrors}
                                    onJobNameChange={setJobName}
                                    fieldNavigation
                                    onEstimatedCostChange={(durationInMinutes, numberOfNodes, wallet, product) =>
                                        setEstimatedCost({durationInMinutes, wallet, numberOfNodes, product})}
                                    onAvailableMachinesChange={props.onPreviewMachinesChange}
                                />
                            </div>
                        </Card>

                        <div data-last-used-file-path="" hidden />
                        <FolderResource
                            {...folders}
                            application={application}
                            cardId="job-card-storage"
                            heading={<JobCardHeading shortcut="S" shortcutsVisible={shortcutsVisible}>Storage</JobCardHeading>}
                        />

                        {/*Workflow*/}
                        {workflowParams.length === 0 ? null : (
                            <Card id="job-card-script">
                                <JobCardHeading shortcut="T" shortcutsVisible={shortcutsVisible}>Script</JobCardHeading>
                                <Grid gridTemplateColumns={"1fr"} gap={"16px"}>
                                    {workflowParams.map(param => (
                                        <Widget key={param.name} parameter={param} errors={errors} provider={provider}
                                            injectWorkflowParameters={setWorkflowInjectParameters}
                                            setErrors={setErrors} active application={application} bindLinkToPort={bindLinkToPort} />
                                    ))}
                                </Grid>
                            </Card>
                        )}

                        {/*Readme*/}
                        {readmeParams.length === 0 ? null : (
                            <Card id="job-card-readme" backgroundColor="var(--warningMain)" color="warningContrast">
                                <JobCardHeading shortcut="I" shortcutsVisible={shortcutsVisible}>
                                    {estimatedCost.product == null ?
                                        "Information" :
                                        `Information from ${getProviderTitle(estimatedCost.product.category.provider)}`
                                    }
                                </JobCardHeading>
                                <Grid gridTemplateColumns={"1fr"} gap={"16px"}>
                                    {readmeParams.map(param => (
                                        <Widget key={param.name} parameter={param} errors={errors} provider={provider}
                                            injectWorkflowParameters={setWorkflowInjectParameters}
                                            setErrors={setErrors} active application={application} bindLinkToPort={bindLinkToPort} />
                                    ))}
                                </Grid>
                            </Card>
                        )}

                        {/*Modules*/}
                        {modulesParam.length === 0 ? null : (
                            <Card id="job-card-modules">
                                <JobCardHeading shortcut="M" shortcutsVisible={shortcutsVisible}>Modules</JobCardHeading>
                                <Grid gridTemplateColumns={"1fr"} gap={"16px"}>
                                    {modulesParam.map(param => (
                                        <Widget key={param.name} parameter={param} errors={errors} provider={provider}
                                            injectWorkflowParameters={setWorkflowInjectParameters}
                                            setErrors={setErrors} active application={application} bindLinkToPort={bindLinkToPort} />
                                    ))}
                                </Grid>
                            </Card>
                        )}

                        {!hasConnectivity ? null : <Card id="job-card-connectivity">
                            <JobCardHeading shortcut="C" shortcutsVisible={shortcutsVisible}>Connectivity</JobCardHeading>
                            {!connectivityEnabled ? null : <Box mt="16px">
                                <Warning>
                                    Options in this section can make your job publicly accessible. Secure your job accordingly.
                                </Warning>
                            </Box>}
                            <Box mt="16px">
                                <FieldGroup>
                                    {sshMode === "DISABLED" ? null : <>
                                        <SshWidget embedded fieldRow application={application} onSshStatusChanged={setSshEnabled}
                                            onSshKeysValid={setSshValid} initialEnabledStatus={initialSshEnabled} />
                                    </>}
                                    {!ingressResourceAllowed(application, bindLinkToPort) ? null : <>
                                        <CompactResourceRowsContent singularLabel="Public link" {...ingress}
                                            firstRowDescription="Public links make your job accessible through a web browser. Anyone with the link can access your application."
                                            application={application} bindLinkToPort={bindLinkToPort} />
                                    </>}
                                    {!peerResourceAllowed(application) ? null : <>
                                        {privateNetworks.params.length <= 1 ? null : (
                                            <FieldRow
                                                title="Hostname"
                                                description="Your job will be identified by this name within the network."
                                                control={<Input value={dnsHostname} onChange={onDnsHostnameChange} />}
                                                bold={dnsHostname.length > 0}
                                            />
                                        )}
                                        <CompactResourceRowsContent singularLabel="Private network" {...privateNetworks}
                                            firstRowDescription="Connect this job to a network of other jobs."
                                            application={application} />
                                    </>}
                                    {!networkIPResourceAllowed(application) ? null : <>
                                        <CompactResourceRowsContent singularLabel="Public IP" {...networks}
                                            firstRowDescription="Make your job reachable from the Internet."
                                            application={application} />
                                    </>}
                                </FieldGroup>
                            </Box>
                        </Card>}

                        {/* Parameters */}
                        {!hasParameters ? null : (
                            <Card id="job-card-parameters">
                                <JobCardHeading shortcut="P" shortcutsVisible={shortcutsVisible}>Parameters</JobCardHeading>
                                <Box mt="16px">
                                    <FieldGroup>
                                        {[...mandatoryParameters, ...optionalParameters].map(param => (
                                            <Widget
                                                key={param.name}
                                                fieldGroup
                                                selected={!param.optional || activeOptParams.includes(param.name)}
                                                parameter={param}
                                                errors={errors}
                                                provider={provider}
                                                injectWorkflowParameters={setWorkflowInjectParameters}
                                                setErrors={setErrors}
                                                application={application}
                                                bindLinkToPort={bindLinkToPort}
                                                initScriptCache={param.name !== "initScript" || !cacheInitScriptParameter ? undefined : {
                                                    parameter: cacheInitScriptParameter,
                                                    enabled: cacheInitScript,
                                                    onChange: enabled => {
                                                        setCacheInitScript(enabled);
                                                        setActiveOptParams(current => enabled ?
                                                            (current.includes(cacheInitScriptParameter.name) ? current : [...current, cacheInitScriptParameter.name]) :
                                                            current.filter(name => name !== cacheInitScriptParameter.name)
                                                        );
                                                    }
                                                }}
                                                onValueChange={() => setActiveOptParams(current =>
                                                    current.includes(param.name) ? current : [...current, param.name]
                                                )}
                                                onSelectedChange={selected => setActiveOptParams(current => selected ?
                                                    (current.includes(param.name) ? current : [...current, param.name]) :
                                                    current.filter(name => name !== param.name)
                                                )}
                                                onClear={!param.optional ? undefined : () => {
                                                    clearWidgetValue(param);
                                                    setActiveOptParams(current => current.filter(name => name !== param.name));
                                                    if (param.name === "initScript" && cacheInitScriptParameter) {
                                                        setCacheInitScript(false);
                                                        setActiveOptParams(current => current.filter(name => name !== cacheInitScriptParameter.name));
                                                    }
                                                    setErrors(current => {
                                                        if (!current[param.name]) return current;
                                                        const next = {...current};
                                                        delete next[param.name];
                                                        return next;
                                                    });
                                                }}
                                            />
                                        ))}
                                    </FieldGroup>
                                </Box>
                            </Card>
                        )}

                    </Grid>
                    </KeyboardNavigation>
                    <aside className={JobSubmissionSidebarClass}>
                        <Card>
                            {previewMode ? null : (
                                <div className={JobSubmissionSecondaryClass}>
                                    <Label>
                                        E-mail notification settings
                                        <Select width="100%" value={jobEmailNotifications} onChange={onChangeJobEmailNotification} name="job-email-notifications">
                                            <option value="never">Do not notify me</option>
                                            <option value="start_or_ends">Notify me when a job starts or stops</option>
                                        </Select>
                                    </Label>
                                </div>
                            )}
                             <div className={JobSubmissionSummaryClass} style={previewMode ? {borderTop: "none", marginTop: 0, paddingTop: 0} : undefined}>
                                <div className={EstimatesContainerClass}>
                                    <table>
                                        {!costUnit ? null : <thead>
                                            <tr>
                                                <th />
                                                <th className="cost-unit">{costUnit}</th>
                                            </tr>
                                        </thead>}
                                        <tbody>
                                            <tr>
                                                <th>Est. cost</th>
                                                <td>
                                                    {!estimatedCost.product ? "-" : removeDisplayedUnit(priceToString(
                                                        estimatedCost.product,
                                                        estimatedCost.numberOfNodes,
                                                        estimatedCost.durationInMinutes,
                                                        {showSuffix: false, display: {precision: 2, referenceBalance: costDisplayReference}}
                                                    ), explainUnit(estimatedCost.product.category).name)}
                                                </td>
                                            </tr>
                                            <tr className="desktop-cost-row">
                                                <th>Balance</th>
                                                <td>{displayWallet === null ? "-" : removeDisplayedUnit(
                                                    formatWalletBalance(displayWallet.usageAndQuota.raw.quota - walletUsage),
                                                    costUnit
                                                )}</td>
                                            </tr>
                                            {displayWallet === null || !displayWallet.usageAndQuota.display.displayOverallocationWarning ? null :
                                                <tr className="desktop-cost-row">
                                                    <th>Usable balance</th>
                                                    <td>
                                                        <OverallocationLink>
                                                            <TooltipV2 tooltip={UNABLE_TO_USE_FULL_ALLOC_MESSAGE}>
                                                                <Icon name="heroExclamationTriangle" color="warningMain" />
                                                                {removeDisplayedUnit(
                                                                    formatWalletBalance(displayWallet.usageAndQuota.raw.maxUsable),
                                                                    costUnit
                                                                )}
                                                            </TooltipV2>
                                                        </OverallocationLink>
                                                    </td>
                                                </tr>
                                            }
                                        </tbody>
                                    </table>
                                </div>
                                <div className={JobSubmitButtonClass}>
                                    {submitControl}
                                </div>
                            </div>
                        </Card>
                        <div className={KeyboardNavigationGuideClass}>
                            <div className="keyboard-navigation-row">
                                <Flex gap={"4px"}>
                                    <span className={ShortcutClass}>↑</span>
                                    <span className={ShortcutClass}>↓</span>
                                    <span className={ShortcutClass}>←</span>
                                    <span className={ShortcutClass}>→</span>
                                </Flex>
                                <span className="keyboard-navigation-action">Move between fields</span>
                            </div>
                            {sectionShortcuts.map(({shortcut, label}) => (
                                <div key={shortcut} className="keyboard-navigation-row">
                                    <span className={ShortcutClass}>{createKeyboardShortcut(shortcut, ["ctrl", "alt"])}</span>
                                    <span className="keyboard-navigation-action">{label}</span>
                                </div>
                            ))}
                        </div>
                    </aside>
                    </div>
                </div>
            </>);

    return <MainContainer main={jobForm} />;
}

function getParameterName(param: Pick<ApplicationParameter, "type" | "name">): string {
    switch (param.type) {
        case "peer": {
            return param.name + "job";
        }
        default:
            return param.name;
    }
}

function findProviderMismatches(
    provider: string,
    ...parameterResources: Pick<ResourceHook, "params" | "errors" | "setErrors">[]
): void {
    for (const group of parameterResources) {
        let anyErrors = false;
        for (const param of group.params) {
            const el = findElement({name: getParameterName(param)});
            if (el) {
                const elementProvider = el.getAttribute("data-provider");
                if (elementProvider != null && provider !== elementProvider) {
                    group.errors[param.name] = `This ${prettierType(param.type)} from ${getProviderTitle(elementProvider)} is not possible to use with the machine from ${getProviderTitle(provider)}.`;
                    anyErrors = true;
                }
            }
        }
        if (anyErrors) {
            group.setErrors({...group.errors});
        } else {
            group.setErrors({});
        }
    }
}

function prettierType(type: string): string {
    switch (type) {
        case "peer":
            return "job";
        case "network_ip":
            return "public IP";
        case "ingress":
            return "link"
        case "license_server":
            return "license";
        case "input_file":
            return "file";
        case "input_directory":
            return "folder";
        default:
            return prettierString(type).toLocaleLowerCase();
    }
}

function toDnsSafeHostname(value: string): string {
    const sanitized = value
        .toLowerCase()
        .replace(/[^a-z0-9-]/g, "-")
        .replace(/-+/g, "-")
        .replace(/^-+|-+$/g, "")
        .slice(0, 63)
        .replace(/^-+|-+$/g, "");

    return sanitized === "" ? "job" : sanitized;
}

async function createInferenceApiServerResources(
    application: Application,
    invokeCommand: InvokeCommand,
): Promise<compute.AppParameterValueNS.ApiServer[]> {
    const mode = application.invocation.inference?.mode ?? "NONE";
    if (mode === "NONE") return [];

    let options: ApiTokens.ApiTokenRetrieveOptionsResponse | null = null;
    try {
        options = await invokeCommand<ApiTokens.ApiTokenRetrieveOptionsResponse>(
            ApiTokens.retrieveOptions(),
            {defaultErrorHandler: mode === "MANDATORY"}
        );
    } catch (err) {
        if (mode === "MANDATORY") throw err;
        return [];
    }

    const inferenceProviders = Object.entries(options?.byProvider ?? {})
        .filter(([, providerOptions]) => providerOptions.availablePermissions.some(permission => permission.name === "inference"))
        .map(([providerId]) => providerId);

    if (inferenceProviders.length === 0) {
        if (mode === "MANDATORY") {
            throw new Error("This application requires inference servers, but none are available for this project.");
        }
        return [];
    }

    const resources: compute.AppParameterValueNS.ApiServer[] = [];
    for (const providerId of inferenceProviders) {
        try {
            const token = await invokeCommand<ApiTokens.ApiToken>(ApiTokens.create({
                title: `.job-token.inference.${randomTokenId()}`,
                description: "Generated for a job requiring inference servers.",
                requestedPermissions: [{name: "inference", action: "use"}],
                expiresAt: Date.now() + 7 * 24 * 60 * 60 * 1000,
                provider: providerId,
                product: {
                    category: "",
                    id: "",
                    provider: ""
                },
            }), {defaultErrorHandler: mode === "MANDATORY"});

            if (token?.status.server && token.status.token) {
                resources.push({
                    type: "api_server",
                    tokenType: "Inference",
                    server: token.status.server,
                    token: token.status.token,
                });
            }
        } catch (err) {
            if (mode === "MANDATORY") throw err;
        }
    }

    if (mode === "MANDATORY" && resources.length === 0) {
        throw new Error("This application requires inference servers, but no inference API tokens could be created.");
    }

    return resources;
}

function randomTokenId(): string {
    return globalThis.crypto?.randomUUID?.() ?? Math.random().toString(36).slice(2);
}

export function getProviderField(): string | undefined {
    try {
        const validatedMachineReservation = validateMachineReservation();
        return validatedMachineReservation?.provider;
    } catch (e) {
        return undefined;
    }
}

export function checkProviderMismatch(resource: Resource, resourceType: string): string | false {
    const provider = getProviderField();
    const resourceProvider = resource.specification.product.provider;
    if (provider && provider !== resourceProvider) {
        return providerMismatchError(resourceProvider, resourceType);
    }
    return false;
}

export function providerMismatchError(resourceProvider: string, resourceType: string): string {
    const selectedProvider = getProviderField() ?? "";
    return providerError(resourceType, resourceProvider, selectedProvider);
}

function providerError(resourceType: string, resourceProvider: string, selectedProvider: string) {
    return `${resourceType} from ${getProviderTitle(resourceProvider)} cannot be used with machines from ${getProviderTitle(selectedProvider)}`;
}

function countErrors(...objects: Record<string, string>[]): number {
    return objects.reduce((acc, cur) => acc + Object.values(cur).length, 0);
}

function countMandatoryAndOptionalErrors(params: string[], errors: Record<string, string>): number {
    let count = 0;
    for (const param of params) {
        if (errors[param]) count++;
    }
    return count;
}

const MarkdownWrapper = injectStyle("md-wrapper", k => `
    ${k} p:first-child {
        margin-top: 0;
    }

    ${k} p:last-child {
        margin-bottom: 0;
    }
`);

export default Create;
