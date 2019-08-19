import {BaseParameter, ParameterProps} from "Applications/Widgets/BaseParameter";
import * as Types from "Applications";
import * as React from "react";

interface PeerParameterProps extends ParameterProps {
    parameter: Types.PeerParameter
}

export const PeerParameter: React.FunctionComponent<PeerParameterProps> = props => {
    return <BaseParameter parameter={props.parameter} />;
};