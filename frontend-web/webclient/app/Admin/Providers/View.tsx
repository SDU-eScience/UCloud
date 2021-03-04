import {useRouteMatch} from "react-router";
import * as React from "react";
import {
    Box,
    Label,
    Checkbox,
    TextArea,
} from "ui-components";
import Error from "ui-components/Error";
import * as Heading from "ui-components/Heading";
import {useCloudAPI} from "Authentication/DataHook";
import * as UCloud from "UCloud";
import LoadingSpinner from "LoadingIcon/LoadingIcon";
import MainContainer from "MainContainer/MainContainer";

function View(): JSX.Element | null {
    const match = useRouteMatch<{ id: string }>();
    const {id} = match.params;
    const [provider, fetchProvider] = useCloudAPI<UCloud.provider.Provider | null>(
        UCloud.provider.providers.retrieve({id}),
        null
    );

    if (provider.loading) return <MainContainer main={<LoadingSpinner/>}/>;
    if (provider.data == null) return null;

    const {https} = provider.data.specification;

    return (<MainContainer main={
        <Box width="650px" mx="auto">
            <Error error={provider.error?.why}/>
            <Heading.h3>{id}</Heading.h3>
            <Box>
                <b>Created by:</b> {provider.data.owner.createdBy}
            </Box>
            <Box>
                <b>Domain: </b> {provider.data.specification.domain}
            </Box>

            <Label>
                <Checkbox checked={https} onChange={e => e}/>
                Uses HTTPS
            </Label>

            <Heading.h4>Certificate</Heading.h4>
            <TextArea maxWidth="1200px" width="100%" value={provider.data.publicKey} rows={10}/>

            <Heading.h4>Refresh token</Heading.h4>
            <TextArea maxWidth="1200px" width="100%" value={provider.data.refreshToken} rows={2}/>
        </Box>
    }/>);
}

export default View;
