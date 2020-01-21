import {APICallParameters, useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {Client} from "Authentication/HttpClientInstance";
import {LoadingMainContainer} from "MainContainer/MainContainer";
import {useCallback} from "react";
import * as React from "react";
import {RouteComponentProps} from "react-router";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {Button, ContainerForText, Markdown} from "ui-components";
import * as Heading from "ui-components/Heading";

function fetchSla(): APICallParameters {
    return {
        reloadId: Math.random(),
        method: "GET",
        path: "/sla"
    };
}

function acceptSla(version: number): APICallParameters {
    return {
        reloadId: Math.random(),
        method: "POST",
        path: "/sla/accept",
        payload: {version}
    };
}

interface ServiceAgreementText {
    version: number;
    text: string;
}

const ServiceLicenseAgreement: React.FunctionComponent<RouteComponentProps> = props => {
    const [sla] = useCloudAPI<ServiceAgreementText>(fetchSla(), {version: 0, text: ""});
    const [commandLoading, invokeCommand] = useAsyncCommand();
    const onAccept = useCallback(async () => {
        try {
            await invokeCommand(acceptSla(sla.data.version));
            await Client.invalidateAccessToken();
            props.history.push("/");
        } catch (res) {
            const response = res.response;
            const why: string = response?.why ?? "Error while attempting to accept agreement";
            snackbarStore.addFailure(why);
        }
    }, [sla, invokeCommand, props.history]);

    return (
        <LoadingMainContainer
            loading={sla.loading || commandLoading}
            error={sla.error?.why}
            header={<Heading.h2>Service License Agreement</Heading.h2>}
            main={
                (
                    <>
                        <ContainerForText>
                            <Markdown>{sla.data.text}</Markdown>
                        </ContainerForText>

                        <Button color={"green"} onClick={onAccept}>
                            Accept
                        </Button>
                    </>
                )
            }
        />
    );
};

export default ServiceLicenseAgreement;
