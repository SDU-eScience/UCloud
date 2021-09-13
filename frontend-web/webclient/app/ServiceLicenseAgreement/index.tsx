import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {Client} from "@/Authentication/HttpClientInstance";
import {LoadingMainContainer} from "@/MainContainer/MainContainer";
import {useCallback} from "react";
import * as React from "react";
import {RouteComponentProps} from "react-router";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {Button, Markdown} from "@/ui-components";
import styled from "styled-components";
import {addStandardDialog} from "@/UtilityComponents";

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

const Container = styled.div`
    margin: 0 auto;
    min-width: 300px;
    max-width: 1000px;
`;

const ServiceLicenseAgreement: React.FunctionComponent<RouteComponentProps> = props => {
    const [sla] = useCloudAPI<ServiceAgreementText>(fetchSla(), {version: 0, text: ""});
    const [commandLoading, invokeCommand] = useCloudCommand();
    const onAccept = useCallback(async () => {
        try {
            await invokeCommand(acceptSla(sla.data.version));
            await Client.invalidateAccessToken();
            props.history.push("/");
        } catch (res) {
            const response = res.response;
            const why: string = response?.why ?? "Error while attempting to accept agreement";
            snackbarStore.addFailure(why, false);
        }
    }, [sla, invokeCommand, props.history]);

    return (
        <LoadingMainContainer
            loading={sla.loading || commandLoading}
            error={sla.error?.why}
            header={null}
            main={
                (
                    <Container>
                        <Markdown>{sla.data.text}</Markdown>

                        <Button color={"green"} onClick={() => addStandardDialog({
                            message: "",
                            confirmText: "I have read and accept the terms of service",
                            cancelText: "Back",
                            onConfirm: onAccept
                        })} fullWidth>
                            I have read and accept the terms of service
                        </Button>
                    </Container>
                )
            }
        />
    );
};

export default ServiceLicenseAgreement;
