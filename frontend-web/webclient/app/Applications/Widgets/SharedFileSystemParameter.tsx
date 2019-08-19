import * as React from "react";
import {BaseParameter, ParameterProps} from "Applications/Widgets/BaseParameter";
import * as Types from "Applications";
import Management from "Applications/FileSystems/Management";

interface SharedFileSystemParameter extends ParameterProps {
    parameter: Types.SharedFileSystemParameter
}

export const SharedFileSystemParameter: React.FunctionComponent<SharedFileSystemParameter> = (props) => {
    const {parameter, parameterRef} = props;
    if (parameter.fsType === "EPHEMERAL") return null;
    return <BaseParameter parameter={parameter}>
        <Management
            parameterRef={parameterRef as React.RefObject<HTMLInputElement>}
            application={props.application}
            mountLocation={parameter.mountLocation ? parameter.mountLocation : "TODO"}
        />
    </BaseParameter>;
};