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
    Checkbox,
    ContainerForText,
    Flex, Grid,
    Input,
    Label,
    OutlineButton,
    VerticalButtonGroup
} from "ui-components";
import Link from "ui-components/Link";
import {OptionalWidgetSearch, setWidgetValues, validateWidgets, Widget} from "Applications/Jobs/Widgets";
import {compute} from "UCloud";
import AppParameterValue = compute.AppParameterValue;
import ApplicationParameter = compute.ApplicationParameter;
import styled from "styled-components";
import {MandatoryField} from "Applications/Widgets/BaseParameter";
import {MachineTypes} from "Applications/MachineTypes";
import {TextSpan} from "ui-components/Text";
import Warning from "ui-components/Warning";
import {dialogStore} from "Dialog/DialogStore";
import * as PublicLinks from "Applications/PublicLinks/Management";
import * as Heading from "ui-components/Heading";
import {fileTablePage} from "Utilities/FileUtilities";
import {Client} from "Authentication/HttpClientInstance";
import BaseLink from "ui-components/BaseLink";
import {FolderResource} from "Applications/Jobs/Resources/Folders";
import {IngressResource} from "Applications/Jobs/Resources/Ingress";
import {PeerResource} from "Applications/Jobs/Resources/Peers";
import {useResource} from "Applications/Jobs/Resources";

export const Create: React.FunctionComponent = () => {
    const {appName, appVersion} = useRouteMatch<{ appName: string, appVersion: string }>().params;
    const [commandLoading, invokeCommand] = useCloudCommand();
    const [applicationResp, fetchApplication] = useCloudAPI<UCloud.compute.ApplicationWithFavoriteAndTags | null>(
        {noop: true},
        null
    );

    const values = useRef<Record<string, AppParameterValue>>({})
    const [generation, setGeneration] = useState<number>(0);
    const [errors, setErrors] = useState<Record<string, string>>({});

    const nameRef = useRef<HTMLInputElement>(null);
    const hoursRef = useRef<HTMLInputElement>(null);
    const minutesRef = useRef<HTMLInputElement>(null);
    const replicasRef = useRef<HTMLInputElement>(null);
    const urlRef = useRef<HTMLInputElement>(null);

    const folders = useResource("resourceFolder");
    const ingress = useResource("resourceIngress");
    const peers = useResource("resourcePeer");

    const [activeOptParams, setActiveOptParams] = useState<string[]>([]);

    useEffect(() => {
        fetchApplication(UCloud.compute.apps.findByNameAndVersion({appName, appVersion}))
    }, [appName, appVersion]);

    // TODO
    const application = applicationResp.data;
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
                    onClick={() => {
                        const {errors, values} = validateWidgets(application.invocation.parameters!);
                        setErrors(errors)
                        if (Object.keys(errors).length === 0) {
                            console.log("Valid!", values);
                        } else {
                            console.log("Not valid!", values);
                        }
                    }}
                >
                    Submit
                </Button>
            </VerticalButtonGroup>
        }
        main={
            <ContainerForText>
                <Grid gridTemplateColumns={"1fr"} gridGap={"48px"} width={"100%"} mb={"48px"}>
                    {/* Reservation metadata */}
                    <Box>
                        <Label mb={"4px"} mt={"4px"}>
                            Job name
                            <Input ref={nameRef} placeholder={"Example: Run with parameters XYZ"}/>
                        </Label>

                        <Flex mb={"1em"}>
                            <Label>
                                Hours
                                <Input ref={hoursRef}/>
                            </Label>
                            <Box ml="4px"/>
                            <Label>
                                Minutes
                                <Input ref={minutesRef}/>
                            </Label>
                        </Flex>

                        {!application.invocation.allowMultiNode ? null : (
                            <Flex mb={"1em"}>
                                <Label>
                                    Number of replicas
                                    <Input ref={replicasRef}/>
                                </Label>
                            </Flex>
                        )}

                        <div>
                            <Label>Machine type <MandatoryField/></Label>
                            <MachineTypes reservation={""} setReservation={() => 42}/>
                        </div>
                    </Box>


                    {/* Parameters */}
                    <Box>
                        <Heading.h4>Mandatory Parameters</Heading.h4>
                        <Grid gridTemplateColumns={"1fr"} gridGap={"5px"}>
                            {mandatoryParameters.map(param => (
                                <Widget key={param.name} parameter={param} generation={generation} errors={errors}
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
                                    <Widget key={param.name} parameter={param} generation={generation} errors={errors}
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
                            <Widget key={param.name} parameter={param} generation={generation} errors={errors}
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
                        errors={errors}
                    />

                    <PeerResource
                        {...peers}
                        application={application}
                        errors={errors}
                    />
                </Grid>
            </ContainerForText>
        }
    />;
}
