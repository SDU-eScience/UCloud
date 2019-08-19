import {BaseParameter, ParameterProps} from "Applications/Widgets/BaseParameter";
import * as Types from "Applications";
import * as React from "react";
import Input from "ui-components/Input";
import styled from "styled-components";
import Flex from "ui-components/Flex";
import Text from "ui-components/Text";
import {Link} from "react-router-dom";
import {useState} from "react";
import {useCloudAPI} from "Authentication/DataHook";
import {Application, WithAppMetadata} from "Applications";
import {Page} from "Types";
import {listByName} from "Applications/api";
import {emptyPage} from "DefaultObjects";
import {runApplication, viewApplication} from "Applications/Pages";

interface PeerParameterProps extends ParameterProps {
    parameter: Types.PeerParameter
}

export const PeerParameter: React.FunctionComponent<PeerParameterProps> = props => {
    const [selectedPeer, setSelectedPeer] = useState<string | null>(null);

    const [suggestedApplication] = useCloudAPI<Page<WithAppMetadata>>(
        props.parameter.suggestedApplication ?
            listByName({name: props.parameter.suggestedApplication, itemsPerPage: 50, page: 0}) :
            {noop: true},
        emptyPage
    );

    return <BaseParameter parameter={props.parameter}>
        <Flex>
            <PointerInput placeholder={"No selected peer"}/>
        </Flex>

        {suggestedApplication.data.items.length === 0 ? null :
            <Text>Could not find running job. Would you like to start {" "}
                <Link to={runApplication(suggestedApplication.data.items[0].metadata)}>a new one?</Link></Text>
        }
    </BaseParameter>;
};

const PointerInput = styled(Input)`
    cursor: pointer;
`;