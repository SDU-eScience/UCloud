import * as React from "react";
import {useCallback, useEffect, useRef, useState} from "react";
import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {useLocation, useNavigate} from "react-router";
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
    ReservationErrors,
    ReservationParameter,
    setReservation,
    validateReservation
} from "@/Applications/Jobs/Widgets/Reservation";
import {displayErrorMessageOrDefault, extractErrorCode, prettierString, useDidMount} from "@/UtilityFunctions";
import {addStandardDialog, OverallocationLink, WalletWarning} from "@/UtilityComponents";
import {ImportParameters} from "@/Applications/Jobs/Widgets/ImportParameters";
import LoadingIcon from "@/LoadingIcon/LoadingIcon";
import {usePage} from "@/Navigation/Redux";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {NetworkIPResource, networkIPResourceAllowed} from "@/Applications/Jobs/Resources/NetworkIPs";
import {bulkRequestOf} from "@/UtilityFunctions";
import {getQueryParam} from "@/Utilities/URIUtilities";
import {default as JobsApi, JobSpecification} from "@/UCloud/JobsApi";
import {BulkResponse, FindByStringId} from "@/UCloud";
import {ProductV2, UNABLE_TO_USE_FULL_ALLOC_MESSAGE, balanceToString, priceToString} from "@/Accounting";
import {SshWidget} from "@/Applications/Jobs/Widgets/Ssh";
import {connectionState} from "@/Providers/ConnectionState";
import {Feature, hasFeature} from "@/Features";
import {useUState} from "@/Utilities/UState";
import {flushSync} from "react-dom";
import {Spacer} from "@/ui-components/Spacer";
import {injectStyle} from "@/Unstyled";
import {UtilityBar} from "@/Navigation/UtilityBar";
import {validateMachineReservation} from "@/Applications/Jobs/Widgets/Machines";
import {Resource} from "@/UCloud/ResourceApi";
import {getProviderTitle} from "@/Providers/ProviderTitle";
import * as AppStore from "@/Applications/AppStoreApi";
import {
    ApplicationParameter,
    ApplicationSummaryWithFavorite,
    ApplicationWithFavoriteAndTags
} from "@/Applications/AppStoreApi";
import {TooltipV2} from "@/ui-components/Tooltip";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {UserDetailsState, defaultEmailSettings} from "@/UserSettings/ChangeEmailSettings";
import {mail} from "@/UCloud";
import retrieveEmailSettings = mail.retrieveEmailSettings;
import toggleEmailSettings = mail.toggleEmailSettings;
import AppRoutes from "@/Routes";

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

function getLicense(app: ApplicationWithFavoriteAndTags): string | undefined {
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
    const [applicationResp, fetchApplication] = useCloudAPI<ApplicationWithFavoriteAndTags | null>(
        {noop: true},
        null
    );

    if (applicationResp) {
        usePage(`${applicationResp.data?.metadata.title} ${applicationResp.data?.metadata.version ?? ""}`, SidebarTabId.APPLICATIONS);
    } else {
        usePage(`${appName} ${appVersion ?? ""}`, SidebarTabId.APPLICATIONS);
    }

    const [previousResp, fetchPrevious] = useCloudAPI<Page<ApplicationSummaryWithFavorite> | null>(
        {noop: true},
        null
    );

    const [estimatedCost, setEstimatedCost] = useState<{
        durationInMinutes: number,
        balance: number,
        maxUsable: number,
        numberOfNodes: number,
        product: ProductV2 | null
    }>({
        durationInMinutes: 0, balance: 0, maxUsable: 0, numberOfNodes: 1, product: null
    });
    const [insufficientFunds, setInsufficientFunds] = useState<InsufficientFunds | null>(null);
    const [errors, setErrors] = useState<Record<string, string>>({});
    const [initialSshEnabled, setInitialSshEnabled] = useState<boolean | undefined>(undefined);
    const [sshEnabled, setSshEnabled] = useState(false);
    const [sshValid, setSshValid] = useState(true);

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
    // Note(Jonas): Should be safe to remove.
    const jobBeingLoaded = useRef<Partial<JobSpecification> | null>(null);

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
        const jobStarted = emailNotifications.settings.jobStarted
        const jobStopped = emailNotifications.settings.jobStopped

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

    useEffect(() => {
        if (appName === "syncthing" && !localStorage.getItem("syncthingRedirect")) {
            navigate("/drives");
        }
        fetchApplication(AppStore.findByNameAndVersion({appName, appVersion: appVersion ?? undefined}));
        fetchPrevious(AppStore.findByName({appName}));
    }, [appName, appVersion]);

    const application = applicationResp.data;

    React.useEffect(() => {
        if (application && provider) {
            const params = application.invocation.parameters.filter(it =>
                PARAMETER_TYPE_FILTER.includes(it.type)
            );

            findProviderMismatches(
                provider, {errors, params, setErrors}, networks, folders, peers, ingress
            );
        }
    }, [provider, application]);

    const onLoadParameters = useCallback((importedJob: Partial<JobSpecification>) => {
        if (application == null) return;
        jobBeingLoaded.current = null;
        const parameters = application.invocation.parameters;
        const values = importedJob.parameters ?? {};
        const resources = importedJob.resources ?? [];

        flushSync(() => {
            setActiveOptParams(() => []);
        });

        {
            // Find optional parameters and make sure the widgets are initialized
            const optionalParameters: string[] = [];
            let needsToRenderParams = false;
            for (const param of parameters) {
                if (param.optional && values[param.name]) {
                    optionalParameters.push(param.name);
                    if (activeOptParams.indexOf(param.name) === -1) {
                        needsToRenderParams = true;
                    }
                }
            }

            if (needsToRenderParams) {
                // Not all widgets have been initialized. Trigger an initialization and start over after render.
                jobBeingLoaded.current = importedJob;
                flushSync(() => {
                    setActiveOptParams(() => optionalParameters);
                });
            }
        }

        // Load reservation
        setReservation(importedJob);

        // Load parameters
        for (const param of parameters) {
            const value = values[param.name];
            if (value) {
                setWidgetValues([{param, value}]);
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
        if (folderResourceAllowed(application)) {
            const newSpace = createSpaceForLoadedResources(folders, resources, "file");
            injectResources(newSpace, resources, "file");
        }
        if (peerResourceAllowed(application)) {
            const newSpace = createSpaceForLoadedResources(peers, resources, "peer");
            injectResources(newSpace, resources, "peer");
        }
        if (ingressResourceAllowed(application)) {
            const newSpace = createSpaceForLoadedResources(ingress, resources, "ingress");
            injectResources(newSpace, resources, "ingress");
        }
        if (networkIPResourceAllowed(application)) {
            const newSpace = createSpaceForLoadedResources(networks, resources, "network");
            injectResources(newSpace, resources, "network");
        }

        folders.setErrors({});
        ingress.setErrors({});
        networks.setErrors({});
        peers.setErrors({});
        setErrors({});
        setReservationErrors({});
    }, [application, activeOptParams, folders, peers, networks, ingress]);

    const submitJob = useCallback(async (allowDuplicateJob: boolean) => {
        if (!application) return;

        const {errors, values} = validateWidgets(application.invocation.parameters!);
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
    }, [application, folders, peers, ingress, networks]);

    if (applicationResp.loading || isInitialMount) return <MainContainer main={<LoadingIcon size={36} />} />;

    if (application == null) {
        return (
            <MainContainer
                main={<Heading.h3>Unable to find application &apos;{appName} {appVersion}&apos;</Heading.h3>}
            />
        );
    }

    const mandatoryParameters = application.invocation!.parameters.filter(it =>
        !it.optional
    );

    const activeParameters = application.invocation.parameters.filter(it =>
        it.optional && activeOptParams.indexOf(it.name) !== -1
    )

    const inactiveParameters = application.invocation.parameters.filter(it =>
        !(!it.optional || activeOptParams.indexOf(it.name) !== -1)
    );

    const isMissingConnection = hasFeature(Feature.PROVIDER_CONNECTION) && estimatedCost.product != null &&
        connectionState.canConnectToProvider(estimatedCost.product.category.provider);

    const errorCount = countMandatoryAndOptionalErrors(application.invocation.parameters.filter(it =>
        PARAMETER_TYPE_FILTER.includes(it.type)
    ).map(it => it.name), errors) + countErrors(folders.errors, ingress.errors, networks.errors, peers.errors);
    const anyError = errorCount > 0;

    const appGroup = application.metadata.group;
    const license = getLicense(application);

    return <MainContainer
        main={
            <>
                <Flex mx="50px" mt="32px">
                    <AppHeader
                        title={appGroup?.specification?.title ?? application.metadata.title}
                        application={application}
                        flavors={appGroup?.status?.applications ?? []}
                        allVersions={previousResp.data?.items ?? []}
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

                        <ForkButton name={application.metadata.name} version={application.metadata.version} />
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
                                            <option value="start" selected={jobEmailNotifications === "start"}>Notify me when a job starts</option>
                                            <option value="ends" selected={jobEmailNotifications === "ends"}>Notify me when a job stops</option>
                                            <option value="start_or_ends" selected={jobEmailNotifications === "start_or_ends"}>Notify me when a job starts or stops</option>
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
                                                        <td>{!estimatedCost.product ? "-" : priceToString(estimatedCost.product, estimatedCost.numberOfNodes, estimatedCost.durationInMinutes, {showSuffix: false})}</td>
                                                    </tr>
                                                    <tr>
                                                        <th>Current balance</th>
                                                        <td>{!estimatedCost.product ? "-" : balanceToString(estimatedCost.product.category, estimatedCost.balance)}</td>
                                                    </tr>
                                                    {
                                                        estimatedCost.maxUsable == estimatedCost.balance ? null : (
                                                            <tr>
                                                                <th>Usable balance</th>
                                                                <td>
                                                                    <OverallocationLink>
                                                                        <TooltipV2
                                                                            tooltip={UNABLE_TO_USE_FULL_ALLOC_MESSAGE}
                                                                        >
                                                                            <Icon name={"heroExclamationTriangle"}
                                                                                color={"warningMain"} />

                                                                            {!estimatedCost.product ? "-" : balanceToString(estimatedCost.product.category, estimatedCost.maxUsable)}
                                                                        </TooltipV2>
                                                                    </OverallocationLink>
                                                                </td>
                                                            </tr>
                                                        )
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
                                onEstimatedCostChange={(durationInMinutes, numberOfNodes, balance, maxUsable, product) =>
                                    setEstimatedCost({durationInMinutes, balance, maxUsable, numberOfNodes, product})}
                            />
                        </Card>

                        <FolderResource
                            {...folders}
                            application={application}
                        />

                        {/* Parameters */}
                        {mandatoryParameters.length === 0 ? null : (
                            <Card>
                                <Heading.h4>Mandatory Parameters</Heading.h4>
                                <Grid gridTemplateColumns={"1fr"} gap={"16px"} mt={"16px"}>
                                    {mandatoryParameters.map(param => (
                                        <Widget key={param.name} parameter={param} errors={errors} provider={provider}
                                            setErrors={setErrors}
                                            active />
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
                                            setErrors={setErrors}
                                            active
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
                                        onActivate={() => {
                                            setActiveOptParams([...activeOptParams, param.name]);
                                        }}
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

const ForkButton: React.FunctionComponent<{ name: string, version: string }> = ({name, version}) => {
    if (!hasFeature(Feature.COPY_APP_MOCKUP)) return null;
    return <TooltipV2 tooltip={`Create a fork of this application to customize it`}>
        <Link to={AppRoutes.apps.fork(name, version)} color={"textPrimary"} hoverColor={"textPrimary"} className={ForkButtonStyle}>
            <Icon size="24" name="fork" />
        </Link>
    </TooltipV2>
}

const ForkButtonStyle = injectStyle("fork-button", k => `
    ${k}:hover svg {
        transition: transform 0.2s ease;
        transform: translateY(-15%);
    }
`);


export default Create;
