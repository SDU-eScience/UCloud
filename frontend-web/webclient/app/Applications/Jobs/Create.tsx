import * as React from "react";
import {useCallback, useEffect, useMemo, useState} from "react";
import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {useLocation, useNavigate} from "react-router-dom";
import {MainContainer} from "@/ui-components/MainContainer";
import {AppHeader} from "@/Applications/View";
import {
    Box,
    Button,
    Card,
    ContainerForText,
    ExternalLink,
    Flex,
    Grid,
    Icon,
    Label,
    Link,
    Markdown, Select,
    Tooltip
} from "@/ui-components";
import {findElement, OptionalWidgetSearch, setWidgetValues, validateWidgets, Widget} from "@/Applications/Jobs/Widgets";
import * as Heading from "@/ui-components/Heading";
import {FolderResource, folderResourceAllowed} from "@/Applications/Jobs/Resources/Folders";
import {IngressResource, ingressResourceAllowed} from "@/Applications/Jobs/Resources/Ingress";
import {PeerResource, peerResourceAllowed} from "@/Applications/Jobs/Resources/Peers";
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
    displayErrorMessageOrDefault,
    doNothing,
    extractErrorCode,
    prettierString,
    useDidMount
} from "@/UtilityFunctions";
import {addStandardDialog, OverallocationLink, WalletWarning} from "@/UtilityComponents";
import {ImportParameters} from "@/Applications/Jobs/Widgets/ImportParameters";
import LoadingIcon from "@/LoadingIcon/LoadingIcon";
import {usePage} from "@/Navigation/Redux";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {NetworkIPResource, networkIPResourceAllowed} from "@/Applications/Jobs/Resources/NetworkIPs";
import {bulkRequestOf} from "@/UtilityFunctions";
import {getQueryParam} from "@/Utilities/URIUtilities";
import {default as JobsApi, DynamicParameters, JobSpecification} from "@/UCloud/JobsApi";
import {BulkResponse, FindByStringId} from "@/UCloud";
import {
    ProductV2,
    UNABLE_TO_USE_FULL_ALLOC_MESSAGE,
    priceToString,
    WalletV2,
    explainWallet
} from "@/Accounting";
import {SshWidget} from "@/Applications/Jobs/Widgets/Ssh";
import {connectionState} from "@/Providers/ConnectionState";
import {Feature, hasFeature} from "@/Features";
import {useUState} from "@/Utilities/UState";
import {Spacer} from "@/ui-components/Spacer";
import {injectStyle} from "@/Unstyled";
import {UtilityBar} from "@/Navigation/UtilityBar";
import {validateMachineReservation} from "@/Applications/Jobs/Widgets/Machines";
import {Resource} from "@/UCloud/ResourceApi";
import {getProviderTitle} from "@/Providers/ProviderTitle";
import * as AppStore from "@/Applications/AppStoreApi";
import {
    Application,
    ApplicationGroup,
    ApplicationParameter,
} from "@/Applications/AppStoreApi";
import {TooltipV2} from "@/ui-components/Tooltip";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {UserDetailsState, defaultEmailSettings} from "@/UserSettings/ChangeEmailSettings";
import {mail} from "@/UCloud";
import retrieveEmailSettings = mail.retrieveEmailSettings;
import toggleEmailSettings = mail.toggleEmailSettings;
import {useDiscovery} from "@/Applications/Hooks";

interface InsufficientFunds {
    why?: string;
    errorCode?: string;
}

const EstimatesContainerClass = injectStyle("estimates-container", k => `
    ${k} {
        margin-top: 20px;
    }
    
    ${k} table {
        width: 100%;
    }
    
    ${k} th {
        text-align: left;
        padding-right: 10px;
    }
    
    ${k} td {
        font-family: var(--monospace);
        text-align: right;
    }
`);

const PARAMETER_TYPE_FILTER = ["input_directory", "input_file", "ingress", "peer", "license_server", "network_ip"];

const initialState: UserDetailsState = {
    settings: defaultEmailSettings
};

function getLicense(app: Application): string | undefined {
    return app.invocation.tool.tool?.description.license
}

export const Create: React.FunctionComponent = () => {
    const [emailNotifications, setEmailNotifications] = React.useState<UserDetailsState>(initialState);
    const [jobEmailNotifications, setJobEmailNotifications] = useState<"never" | "start" | "ends" | "start_or_ends">("never");
    const navigate = useNavigate();
    const location = useLocation();
    const appName = getQueryParam(location.search, "app");
    const appVersion = getQueryParam(location.search, "version");

    if (!appName) {
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
    const [injectedParameters, fetchInjectedParameters] = useCloudAPI<DynamicParameters | null>(
        {noop: true},
        null
    );
    const [workflowInjectedParameters, setWorkflowInjectParameters] = useState<ApplicationParameter[]>([]);

    const application = applicationResp?.data?.status?.applications?.find(it => it.metadata.name === appName);

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
    const displayWallet = useMemo(() => {
        const wallet = estimatedCost.wallet;
        if (wallet === null) return null;
        return explainWallet(wallet);
    }, [estimatedCost.wallet]);
    const [discovery] = useDiscovery();

    const provider = getProviderField();

    const networks = useResource("network", provider,
        (name) => ({type: "network_ip", description: "", title: "", optional: true, name}));
    const ingress = useResource("ingress", provider,
        (name) => ({type: "ingress", description: "", title: "", optional: true, name}));
    const folders = useResource("resourceFolder", provider,
        (name) => ({type: "input_directory", description: "", title: "", optional: true, name}));
    const peers = useResource("resourcePeer", provider,
        (name) => ({type: "peer", description: "", title: "", optional: true, name}));

    const [activeOptParams, setActiveOptParams] = useState<string[]>([]);
    const [reservationErrors, setReservationErrors] = useState<ReservationErrors>({});

    const [importDialogOpen, setImportDialogOpen] = useState(false);

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
        retrieveEmailNotificationSettings();
    }, []);

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
            snackbarStore.addFailure("Failed to update user email settings", false);
        } else {
            snackbarStore.addSuccess("User email settings updated", false);
        }

    }, [emailNotifications]);

    useUState(connectionState);

    /* Note(Jonas): While this is a pretty weird data-type, I don't think we every need to store more than one,
        which is why I'm not using a Record<string, T>  */
    const appParams = React.useRef<[groupId: number, Partial<JobSpecification>]>([-1, {}]);
    // NOTE(Jonas): Not entirely sure a ref is strictly needed, but should be more consistent.
    const sshEnabledRef = React.useRef(false);
    sshEnabledRef.current = sshEnabled;
    useEffect(() => {
        if (appName === "syncthing" && !localStorage.getItem("syncthingRedirect")) {
            navigate("/drives");
        }

        if (application?.metadata.groupId) {
            const reservationOptions = getReservationValues();

            const {values} = validateWidgets(parameters);
            const foldersResources = validateWidgets(folders.params);
            // TODO(Jonas): This should preferably not validate, but just get the values (e.g. one field could be missing)
            const peersResources = validateWidgets(peers.params);
            const networkResources = validateWidgets(networks.params);
            const ingressResources = validateWidgets(ingress.params);
            for (const err of [
                ...Object.values(foldersResources.errors).map(it => "Folders: " + it),
                ...Object.values(peersResources.errors).map(it => "Connected jobs: " + it),
                ...Object.values(networkResources.errors).map(it => "IPs: " + it),
                ...Object.values(ingressResources.errors).map(it => "Public link: " + it)
            ]) {
                snackbarStore.addFailure(err, false);
            }

            appParams.current = [application.metadata.groupId, {
                ...reservationOptions,
                parameters: values,
                resources: Object.values(foldersResources.values)
                    .concat(Object.values(peersResources.values))
                    .concat(Object.values(ingressResources.values))
                    .concat(Object.values(networkResources.values)),
                sshEnabled: sshEnabledRef.current
            }];
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
    }, [appName, appVersion, discovery]);

    useEffect(() => {
        if (!application) return;
        fetchInjectedParameters(JobsApi.requestDynamicParameters({
            application: {name: application.metadata.name, version: application.metadata.version}
        })).then(() => setTimeout(() => {
            try {
                const groupId = application.metadata.groupId;
                const [storedGroupId, jobSpec] = appParams.current;
                if (storedGroupId === groupId) {
                    onLoadParameters(jobSpec);
                }
            } catch (e) {
                console.warn(e);
            }
        }, 0));
    }, [application]);

    const parameters = useMemo(() => {
        let injected: ApplicationParameter[] = [];
        const injectedData = injectedParameters.data;
        if (injectedData && estimatedCost.product) {
            const provider = estimatedCost.product.category.provider;
            injected = injectedData.parametersByProvider[provider] ?? [];
        }
        const fromApp = application?.invocation?.parameters ?? [];
        return [...injected, ...fromApp, ...workflowInjectedParameters];
    }, [application, injectedParameters, workflowInjectedParameters, estimatedCost]);


    React.useEffect(() => {
        if (application && provider) {
            const params = parameters.filter(it =>
                PARAMETER_TYPE_FILTER.includes(it.type)
            );

            findProviderMismatches(
                provider, {errors, params, setErrors}, networks, folders, peers, ingress
            );
        }
    }, [provider, application]);

    const doLoadParameters = useCallback(async (importedJob: Partial<JobSpecification>, initialImport?: boolean) => {
        if (application == null) return;
        const values = importedJob.parameters ?? {};
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
            const newSpace = createSpaceForLoadedResources(folders, resources, "file");
            setTimeout(() => injectResources(newSpace, resources, "file"), 0);
        }
        if (peerResourceAllowed(application)) {
            const newSpace = createSpaceForLoadedResources(peers, resources, "peer");
            setTimeout(() => injectResources(newSpace, resources, "peer"), 0);
        }
        if (ingressResourceAllowed(application)) {
            const newSpace = createSpaceForLoadedResources(ingress, resources, "ingress");
            setTimeout(() => injectResources(newSpace, resources, "ingress"), 0);
        }
        if (networkIPResourceAllowed(application)) {
            const newSpace = createSpaceForLoadedResources(networks, resources, "network");
            setTimeout(() => injectResources(newSpace, resources, "network"), 0);
        }

        folders.setErrors({});
        ingress.setErrors({});
        networks.setErrors({});
        peers.setErrors({});
        setErrors({});
        setReservationErrors({});
    }, [application, activeOptParams, folders, peers, networks, ingress, parameters]);

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
                appParams.current = [-1, {}];
            }
        }
    }, [onLoadParameters, reloadHack]);

    const submitJob = useCallback(async (allowDuplicateJob: boolean) => {
        if (!application) return;

        const {errors, values} = validateWidgets(parameters!);
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

        if (Object.keys(errors).length === 0 &&
            reservationValidation.options !== undefined &&
            Object.keys(foldersValidation.errors).length === 0 &&
            Object.keys(peersValidation.errors).length === 0
        ) {
            const request: JobSpecification = {
                ...reservationValidation.options,
                application: application?.metadata,
                parameters: values,
                resources: Object.values(foldersValidation.values)
                    .concat(Object.values(peersValidation.values))
                    .concat(Object.values(ingressValidation.values))
                    .concat(Object.values(networkValidation.values)),
                sshEnabled,
                allowDuplicateJob
            };

            try {
                const response = await invokeCommand<BulkResponse<FindByStringId | null>>(
                    JobsApi.create(bulkRequestOf(request)),
                    {defaultErrorHandler: false}
                );

                const ids = response?.responses;
                if (!ids || ids.length === 0) {
                    snackbarStore.addFailure("UCloud failed to submit the job", false);
                    return;
                }

                navigate(`/jobs/properties/${ids[0]?.id}?app=${application.metadata.name}`);
            } catch (e) {
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
    }, [application, folders, peers, ingress, networks, navigate]);

    if (applicationResp.loading || isInitialMount) return <MainContainer main={<LoadingIcon size={36} />} />;

    if (application == null) {
        return (
            <MainContainer
                main={<Heading.h3>Unable to find application &apos;{appName}&apos;</Heading.h3>}
            />
        );
    }

    let mandatoryWorkflow = parameters.filter(it => !it.optional && it.type === "workflow");
    if (mandatoryWorkflow.length > 1) mandatoryWorkflow = [mandatoryWorkflow[0]];

    let modulesParam = parameters.filter(it => it.type === "modules");
    if (modulesParam.length > 0) modulesParam = [modulesParam[0]];

    let readmeParams = parameters.filter(it => it.type === "readme");

    const mandatoryParameters = parameters.filter(it =>
        !it.optional && it.type !== "workflow" && it.type !== "modules" && it.type !== "readme"
    );

    const activeParameters = parameters.filter(it =>
        it.optional && activeOptParams.indexOf(it.name) !== -1 && it.type !== "readme"
    )

    const inactiveParameters = parameters.filter(it =>
        !(!it.optional || activeOptParams.indexOf(it.name) !== -1) && it.type !== "readme"
    );

    const isMissingConnection = hasFeature(Feature.PROVIDER_CONNECTION) && estimatedCost.product != null &&
        connectionState.canConnectToProvider(estimatedCost.product.category.provider);

    const errorCount = countMandatoryAndOptionalErrors(parameters.filter(it =>
        PARAMETER_TYPE_FILTER.includes(it.type)
    ).map(it => it.name), errors) + countErrors(folders.errors, ingress.errors, networks.errors, peers.errors);
    const anyError = errorCount > 0;

    const appGroup = applicationResp?.data;
    const license = getLicense(application);

    return <MainContainer
        main={
            <>
                <Flex mx="50px" mt="32px">
                    <AppHeader
                        title={appGroup?.specification?.title ?? application.metadata.title}
                        application={application}
                        flavors={appGroup?.status?.applications ?? []}
                        allVersions={application.versions ?? []}
                    />
                    <Box flexGrow={1} />

                    <Flex height={"35px"} alignItems={"center"} gap={"18px"}>
                        {!application.metadata.website ? null : (
                            <ExternalLink href={application.metadata.website}>
                                <Button>
                                    <Icon name="heroArrowTopRightOnSquare" color="primaryContrast" />
                                    <div>Documentation</div>
                                </Button>
                            </ExternalLink>
                        )}
                        {license ? <TooltipV2 tooltip={`License: ${license}`}><Icon size="24" name="fileSignatureSolid" /></TooltipV2> : null}
                        <UtilityBar />
                    </Flex>
                </Flex>
                <ContainerForText>
                    <Grid gridTemplateColumns={"1fr"} gap={"24px"} width={"100%"} mb={"24px"} mt={"24px"}>
                        {insufficientFunds ? <WalletWarning errorCode={insufficientFunds.errorCode} /> : null}
                        {isMissingConnection ?
                            <Box mt={32}>
                                <Link to={"/providers/connect"}>
                                    <Icon name="warning" color="warningMain" mx={8} />
                                    Connection required!
                                </Link>
                            </Box> :
                            <Spacer
                                left={<Flex maxWidth="800px" flexDirection={"column"}>
                                    <div className={MarkdownWrapper}>
                                        <Markdown
                                            unwrapDisallowed
                                            disallowedElements={[
                                                "image",
                                                "heading"
                                            ]}
                                        >
                                            {application.metadata.description}
                                        </Markdown>
                                    </div>

                                    <Box flexGrow={1} />

                                    <Label mt={"16px"}>
                                        E-mail notification settings
                                        <Select width={"300px"} onChange={onChangeJobEmailNotification} name={"job-email-notifications"}>
                                            <option value="never" selected={jobEmailNotifications === "never"}>Do not notify me</option>
                                            <option value="start_or_ends" selected={jobEmailNotifications === "start_or_ends"}>Notify me when a job starts or stops</option>
                                        </Select>
                                    </Label>
                                    <Label mt={"16px"}>
                                        Job report sample rate settings
                                        <Select width={"300px"} onChange={doNothing} name={"job-resource-sample-rate"}>
                                            {/*<option value="fie" selected={}>Fie dog</option>*/}
                                        </Select>
                                    </Label>
                                </Flex>}
                                right={
                                    <div>
                                        <Flex>
                                            <ImportParameters application={application} onImport={onLoadParameters}
                                                importDialogOpen={importDialogOpen}
                                                setImportDialogOpen={setImportDialogOpen}
                                                onImportDialogClose={() => setImportDialogOpen(false)} />

                                            {anyError ?
                                                <Tooltip trigger={
                                                    <Button ml={"10px"} type="button" color={"successMain"} disabled>
                                                        <Icon name="heroPlay" mr={8} />
                                                        Submit
                                                    </Button>
                                                }>
                                                    {errorCount} parameter error{errorCount > 1 ? "s" : ""} to resolve
                                                    before submitting.
                                                </Tooltip>
                                                :
                                                <Button
                                                    color={"successMain"}
                                                    type={"button"}
                                                    ml={"10px"}
                                                    disabled={isLoading || !sshValid || isMissingConnection}
                                                    onClick={() => submitJob(false)}
                                                >
                                                    <Icon name="heroPlay" mr={8} />
                                                    Submit
                                                </Button>
                                            }
                                        </Flex>

                                        <div className={EstimatesContainerClass}>
                                            <table>
                                                <tbody>
                                                    <tr>
                                                        <th>Estimated cost</th>
                                                        <td>
                                                            {!estimatedCost.product ?
                                                                "-" :
                                                                priceToString(
                                                                    estimatedCost.product,
                                                                    estimatedCost.numberOfNodes,
                                                                    estimatedCost.durationInMinutes,
                                                                    {showSuffix: false}
                                                                )
                                                            }
                                                        </td>
                                                    </tr>
                                                    <tr>
                                                        <th>Current balance</th>
                                                        <td>
                                                            {displayWallet === null ?
                                                                "-" :
                                                                displayWallet.usageAndQuota.display.currentBalance
                                                            }
                                                        </td>
                                                    </tr>
                                                    {displayWallet === null || !displayWallet.usageAndQuota.display.displayOverallocationWarning ? null :
                                                        <tr>
                                                            <th>Usable balance</th>
                                                            <td>
                                                                <OverallocationLink>
                                                                    <TooltipV2 tooltip={UNABLE_TO_USE_FULL_ALLOC_MESSAGE}>
                                                                        <Icon name={"heroExclamationTriangle"} color={"warningMain"} />
                                                                        {displayWallet.usageAndQuota.display.maxUsableBalance}
                                                                    </TooltipV2>
                                                                </OverallocationLink>
                                                            </td>
                                                        </tr>
                                                    }
                                                </tbody>
                                            </table>
                                        </div>
                                    </div>
                                }
                            />
                        }
                        <Card pt="25px">
                            <ReservationParameter
                                application={application}
                                errors={reservationErrors}
                                onEstimatedCostChange={(durationInMinutes, numberOfNodes, wallet, product) =>
                                    setEstimatedCost({durationInMinutes, wallet, numberOfNodes, product})}
                            />
                        </Card>

                        <div data-last-used-file-path="" hidden />
                        <FolderResource
                            {...folders}
                            application={application}
                        />

                        {/*Workflow*/}
                        {mandatoryWorkflow.length === 0 ? null : (
                            <Card>
                                <Heading.h4>Script</Heading.h4>
                                <Grid gridTemplateColumns={"1fr"} gap={"16px"} mt={"16px"}>
                                    {mandatoryWorkflow.map(param => (
                                        <Widget key={param.name} parameter={param} errors={errors} provider={provider}
                                            injectWorkflowParameters={setWorkflowInjectParameters}
                                            setErrors={setErrors} active application={application} />
                                    ))}
                                </Grid>
                            </Card>
                        )}

                        {/*Readme*/}
                        {readmeParams.length === 0 ? null : (
                            <Card backgroundColor={"var(--warningMain)"} color={"warningContrast"}>
                                <Heading.h4>
                                    {estimatedCost.product == null ?
                                        "Information" :
                                        `Information from ${getProviderTitle(estimatedCost.product.category.provider)}`
                                    }
                                </Heading.h4>
                                <Grid gridTemplateColumns={"1fr"} gap={"16px"} mt={"16px"}>
                                    {readmeParams.map(param => (
                                        <Widget key={param.name} parameter={param} errors={errors} provider={provider}
                                            injectWorkflowParameters={setWorkflowInjectParameters}
                                            setErrors={setErrors} active application={application} />
                                    ))}
                                </Grid>
                            </Card>
                        )}

                        {/*Modules*/}
                        {modulesParam.length === 0 ? null : (
                            <Card>
                                <Heading.h4>Modules</Heading.h4>
                                <Grid gridTemplateColumns={"1fr"} gap={"16px"} mt={"16px"}>
                                    {modulesParam.map(param => (
                                        <Widget key={param.name} parameter={param} errors={errors} provider={provider}
                                            injectWorkflowParameters={setWorkflowInjectParameters}
                                            setErrors={setErrors} active application={application} />
                                    ))}
                                </Grid>
                            </Card>
                        )}

                        {/* Parameters */}
                        {mandatoryParameters.length === 0 ? null : (
                            <Card>
                                <Heading.h4>Mandatory Parameters</Heading.h4>
                                <Grid gridTemplateColumns={"1fr"} gap={"16px"} mt={"16px"}>
                                    {mandatoryParameters.map(param => (
                                        <Widget key={param.name} parameter={param} errors={errors} provider={provider}
                                            injectWorkflowParameters={setWorkflowInjectParameters}
                                            setErrors={setErrors} active application={application} />
                                    ))}
                                </Grid>
                            </Card>
                        )}
                        {activeParameters.length === 0 ? null : (
                            <Card>
                                <Heading.h4>Additional Parameters</Heading.h4>
                                <Grid gridTemplateColumns={"1fr"} gap={"16px"} mt={"16px"}>
                                    {activeParameters.map(param => (
                                        <Widget
                                            key={param.name} parameter={param} errors={errors} provider={provider}
                                            injectWorkflowParameters={setWorkflowInjectParameters}
                                            setErrors={setErrors}
                                            active
                                            application={application}
                                            onRemove={() => {
                                                if (errors[param.name]) {
                                                    delete errors[param.name];
                                                    setErrors({...errors});
                                                }
                                                setActiveOptParams(activeOptParams.filter(it => it !== param.name));
                                            }}
                                        />
                                    ))}
                                </Grid>
                            </Card>
                        )}
                        {inactiveParameters.length === 0 ? null : (
                            <Card>
                                <OptionalWidgetSearch pool={inactiveParameters} mapper={param => (
                                    <Widget key={param.name} parameter={param} errors={errors} provider={provider}
                                        setErrors={setErrors}
                                        active={false}
                                        application={application}
                                        onActivate={() => {
                                            setActiveOptParams([...activeOptParams, param.name]);
                                        }}
                                        injectWorkflowParameters={setWorkflowInjectParameters}
                                    />
                                )} />
                            </Card>
                        )}

                        {/* SSH */}
                        <SshWidget application={application} onSshStatusChanged={setSshEnabled}
                            onSshKeysValid={setSshValid} initialEnabledStatus={initialSshEnabled} />

                        {/* Resources */}

                        <IngressResource
                            {...ingress}
                            application={application}
                        />

                        <PeerResource
                            {...peers}
                            application={application}
                        />

                        <NetworkIPResource
                            {...networks}
                            application={application}
                        />

                    </Grid>
                </ContainerForText>
            </>}
    />;
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