import {useInput} from "Admin/LicenseServers";
import {useCloudCommand} from "Authentication/DataHook";
import {Client} from "Authentication/HttpClientInstance";
import MainContainer from "MainContainer/MainContainer";
import {useTitle} from "Navigation/Redux/StatusActions";
import * as React from "react";
import {snackbarStore} from "Snackbar/SnackbarStore";
import * as UCloud from "UCloud";
import {Box, Button, Checkbox, Flex, Heading, Input, Label} from "ui-components";
import {errorMessageOrDefault, stopPropagation} from "UtilityFunctions";



function Create(): JSX.Element | null {
    const [loading, invokeCommand] = useCloudCommand();

    useTitle("Create Provider");

    const idInput = useInput();
    const domainInput = useInput();
    const portInput = useInput();
    const [isHttps, setHttps] = React.useState(true);
    const [dockerSettings, setDocker] = React.useState(docker);
    const [virtualMachineSettings, setVirtualMachine] = React.useState(virtualMachine);

    const dEnabled = dockerSettings.enabled;
    const vmEnabled = virtualMachineSettings.enabled;

    if (!Client.userIsAdmin) return null;

    return <MainContainer
        main={<Box maxWidth={800} mt={30} marginLeft="auto" marginRight="auto">
            <Label>
                ID
                <Input error={idInput.hasError} ref={idInput.ref} placeholder="ID..." />
            </Label>
            <Flex>
                <Label width="80%">
                    Domain
                    <Input error={domainInput.hasError} ref={domainInput.ref} placeholder="Domain..." />
                </Label>
                <Label ml="8px" width="20%">
                    Port
                    <Input type="number" min={0} max={65536} ref={portInput.ref} placeholder="Port..." />
                </Label>
            </Flex>
            <Label>
                <Checkbox onClick={() => setHttps(h => !h)} checked={isHttps} onChange={stopPropagation} />
                Uses HTTPS
            </Label>

            <Heading>Docker Support</Heading>
            <Label>
                <Checkbox onClick={() => setDocker(s => ({...s, enabled: !s.enabled}))} checked={dockerSettings.enabled} onChange={stopPropagation} />
                Docker enabled: Flag to enable/disable this feature. <b>All other flags are ignored if this is <code>false</code>.</b>
            </Label>
            <Label>
                <Checkbox disabled={!dEnabled} onClick={() => setDocker(s => ({...s, batch: !s.batch}))} checked={dockerSettings.batch && dEnabled} onChange={e => e} />
                Batch: Flag to enable/disable <code>BATCH</code> <code>Application</code>s
            </Label>
            <Label>
                <Checkbox disabled={!dEnabled} onClick={() => setDocker(s => ({...s, logs: !s.logs}))} checked={dockerSettings.logs && dEnabled} onChange={e => e} />
                Log: lag to enable/disable the log API
            </Label>
            <Label>
                <Checkbox disabled={!dEnabled} onClick={() => setDocker(s => ({...s, peers: !s.peers}))} checked={dockerSettings.peers && dEnabled} onChange={e => e} />
                Peers: Flag to enable/disable connection between peering <code>Job</code>s
            </Label>
            <Label>
                <Checkbox disabled={!dEnabled} onClick={() => setDocker(s => ({...s, terminal: !s.terminal}))} checked={dockerSettings.terminal && dEnabled} onChange={e => e} />
                Terminal: Flag to enable/disable the interactive terminal API
            </Label>
            <Label>
                <Checkbox disabled={!dEnabled} onClick={() => setDocker(s => ({...s, vnc: !s.vnc}))} checked={dockerSettings.vnc && dEnabled} onChange={e => e} />
                VNC: Flag to enable/disable the interactive interface of <code>VNC</code> <code>Application</code>s
            </Label>
            <Label>
                <Checkbox disabled={!dEnabled} onClick={() => setDocker(s => ({...s, web: !s.web}))} checked={dockerSettings.web && dEnabled} onChange={e => e} />
                Web: Flag to enable/disable the interactive interface of <code>WEB</code> <code>Application</code>s
            </Label>

            <Heading>Virtual Machine Support</Heading>
            <Label>
                <Checkbox onClick={() => setVirtualMachine(s => ({...s, enabled: !s.enabled}))} checked={vmEnabled} onChange={e => e} />
                Virtual machine enabled: Flag to enable/disable this feature. <b>All other flags are ignored if this is <code>false</code>.</b>
            </Label>
            <Label>
                <Checkbox disabled={!vmEnabled} onClick={() => setVirtualMachine(s => ({...s, logs: !s.logs}))} checked={virtualMachineSettings.logs && vmEnabled} onChange={e => e} />
                Logs: Flag to enable/disable the log API
            </Label>
            <Label>
                <Checkbox disabled={!vmEnabled} onClick={() => setVirtualMachine(s => ({...s, terminal: !s.terminal}))} checked={virtualMachineSettings.terminal && vmEnabled} onChange={e => e} />
                Terminal: Flag to enable/disable the interactive terminal API
            </Label>
            <Label>
                <Checkbox disabled={!vmEnabled} onClick={() => setVirtualMachine(s => ({...s, vnc: !s.vnc}))} checked={virtualMachineSettings.vnc && vmEnabled} onChange={e => e} />
                VNC: Flag to enable/disable the VNC API
            </Label>

            <Button my="12px" disabled={loading} onClick={submit}>Submit</Button>
        </ Box>}
    />;

    async function submit() {
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
            await invokeCommand(UCloud.provider.providers.create({
                id,
                domain,
                https: isHttps,
                manifest: {
                    features: {
                        compute: {
                            docker: dockerSettings,
                            virtualMachine: virtualMachineSettings
                        }
                    }
                },
                port: isNaN(port) ? undefined : port,
                /* items,
                type,
                product */
            }), {defaultErrorHandler: false});
        } catch (e) {
            snackbarStore.addFailure(errorMessageOrDefault(e, "Failed to create provider"), false);
        }
    }
}

const docker = {
    enabled: false,
    web: false,
    vnc: false,
    batch: false,
    logs: false,
    terminal: false,
    peers: false,
};

const virtualMachine = {
    enabled: false,
    logs: false,
    vnc: false,
    terminal: false
}

export default Create;
