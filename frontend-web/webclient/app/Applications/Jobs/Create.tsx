import * as React from "react";
import * as UCloud from "UCloud";
import {useCloudAPI, useCloudCommand} from "Authentication/DataHook";
import {useRouteMatch} from "react-router";
import {useCallback, useEffect, useRef, useState} from "react";
import {MainContainer} from "MainContainer/MainContainer";
import {AppHeader} from "Applications/View";
import {
    Box,
    Button,
    ContainerForText,
    Flex, Grid,
    OutlineButton,
    VerticalButtonGroup
} from "ui-components";
import Link from "ui-components/Link";
import {OptionalWidgetSearch, validateWidgets, Widget} from "Applications/Jobs/Widgets";
import * as Heading from "ui-components/Heading";
import {FolderResource} from "Applications/Jobs/Resources/Folders";
import {IngressResource} from "Applications/Jobs/Resources/Ingress";
import {PeerResource} from "Applications/Jobs/Resources/Peers";
import {useResource} from "Applications/Jobs/Resources";
import {ReservationErrors, ReservationParameter, validateReservation} from "Applications/Jobs/Widgets/Reservation";
import {displayErrorMessageOrDefault, extractErrorCode} from "UtilityFunctions";
import {addStandardDialog, WalletWarning} from "UtilityComponents";

interface InsufficientFunds {
    why?: string;
    errorCode?: string;
}

export const Create: React.FunctionComponent = () => {
    const {appName, appVersion} = useRouteMatch<{ appName: string, appVersion: string }>().params;
    const [commandLoading, invokeCommand] = useCloudCommand();
    const [applicationResp, fetchApplication] = useCloudAPI<UCloud.compute.ApplicationWithFavoriteAndTags | null>(
        {noop: true},
        null
    );

    const [insufficientFunds, setInsufficientFunds] = useState<InsufficientFunds | null>(null);
    const [errors, setErrors] = useState<Record<string, string>>({});

    const urlRef = useRef<HTMLInputElement>(null);

    const folders = useResource("resourceFolder");
    const ingress = useResource("resourceIngress");
    const peers = useResource("resourcePeer");

    const [activeOptParams, setActiveOptParams] = useState<string[]>([]);

    const [reservationErrors, setReservationErrors] = useState<ReservationErrors>({});

    useEffect(() => {
        fetchApplication(UCloud.compute.apps.findByNameAndVersion({appName, appVersion}))
    }, [appName, appVersion]);

    const application = applicationResp.data;

    const submitJob = useCallback(async (allowDuplicateJob: boolean) => {
        if (!application) return;

        const {errors, values} = validateWidgets(application.invocation.parameters!);
        setErrors(errors)

        const reservationValidation = validateReservation();
        setReservationErrors(reservationValidation.errors);

        const foldersValidation = validateWidgets(folders.ids.map(name => ({
            type: "input_directory",
            name,
            optional: true,
            title: "",
            description: ""
        })));
        folders.setErrors(foldersValidation.errors);

        const peersValidation = validateWidgets(peers.ids.map(name => ({
            type: "peer",
            name,
            optional: true,
            title: "",
            description: ""
        })));
        peers.setErrors(peersValidation.errors);

        if (Object.keys(errors).length === 0 &&
            reservationValidation.options !== undefined &&
            Object.keys(foldersValidation.errors).length === 0 &&
            Object.keys(peersValidation.errors).length === 0
        ) {
            const request: UCloud.compute.JobParameters = {
                ...reservationValidation.options,
                application: application?.metadata,
                parameters: values,
                resources: Object.values(foldersValidation.values)
                    .concat(Object.values(peersValidation.values)),
                allowDuplicateJob
            };

            try {
                await invokeCommand(UCloud.compute.jobs.create(request), {defaultErrorHandler: false});
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
                    setInsufficientFunds({ why, errorCode });
                } else {
                    displayErrorMessageOrDefault(e, "An error occured while submitting the job");
                }
            }
        }
    }, [application, folders, ingress, peers]);

    // TODO
    if (application === null) return <MainContainer main={"Loading"}/>;

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
                <AppHeader slim application={application}/>
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
                >
                    Import parameters
                </OutlineButton>

                <Button fullWidth>
                    Add to favorites
                </Button>

                <Button
                    type={"button"}
                    color={"blue"}
                    onClick={() => submitJob(false)}
                >
                    Submit
                </Button>
            </VerticalButtonGroup>
        }
        main={
            <ContainerForText>
                <Grid gridTemplateColumns={"1fr"} gridGap={"48px"} width={"100%"} mb={"48px"} mt={"16px"}>
                    {insufficientFunds ? <WalletWarning errorCode={insufficientFunds.errorCode} /> : null}
                    <ReservationParameter application={application} errors={reservationErrors}/>

                    {/* Parameters */}
                    <Box>
                        <Heading.h4>Mandatory Parameters</Heading.h4>
                        <Grid gridTemplateColumns={"1fr"} gridGap={"5px"}>
                            {mandatoryParameters.map(param => (
                                <Widget key={param.name} parameter={param} errors={errors}
                                        active={true}
                                />
                            ))}
                        </Grid>
                    </Box>
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
                        )}/>
                    )}

                    {/* Resources */}
                    <IngressResource
                        application={application}
                        inputRef={urlRef}
                        enabled={false}
                        setEnabled={() => 42}
                    />

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
