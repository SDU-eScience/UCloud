import {callAPIWithErrorHandler, useCloudCommand} from "Authentication/DataHook";
import {Client} from "Authentication/HttpClientInstance";
import MainContainer from "MainContainer/MainContainer";
import {useLoading, useTitle} from "Navigation/Redux/StatusActions";
import * as React from "react";
import {snackbarStore} from "Snackbar/SnackbarStore";
import * as UCloud from "UCloud";
import {Box, Button, Input, Label} from "ui-components";
import {errorMessageOrDefault, inDevEnvironment, onDevSite} from "UtilityFunctions";
import {useLayoutEffect, useRef, useState} from "react";
import {SidebarPages, useSidebarPage} from "ui-components/Sidebar";
import {useRefreshFunction} from "Navigation/Redux/HeaderActions";
import {Toggle} from "ui-components/Toggle";
import {useHistory} from "react-router";
import {FindByStringId} from "UCloud";
import {getQueryParamOrElse} from "Utilities/URIUtilities";

function ApproveOrSign(): JSX.Element | null {
    const [loading, invokeCommand] = useCloudCommand();

    useTitle("Approve Provider");
    useSidebarPage(SidebarPages.Admin);
    useLoading(loading);
    const history = useHistory();

    const inputRef = useRef<HTMLInputElement>(null);
    const userIsAdmin = Client.userIsAdmin;
    const shouldSign = !userIsAdmin;
    const devMode = inDevEnvironment() || onDevSite();
    const [isAlsoSigning, setIsAlsoSigning] = useState(devMode);

    useLayoutEffect(() => {
        inputRef.current!.value = getQueryParamOrElse(history.location.search, "token", "");
    }, []);

    return <MainContainer
        main={
            <Box maxWidth={800} mt={30} marginLeft="auto" marginRight="auto">
                <form onSubmit={submit}>
                    <Label>
                        Enter authorization code:
                        <Input ref={inputRef} height={"48px"}/>
                    </Label>

                    {!userIsAdmin || !devMode ? null :
                        <Label>
                            (Admin only) Sign the request also:
                            <Toggle checked={isAlsoSigning} onChange={() => setIsAlsoSigning(!isAlsoSigning)}/>
                        </Label>
                    }

                    <Button type={"submit"} fullWidth>Submit</Button>
                </form>
            </Box>
        }
    />;

    async function submit(e: React.SyntheticEvent) {
        e.preventDefault();
        if (shouldSign || isAlsoSigning) {
            const res = await invokeCommand(
                UCloud.provider.providers.requestApproval({
                    type: "sign",
                    token: inputRef.current?.value ?? ""
                })
            );

            if (res && !userIsAdmin) {
                snackbarStore.addSuccess(
                    "Your request has successfully been submitted and is now awaiting approval",
                    true
                );

                history.push("/");
                return;
            }
        }

        if (isAlsoSigning && userIsAdmin) {
            const res = await callAPIWithErrorHandler<FindByStringId>(
                UCloud.provider.providers.approve({
                    token: inputRef.current?.value ?? ""
                })
            );

            if (res) {
                history.push(`/admin/providers/view/${encodeURIComponent(res.id)}`)
                res.id
            }
        }
    }
}

export default ApproveOrSign;
