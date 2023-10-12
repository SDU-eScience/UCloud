import {useCloudAPI, useCloudCommand} from "@/Authentication/DataHook";
import {Client} from "@/Authentication/HttpClientInstance";
import {LoadingMainContainer} from "@/MainContainer/MainContainer";
import {useCallback} from "react";
import * as React from "react";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {Button, Markdown} from "@/ui-components";
import {addStandardDialog} from "@/UtilityComponents";
import {initializeResources} from "@/Services/ResourceInit";
import {useNavigate} from "react-router";
import {injectStyleSimple} from "@/Unstyled";

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

const Container = injectStyleSimple("container", `
    margin: 0 auto;
    min-width: 300px;
    max-width: 1000px;
`);

const ServiceLicenseAgreement: React.FunctionComponent = () => {
    const [sla] = useCloudAPI<ServiceAgreementText>(fetchSla(), {version: 0, text: ""});
    const navigate = useNavigate();
    const [commandLoading, invokeCommand] = useCloudCommand();
    const onAccept = useCallback(async () => {
        try {
            await invokeCommand(acceptSla(sla.data.version));
            await Client.invalidateAccessToken();
            initializeResources()
            navigate("/");
        } catch (res) {
            const response = res.response;
            const why: string = response?.why ?? "Error while attempting to accept agreement";
            snackbarStore.addFailure(why, false);
        }
    }, [sla, invokeCommand, navigate]);

    return (
        <LoadingMainContainer
            loading={sla.loading || commandLoading}
            error={sla.error?.why}
            header={null}
            main={
                (
                    <div className={Container}>
                        <Markdown>{sla.data.text}</Markdown>

                        <Button color={"green"} onClick={() => addStandardDialog({
                            message: "",
                            confirmText: "I have read and accept the terms of service",
                            cancelText: "Back",
                            onConfirm: onAccept
                        })} fullWidth>
                            I have read and accept the terms of service
                        </Button>
                    </div>
                )
            }
        />
    );
};

export default ServiceLicenseAgreement;
