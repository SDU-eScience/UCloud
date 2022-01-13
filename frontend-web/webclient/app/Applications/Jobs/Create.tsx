import * as React from "react";
import {useCallback, useEffect, useLayoutEffect, useRef, useState} from "react";
import * as UCloud from "@/UCloud";
import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {useHistory} from "react-router";
import {MainContainer} from "@/MainContainer/MainContainer";
import {AppHeader, Information} from "@/Applications/View";
import {Box, Button, ContainerForText, Grid, Icon, Markdown, VerticalButtonGroup} from "@/ui-components";
import Link from "@/ui-components/Link";
import {OptionalWidgetSearch, setWidgetValues, validateWidgets, Widget} from "@/Applications/Jobs/Widgets";
import * as Heading from "@/ui-components/Heading";
import {FolderResource} from "@/Applications/Jobs/Resources/Folders";
import {getProviderField, IngressResource} from "@/Applications/Jobs/Resources/Ingress";
import {PeerResource} from "@/Applications/Jobs/Resources/Peers";
import {createSpaceForLoadedResources, injectResources, useResource} from "@/Applications/Jobs/Resources";
import {
    ReservationErrors,
    ReservationParameter,
    setReservation,
    validateReservation
} from "@/Applications/Jobs/Widgets/Reservation";
import {displayErrorMessageOrDefault, extractErrorCode} from "@/UtilityFunctions";
import {addStandardDialog, WalletWarning} from "@/UtilityComponents";
import {ImportParameters} from "@/Applications/Jobs/Widgets/ImportParameters";
import LoadingIcon from "@/LoadingIcon/LoadingIcon";
import {SidebarPages, useSidebarPage} from "@/ui-components/Sidebar";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {NetworkIPResource} from "@/Applications/Jobs/Resources/NetworkIPs";
import {bulkRequestOf} from "@/DefaultObjects";
import {getQueryParam} from "@/Utilities/URIUtilities";
import {default as JobsApi, JobSpecification} from "@/UCloud/JobsApi";
import {BulkResponse, FindByStringId} from "@/UCloud";
import {Product, usageExplainer} from "@/Accounting";
import styled from "styled-components";

interface InsufficientFunds {
    why?: string;
    errorCode?: string;
}

export const Create: React.FunctionComponent = () => {
    const history = useHistory();
    const appName = getQueryParam(history.location.search, "app");
    const appVersion = getQueryParam(history.location.search, "version");

    if (!appName || !appVersion) {
        history.push("/");
        return null;
    }

    const [isLoading, invokeCommand] = useCloudCommand();
    const [applicationResp, fetchApplication] = useCloudAPI<UCloud.compute.ApplicationWithFavoriteAndTags | null>(
        {noop: true},
        null
    );

    const [previousResp, fetchPrevious] = useCloudAPI<UCloud.Page<UCloud.compute.ApplicationSummaryWithFavorite> | null>(
        {noop: true},
        null
    );

    const [estimatedCost, setEstimatedCost] = useState<{cost: number, balance: number, product: Product | null}>({
        cost: 0, balance: 0, product: null
    });
    const [insufficientFunds, setInsufficientFunds] = useState<InsufficientFunds | null>(null);
    const [errors, setErrors] = useState<Record<string, string>>({});

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
    const jobBeingLoaded = useRef<Partial<JobSpecification> | null>(null);

    useEffect(() => {
        fetchApplication(UCloud.compute.apps.findByNameAndVersion({appName, appVersion}))
        fetchPrevious(UCloud.compute.apps.findByName({appName}));
    }, [appName, appVersion]);

    const application = applicationResp.data;

    const onLoadParameters = useCallback((importedJob: Partial<JobSpecification>) => {
        if (application == null) return;
        jobBeingLoaded.current = null;
        const parameters = application.invocation.parameters;
        const values = importedJob.parameters ?? {};
        const resources = importedJob.resources ?? [];

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
                setActiveOptParams(optionalParameters);
                return;
            }
        }

        // Find resources and render if needed
        if (createSpaceForLoadedResources(folders, resources, "file", jobBeingLoaded, importedJob)) return;
        if (createSpaceForLoadedResources(peers, resources, "peer", jobBeingLoaded, importedJob)) return;
        if (createSpaceForLoadedResources(ingress, resources, "ingress", jobBeingLoaded, importedJob)) return;
        if (createSpaceForLoadedResources(networks, resources, "network", jobBeingLoaded, importedJob)) return;

        // Load reservation
        setReservation(importedJob);

        // Load parameters
        for (const param of parameters) {
            const value = values[param.name];
            if (value) {
                setWidgetValues([{param, value}]);
            }
        }

        // Load resources
        injectResources(folders, resources, "file");
        injectResources(peers, resources, "peer");
        injectResources(ingress, resources, "ingress");
        injectResources(networks, resources, "network");
    }, [application, activeOptParams, folders, peers]);

    useLayoutEffect(() => {
        if (jobBeingLoaded.current !== null) {
            onLoadParameters(jobBeingLoaded.current);
        }
    });

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

                history.push(`/jobs/properties/${ids[0]?.id}?app=${application.metadata.name}`);
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

    useSidebarPage(SidebarPages.Runs);
    useTitle(application == null ? `${appName} ${appVersion}` : `${application.metadata.title} ${appVersion}`);

    if (applicationResp.loading) return <MainContainer main={<LoadingIcon size={36} />} />;

    if (application == null) {
        return (
            <MainContainer
                main={<Heading.h3>Unable to find application &apos;{appName} v{appVersion}&apos;</Heading.h3>}
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

    return <MainContainer
        headerSize={92}
        header={
            <AppHeader slim application={application} allVersions={previousResp.data?.items ?? []} />
        }
        sidebar={
            <VerticalButtonGroup>
                <Link
                    to={`/applications/details/${appName}/${appVersion}/`}>
                    <Button fullWidth>
                        App Details
                    </Button>
                </Link>
                <Button
                    type={"button"}
                    color={"blue"}
                    disabled={isLoading}
                    onClick={() => submitJob(false)}
                >
                    Submit
                </Button>

                <Box mt={32} color={estimatedCost.balance >= estimatedCost.cost ? "black" : "red"} textAlign="center">
                    {estimatedCost.balance === 0 || estimatedCost.product == null ? null : (
                        <>
                            <Icon name={"grant"} />{" "}
                            Estimated cost: <br />

                            {usageExplainer(estimatedCost.cost, estimatedCost.product.productType,
                                estimatedCost.product.chargeType, estimatedCost.product.unitOfPrice)}
                        </>
                    )}
                </Box>
                <Box mt={32} color="black" textAlign="center">
                    {estimatedCost.balance === 0 || estimatedCost.product == null ? null : (
                        <>
                            <Icon name="grant" />{" "}
                            Current balance: <br />

                            {usageExplainer(estimatedCost.balance, estimatedCost.product.productType,
                                estimatedCost.product.chargeType, estimatedCost.product.unitOfPrice)}
                        </>
                    )}
                </Box>
            </VerticalButtonGroup>
        }
        main={
            <>
                <ContainerForText left>
                    <b>Description</b>
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
                </ContainerForText>
                <ContainerForText>
                    <Grid gridTemplateColumns={"1fr"} gridGap={"48px"} width={"100%"} mb={"48px"} mt={"16px"}>
                        {insufficientFunds ? <WalletWarning errorCode={insufficientFunds.errorCode} /> : null}
                        <ImportParameters application={application} onImport={onLoadParameters}
                            importDialogOpen={importDialogOpen} setImportDialogOpen={setImportDialogOpen}
                            onImportDialogClose={() => setImportDialogOpen(false)} />
                        <ReservationParameter
                            application={application}
                            errors={reservationErrors}
                            onEstimatedCostChange={(cost, balance, product) => setEstimatedCost({cost, balance, product})}
                        />

                        {/* Parameters */}
                        {mandatoryParameters.length === 0 ? null : (
                            <Box>
                                <Heading.h4>Mandatory Parameters</Heading.h4>
                                <Grid gridTemplateColumns={"1fr"} gridGap={"5px"}>
                                    {mandatoryParameters.map(param => (
                                        <Widget key={param.name} parameter={param} errors={errors} provider={provider}
                                            active />
                                    ))}
                                </Grid>
                            </Box>
                        )}
                        {activeParameters.length === 0 ? null : (
                            <Box>
                                <Heading.h4>Additional Parameters</Heading.h4>
                                <Grid gridTemplateColumns={"1fr"} gridGap={"5px"}>
                                    {activeParameters.map(param => (
                                        <Widget key={param.name} parameter={param} errors={errors} provider={provider}
                                            active
                                            onRemove={() => {
                                                setActiveOptParams(activeOptParams.filter(it => it !== param.name));
                                            }}
                                        />
                                    ))}
                                </Grid>
                            </Box>
                        )}
                        {inactiveParameters.length === 0 ? null : (
                            <GrayBox>
                                <OptionalWidgetSearch pool={inactiveParameters} mapper={param => (
                                    <Widget key={param.name} parameter={param} errors={errors} provider={provider}
                                        active={false}
                                        onActivate={() => {
                                            setActiveOptParams([...activeOptParams, param.name]);
                                        }}
                                    />
                                )} />
                            </GrayBox>
                        )}

                        {/* Resources */}
                        <IngressResource
                            {...ingress}
                            application={application}
                        />


                        <FolderResource
                            {...folders}
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

export const GrayBox = styled.div`
    padding: 12px 12px 12px 12px;
    background-color: var(--lightGray);
    border-radius: 15px;

    & > div > div > button {
        margin-top: auto;
        margin-bottom: auto;
    }
`;

export default Create;
