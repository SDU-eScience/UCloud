import * as React from "react";
import {useCallback, useEffect, useLayoutEffect, useRef, useState} from "react";
import * as UCloud from "UCloud";
import {compute} from "UCloud";
import {useCloudAPI, useCloudCommand} from "Authentication/DataHook";
import {useHistory, useRouteMatch} from "react-router";
import {MainContainer} from "MainContainer/MainContainer";
import {AppHeader} from "Applications/View";
import {Box, Button, ContainerForText, Flex, Grid, Icon, OutlineButton, VerticalButtonGroup} from "ui-components";
import Link from "ui-components/Link";
import {OptionalWidgetSearch, setWidgetValues, validateWidgets, Widget} from "Applications/Jobs/Widgets";
import * as Heading from "ui-components/Heading";
import {FolderResource} from "Applications/Jobs/Resources/Folders";
import {getProviderField, IngressResource, IngressRow, validateIngresses} from "Applications/Jobs/Resources/Ingress";
import {PeerResource} from "Applications/Jobs/Resources/Peers";
import {createSpaceForLoadedResources, injectResources, useResource} from "Applications/Jobs/Resources";
import {
    ReservationErrors,
    ReservationParameter,
    setReservation,
    validateReservation
} from "Applications/Jobs/Widgets/Reservation";
import {displayErrorMessageOrDefault, extractErrorCode} from "UtilityFunctions";
import {addStandardDialog, WalletWarning} from "UtilityComponents";
import {creditFormatter} from "Project/ProjectUsage";
import {ImportParameters} from "Applications/Jobs/Widgets/ImportParameters";
import LoadingIcon from "LoadingIcon/LoadingIcon";
import {FavoriteToggle} from "Applications/FavoriteToggle";
import {SidebarPages, useSidebarPage} from "ui-components/Sidebar";
import JobParameters = compute.JobParameters;
import {useTitle} from "Navigation/Redux/StatusActions";
import {snackbarStore} from "Snackbar/SnackbarStore";

interface InsufficientFunds {
    why?: string;
    errorCode?: string;
}

export const Create: React.FunctionComponent = () => {
    const {appName, appVersion} = useRouteMatch<{appName: string, appVersion: string}>().params;
    const [, invokeCommand] = useCloudCommand();
    const [applicationResp, fetchApplication] = useCloudAPI<UCloud.compute.ApplicationWithFavoriteAndTags | null>(
        {noop: true},
        null
    );

    const [estimatedCost, setEstimatedCost] = useState<{cost: number, balance: number}>({cost: 0, balance: 0});
    const [insufficientFunds, setInsufficientFunds] = useState<InsufficientFunds | null>(null);
    const [errors, setErrors] = useState<Record<string, string>>({});

    const provider = getProviderField();

    // NOTE: Should this use `useResource` as well?
    const [urlInfo, setUrlInfo] = useState([{id: React.createRef<HTMLInputElement>(), domain: React.createRef<HTMLInputElement>()}]);
    // NOTEEND

    const folders = useResource("resourceFolder",
        (name) => ({type: "input_directory", description: "", title: "", optional: true, name}));
    const peers = useResource("resourcePeer",
        (name) => ({type: "peer", description: "", title: "", optional: true, name}));

    const [ingressEnabled, setIngressEnabled] = useState(false);

    const [activeOptParams, setActiveOptParams] = useState<string[]>([]);
    const [reservationErrors, setReservationErrors] = useState<ReservationErrors>({});

    const [importDialogOpen, setImportDialogOpen] = useState(false);
    const jobBeingLoaded = useRef<Partial<JobParameters> | null>(null);

    useEffect(() => {
        fetchApplication(UCloud.compute.apps.findByNameAndVersion({appName, appVersion}))
    }, [appName, appVersion]);

    const application = applicationResp.data;
    const history = useHistory();

    const onLoadParameters = useCallback((importedJob: Partial<JobParameters>) => {
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

        // Load reservation
        setReservation(importedJob);

        // Load parameters
        for (const param of parameters) {
            const value = values[param.name]
            if (value) {
                setWidgetValues([{param, value}]);
            }
        }

        // Load resources
        injectResources(folders, resources, "file");
        injectResources(peers, resources, "peer");
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

        const ingressValidation = validateIngresses(urlInfo, ingressEnabled);

        if (Object.keys(errors).length === 0 &&
            reservationValidation.options !== undefined &&
            Object.keys(foldersValidation.errors).length === 0 &&
            Object.keys(peersValidation.errors).length === 0 &&
            ingressValidation.errors.length === 0
        ) {
            const request: UCloud.compute.JobParameters = {
                ...reservationValidation.options,
                application: application?.metadata,
                parameters: values,
                resources: Object.values(foldersValidation.values)
                    .concat(Object.values(peersValidation.values))
                    .concat(ingressValidation.values),
                allowDuplicateJob
            };

            try {
                const response = await invokeCommand<UCloud.compute.JobsCreateResponse>(
                    UCloud.compute.jobs.create(request),
                    {defaultErrorHandler: false}
                );

                const ids = response?.ids;
                if (!ids || ids.length === 0) {
                    snackbarStore.addFailure("UCloud failed to submit the job", false);
                    return;
                }

                history.push(`/applications/jobs/${ids[0]}?app=${application.metadata.name}`);
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
    }, [application, folders, peers]);

    useSidebarPage(SidebarPages.Runs);
    useTitle(application == null ? `${appName} ${appVersion}` : `${application.metadata.title} ${appVersion}`);

    if (application === null) return <MainContainer main={<LoadingIcon size={36} />} />;

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
        headerSize={48}
        header={
            <Flex mx={["0px", "0px", "0px", "0px", "0px", "50px"]}>
                <AppHeader slim application={application} />
            </Flex>
        }
        sidebar={
            <VerticalButtonGroup>
                <Link
                    to={`/applications/details/${appName}/${appVersion}/`}>
                    <OutlineButton fullWidth>
                        App details
                    </OutlineButton>
                </Link>
                <OutlineButton
                    fullWidth
                    color={"darkGreen"}
                    as={"label"}
                    onClick={() => setImportDialogOpen(true)}
                >
                    Import parameters
                </OutlineButton>

                <FavoriteToggle application={application} />

                <Button
                    type={"button"}
                    color={"blue"}
                    onClick={() => submitJob(false)}
                >
                    Submit
                </Button>

                <Box mt={32} color={estimatedCost.balance >= estimatedCost.cost ? "black" : "red"} textAlign="center">
                    {estimatedCost.balance === 0 ? null : (
                        <>
                            <Icon name={"grant"} />{" "}
                            Estimated cost: <br />

                            {creditFormatter(estimatedCost.cost, 0)}
                        </>
                    )}
                </Box>
                <Box mt={32} color="black" textAlign="center">
                    {estimatedCost.balance === 0 ? null : (
                        <>
                            <Icon name="grant" />{" "}
                            Current balance: <br />

                            {creditFormatter(estimatedCost.balance, 0)}
                        </>
                    )}
                </Box>
            </VerticalButtonGroup>
        }
        main={
            <ContainerForText>
                <Grid gridTemplateColumns={"1fr"} gridGap={"48px"} width={"100%"} mb={"48px"} mt={"16px"}>
                    {insufficientFunds ? <WalletWarning errorCode={insufficientFunds.errorCode} /> : null}
                    <ImportParameters application={application} onImport={onLoadParameters}
                        importDialogOpen={importDialogOpen}
                        onImportDialogClose={() => setImportDialogOpen(false)} />
                    <ReservationParameter
                        application={application}
                        errors={reservationErrors}
                        onEstimatedCostChange={(cost, balance) => setEstimatedCost({cost, balance})}
                    />

                    {/* Parameters */}
                    {mandatoryParameters.length === 0 ? null : (
                        <Box>
                            <Heading.h4>Mandatory Parameters</Heading.h4>
                            <Grid gridTemplateColumns={"1fr"} gridGap={"5px"}>
                                {mandatoryParameters.map(param => (
                                    <Widget key={param.name} parameter={param} errors={errors} active={true} />
                                ))}
                            </Grid>
                        </Box>
                    )}
                    {activeParameters.length === 0 ? null : (
                        <Box>
                            <Heading.h4>Additional Parameters</Heading.h4>
                            <Grid gridTemplateColumns={"1fr"} gridGap={"5px"}>
                                {activeParameters.map(param => (
                                    <Widget key={param.name} parameter={param} errors={errors}
                                        active={true}
                                        onRemove={() => {
                                            setActiveOptParams(activeOptParams.filter(it => it !== param.name));
                                        }}
                                    />
                                ))}
                            </Grid>
                        </Box>
                    )}
                    {inactiveParameters.length === 0 ? null : (
                        <OptionalWidgetSearch pool={inactiveParameters} mapper={param => (
                            <Widget key={param.name} parameter={param} errors={errors}
                                active={false}
                                onActivate={() => {
                                    setActiveOptParams([...activeOptParams, param.name]);
                                }}
                            />
                        )} />
                    )}

                    {/* Resources */}

                    <div>
                        <IngressResource
                            application={application}
                            enabled={ingressEnabled}
                            addRow={() => {
                                urlInfo.push({id: React.createRef(), domain: React.createRef()});
                                setUrlInfo([...urlInfo]);
                            }}
                            setEnabled={enabled => setIngressEnabled(enabled)}
                        />

                        {!ingressEnabled ? null :
                            urlInfo.map((refs, index) => <IngressRow key={index} refs={refs} provider={provider} />)
                        }
                    </div>

                    <FolderResource
                        {...folders}
                        application={application}
                    />

                    <PeerResource
                        {...peers}
                        application={application}
                    />
                </Grid>
            </ContainerForText>
        }
    />;
}

export default Create;
