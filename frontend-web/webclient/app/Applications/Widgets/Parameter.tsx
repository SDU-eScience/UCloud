import * as React from "react";
import * as Types from "../index";
import {Box} from "ui-components";
import {ParameterProps} from "Applications/Widgets/BaseParameter";
import {FloatingParameter, IntegerParameter} from "Applications/Widgets/NumberParameter";
import {BooleanParameter} from "Applications/Widgets/BooleanParameter";
import {TextParameter} from "Applications/Widgets/TextParameter";
import {InputDirectoryParameter, InputFileParameter} from "Applications/Widgets/FileParameter";
import {PeerParameter} from "Applications/Widgets/PeerParameter";
import {SharedFileSystemParameter} from "Applications/Widgets/SharedFileSystemParameter";

export const Parameter = (props: ParameterProps) => {
    let component = (<div/>);
    switch (props.parameter.type) {
        case Types.ParameterTypes.InputFile:
            component = <InputFileParameter {...props} />;
            break;
        case Types.ParameterTypes.InputDirectory:
            component = <InputDirectoryParameter {...props} />;
            break;
        case Types.ParameterTypes.Integer:
            component = <IntegerParameter
                onParamRemove={props.onParamRemove}
                initialSubmit={props.initialSubmit}
                parameterRef={props.parameterRef as React.RefObject<HTMLInputElement>}
                parameter={props.parameter}
                application={props.application}
            />;
            break;
        case Types.ParameterTypes.FloatingPoint:
            component = <FloatingParameter
                onParamRemove={props.onParamRemove}
                initialSubmit={props.initialSubmit}
                parameterRef={props.parameterRef as React.RefObject<HTMLInputElement>}
                parameter={props.parameter}
                application={props.application}
            />;
            break;
        case Types.ParameterTypes.Text:
            component = <TextParameter
                onParamRemove={props.onParamRemove}
                initialSubmit={props.initialSubmit}
                parameterRef={props.parameterRef}
                parameter={props.parameter}
                application={props.application}
            />;
            break;
        case Types.ParameterTypes.Boolean:
            component = <BooleanParameter
                onParamRemove={props.onParamRemove}
                initialSubmit={props.initialSubmit}
                parameterRef={props.parameterRef as React.RefObject<HTMLSelectElement>}
                parameter={props.parameter}
                application={props.application}
            />;
            break;
        case Types.ParameterTypes.Peer:
            component = <PeerParameter
                parameter={props.parameter}
                initialSubmit={props.initialSubmit}
                parameterRef={props.parameterRef}
                application={props.application}
            />;
            break;
        case Types.ParameterTypes.SharedFileSystem:
            component = <SharedFileSystemParameter
                parameter={props.parameter}
                initialSubmit={props.initialSubmit}
                parameterRef={props.parameterRef}
                application={props.application}
            />;
            break;
    }

    return component;
};

