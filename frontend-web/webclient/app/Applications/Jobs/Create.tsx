import * as React from "react";
import {useCallback, useEffect, useRef, useState} from "react";
import * as UCloud from "@/UCloud";
import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {useLocation, useNavigate} from "react-router";
import {MainContainer} from "@/MainContainer/MainContainer";
import {AppHeader, Information} from "@/Applications/View";
import {Box, Button, Card, ContainerForText, ExternalLink, Flex, Grid, Icon, Link, Markdown, Tooltip} from "@/ui-components";
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
import {displayErrorMessageOrDefault, extractErrorCode, prettierString} from "@/UtilityFunctions";
import {addStandardDialog, WalletWarning} from "@/UtilityComponents";
import {ImportParameters} from "@/Applications/Jobs/Widgets/ImportParameters";
import LoadingIcon from "@/LoadingIcon/LoadingIcon";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {NetworkIPResource, networkIPResourceAllowed} from "@/Applications/Jobs/Resources/NetworkIPs";
import {bulkRequestOf, emptyPageV2} from "@/DefaultObjects";
import {buildQueryString, getQueryParam} from "@/Utilities/URIUtilities";
import {default as JobsApi, JobSpecification} from "@/UCloud/JobsApi";
import {BulkResponse, FindByStringId} from "@/UCloud";
import {Product, usageExplainer} from "@/Accounting";
import {SshWidget} from "@/Applications/Jobs/Widgets/Ssh";
import {connectionState} from "@/Providers/ConnectionState";
import {Feature, hasFeature} from "@/Features";
import {useUState} from "@/Utilities/UState";
import {flushSync} from "react-dom";
import {getProviderTitle} from "@/Providers/ProviderTitle";
import {validateMachineReservation} from "./Widgets/Machines";
import {Resource} from "@/UCloud/ResourceApi";
import {Spacer} from "@/ui-components/Spacer";
import {UtilityBar} from "@/Playground/Playground";
import {injectStyleSimple} from "@/Unstyled";
import {RetrieveGroupResponse, retrieveGroup} from "../api";

interface InsufficientFunds {
    why?: string;
    errorCode?: string;
}

const EstimatesContainerClass = injectStyleSimple("estimates-container", `
    padding-bottom: 20px;
    text-align: right;
`);

const PARAMETER_TYPE_FILTER = ["input_directory", "input_file", "ingress", "peer", "license_server", "network_ip"];

export const Create: React.FunctionComponent = () => {
    const navigate = useNavigate();
    const location = useLocation();
    const appName = getQueryParam(location.search, "app");
    const appVersion = getQueryParam(location.search, "version");

    if (!appName) {
        navigate("/");
        return null;
    }



    const [isLoading, invokeCommand] = useCloudCommand();
    const [applicationResp, fetchApplication] = useCloudAPI<UCloud.compute.ApplicationWithFavoriteAndTags | null>(
        {noop: true},
        null
    );

    const [appGroup, fetchAppGroup] = useCloudAPI<RetrieveGroupResponse | null>(
        {noop: true},
        null
    );

    if (applicationResp) {
        useTitle(`${applicationResp.data?.metadata.name} ${applicationResp.data?.metadata.version ?? ""}`);
    } else {
        useTitle(`${appName} ${appVersion ?? ""}`);
    }

    const [previousResp, fetchPrevious] = useCloudAPI<UCloud.Page<UCloud.compute.ApplicationSummaryWithFavorite> | null>(
        {noop: true},
        null
    );

    const [estimatedCost, setEstimatedCost] = useState<{cost: number, balance: number, product: Product | null}>({
        cost: 0, balance: 0, product: null
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

    useUState(connectionState);

    useEffect(() => {
        if (appName === "syncthing" && !localStorage.getItem("syncthingRedirect")) {
            navigate("/drives");
        }
        fetchApplication(UCloud.compute.apps.findByNameAndVersion({appName, appVersion: appVersion ?? undefined}));
        fetchPrevious(UCloud.compute.apps.findByName({appName}));
    }, [appName, appVersion]);

    useEffect(() => {
        fetchAppGroup(retrieveGroup({name: appName}));
    }, [appName]);

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

    useTitle(application == null ? `${appName} ${appVersion ?? ""}` : `${application.metadata.title} ${appVersion ?? ""}`);

    if (applicationResp.loading) return <MainContainer main={<LoadingIcon size={36} />} />;

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

    return <MainContainer
        main={
            <>
                <Box mx="50px" mt="32px">
                    <Spacer left={
                        <AppHeader
                            title={appGroup?.data?.group.title ?? application.metadata.title}
                            slim application={application}
                            flavors={appGroup?.data?.applications ?? []}
                            allVersions={previousResp.data?.items ?? []}
                            />} right={<>
                        {!application.metadata.website ? null : (
                            <Tooltip
                                trigger={<ExternalLink title="Documentation" href={application.metadata.website}>
                                    <Icon name="documentation" color="blue" />
                                </ExternalLink>}>
                                View documentation
                            </Tooltip>
                        )}
                        <UtilityBar searchEnabled={false} />
                    </>} />
                </Box>
                <ContainerForText>
                    <Grid gridTemplateColumns={"1fr"} gridGap={"48px"} width={"100%"} mb={"48px"} mt={"16px"}>
                        {insufficientFunds ? <WalletWarning errorCode={insufficientFunds.errorCode} /> : null}
                        {isMissingConnection ?
                            <Box mt={32}>
                                <Link to={"/providers/connect"}>
                                    <Icon name="warning" color="orange" mx={8} />
                                    Connection required!
                                </Link>
                            </Box> :
                            <Spacer
                                left={<Flex maxWidth="800px">
                                    <Markdown
                                        unwrapDisallowed
                                        disallowedElements={[
                                            "image",
                                            "heading"
                                        ]}
                                    >
                                        {application.metadata.description}
                                    </Markdown>
                                    <Information simple application={application} />
                                </Flex>}
                                right={
                                    <div>
                                        {estimatedCost.product ? <div className={EstimatesContainerClass}>
                                            Estimated cost: {usageExplainer(estimatedCost.cost, estimatedCost.product.productType, estimatedCost.product.chargeType, estimatedCost.product.unitOfPrice)}<br />
                                            Current balance: {usageExplainer(estimatedCost.balance, estimatedCost.product.productType, estimatedCost.product.chargeType, estimatedCost.product.unitOfPrice)}<br />
                                        </div> : <></>}

                                        <Flex>
                                            <ImportParameters application={application} onImport={onLoadParameters}
                                                importDialogOpen={importDialogOpen} setImportDialogOpen={setImportDialogOpen}
                                                onImportDialogClose={() => setImportDialogOpen(false)} />

                                            {anyError ?
                                                <Tooltip trigger={
                                                    <Button ml={"10px"} type="button" color={"green"} disabled>
                                                        <Icon name="play" />
                                                        Submit
                                                    </Button>
                                                }>
                                                    {errorCount} parameter error{errorCount > 1 ? "s" : ""} to resolve before submitting.
                                                </Tooltip>
                                            :
                                                <Button
                                                    color={"green"}
                                                    type={"button"}
                                                    ml={"10px"}
                                                    disabled={isLoading || !sshValid || isMissingConnection}
                                                    onClick={() => submitJob(false)}
                                                >
                                                    <Icon name="play" />
                                                    Submit
                                                </Button>
                                            }
                                        </Flex>
                                    </div>
                                }
                            />
                        }
                        <Card pt="25px">
                            <ReservationParameter
                                application={application}
                                errors={reservationErrors}
                                onEstimatedCostChange={(cost, balance, product) => setEstimatedCost({cost, balance, product})}
                            />
                        </Card>

                        {/* Parameters */}
                        {mandatoryParameters.length === 0 ? null : (
                            <Card>
                                <Heading.h4>Mandatory Parameters</Heading.h4>
                                <Grid gridTemplateColumns={"1fr"} gridGap={"5px"} mt="-20px">
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
                                <Grid gridTemplateColumns={"1fr"} gridGap={"5px"}>
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

                        <FolderResource
                            {...folders}
                            application={application}
                        />

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

function getParameterName(param: Pick<UCloud.compute.ApplicationParameter, "type" | "name">): string {
    switch (param.type) {
        case "peer": {
            return param.name + "job";
        }
        default: return param.name;
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
        default: return prettierString(type).toLocaleLowerCase();
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

export default Create;
