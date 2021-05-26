import {useInput} from "Admin/LicenseServers";
import {useCloudCommand} from "Authentication/DataHook";
import {Client} from "Authentication/HttpClientInstance";
import MainContainer from "MainContainer/MainContainer";
import {useTitle} from "Navigation/Redux/StatusActions";
import * as React from "react";
import {snackbarStore} from "Snackbar/SnackbarStore";
import * as UCloud from "UCloud";
import {Box, Button, Checkbox, Flex, Input, Label} from "ui-components";
import {errorMessageOrDefault, stopPropagation} from "UtilityFunctions";
import {bulkRequestOf} from "DefaultObjects";
import {useHistory} from "react-router";
import {useProjectId} from "Project";

function Create(): JSX.Element | null {
    const [loading, invokeCommand] = useCloudCommand();

    useTitle("Create Provider");

    const projectId = useProjectId();
    const history = useHistory();
    const idInput = useInput();
    const domainInput = useInput();
    const portInput = useInput();
    const [isHttps, setHttps] = React.useState(true);

    if (!Client.userIsAdmin) return null;

    return <MainContainer
        main={<Box maxWidth={800} mt={30} marginLeft="auto" marginRight="auto">
            <Label>
                ID
                <Input error={idInput.hasError} ref={idInput.ref} placeholder="ID..."/>
            </Label>
            <Flex>
                <Label width="80%">
                    Domain
                    <Input error={domainInput.hasError} ref={domainInput.ref} placeholder="Domain..."/>
                </Label>
                <Label ml="8px" width="20%">
                    Port
                    <Input type="number" min={0} max={65536} ref={portInput.ref} placeholder="Port..."/>
                </Label>
            </Flex>
            <Label>
                <Checkbox onClick={() => setHttps(h => !h)} checked={isHttps} onChange={stopPropagation}/>
                Uses HTTPS
            </Label>

            <Button my="12px" disabled={loading} onClick={submit}>Submit</Button>
        </ Box>}
    />;

    async function submit() {
        if (projectId == null) {
            snackbarStore.addFailure("Please select a valid project before creating a provider", false);
            return;
        }

        const domain = domainInput.ref.current?.value ?? "";
        const id = idInput.ref.current?.value ?? "";
        const port = parseInt(portInput.ref.current?.value ?? "", 10);

        let error = false;

        if (!domain) {
            error = true;
            domainInput.setHasError(true);
        }

        if (!id) {
            error = true;
            idInput.setHasError(true);
        }

        if (error) return;

        try {
            const res = await invokeCommand(
                UCloud.provider.providers.create(bulkRequestOf({
                    id,
                    domain,
                    https: isHttps,
                    port: isNaN(port) ? undefined : port,
                })),
                {defaultErrorHandler: false}
            );

            if (res) {
                history.push(`/admin/providers/view/${encodeURI(id)}`);
            }
        } catch (e) {
            snackbarStore.addFailure(errorMessageOrDefault(e, "Failed to create provider"), false);
        }
    }
}

export default Create;
