import {useRouteMatch} from "react-router";
import * as React from "react";
import {Box, Label, Checkbox, TextArea} from "ui-components";
import Error from "ui-components/Error";
import * as Heading from "ui-components/Heading";
import {useCloudAPI} from "Authentication/DataHook";
import * as UCloud from "UCloud";
import LoadingSpinner from "LoadingIcon/LoadingIcon";
import MainContainer from "MainContainer/MainContainer";

function View(): JSX.Element | null {
    const match = useRouteMatch<{id: string}>();
    const {id} = match.params;
    const [provider] = useCloudAPI<UCloud.provider.Provider | null>(
        UCloud.provider.providers.retrieve({id}),
        null
    );

    if (provider.loading) return <MainContainer main={<LoadingSpinner />} />;
    if (provider.data == null) return null;

    const {https, manifest} = provider.data.specification;
    const {docker, virtualMachine} = manifest.features.compute;

    return (<MainContainer main={
        <Box>
            <Error error={provider.error?.why} />
            <Heading.h3>{id}</Heading.h3>
            <Box>
                <b>Created by:</b> {provider.data.owner.createdBy}
            </Box>
            <Box>
                <b>Domain: </b> {provider.data.specification.domain}
            </Box>

            <Label>
                <Checkbox checked={https} onChange={e => e} />
                Uses HTTPS
            </Label>

            <Heading.h4>Public key</Heading.h4>
            <TextArea maxWidth="1200px" width="100%" value={provider.data.publicKey} />

            <Heading.h4>Refresh token</Heading.h4>
            <TextArea maxWidth="1200px" width="100%" value={provider.data.refreshToken} />

            <Heading.h4>Docker Support</Heading.h4>
            <Label>
                <Checkbox checked={docker.enabled} onChange={e => e} />
                Docker enabled: Flag to enable/disable this feature. <b>All other flags are ignored if this is <code>false</code>.</b>
            </Label>
            <Label>
                <Checkbox checked={docker.batch} onChange={e => e} />
                Batch: Flag to enable/disable <code>BATCH</code> <code>Application</code>s
            </Label>
            <Label>
                <Checkbox checked={docker.logs} onChange={e => e} />
                Log: lag to enable/disable the log API
            </Label>
            <Label>
                <Checkbox checked={docker.peers} onChange={e => e} />
                Peers: Flag to enable/disable connection between peering <code>Job</code>s
            </Label>
            <Label>
                <Checkbox checked={docker.terminal} onChange={e => e} />
                Terminal: Flag to enable/disable the interactive terminal API
            </Label>
            <Label>
                <Checkbox checked={docker.vnc} onChange={e => e} />
                VNC: Flag to enable/disable the interactive interface of <code>VNC</code> <code>Application</code>s
            </Label>
            <Label>
                <Checkbox checked={docker.web} onChange={e => e} />
                Web: Flag to enable/disable the interactive interface of <code>WEB</code> <code>Application</code>s
            </Label>

            <Heading.h4>Virtual Machine Support</Heading.h4>
            <Label>
                <Checkbox checked={virtualMachine.enabled} onChange={e => e} />
                Virtual machine enabled: Flag to enable/disable this feature. <b>All other flags are ignored if this is <code>false</code>.</b>
            </Label>
            <Label>
                <Checkbox checked={virtualMachine.logs} onChange={e => e} />
                Logs: Flag to enable/disable the log API
            </Label>
            <Label>
                <Checkbox checked={virtualMachine.terminal} onChange={e => e} />
                Terminal: Flag to enable/disable the interactive terminal API
            </Label>
            <Label>
                <Checkbox checked={virtualMachine.vnc} onChange={e => e} />
                VNC: Flag to enable/disable the VNC API
            </Label>
        </Box>
    } />);
}



export default View;