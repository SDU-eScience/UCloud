import {ParameterProps} from "Applications/Widgets/BaseParameter";
import {BooleanParameter} from "Applications/Widgets/BooleanParameter";
import {InputDirectoryParameter, InputFileParameter} from "Applications/Widgets/FileParameter";
import {FloatingParameter, IntegerParameter} from "Applications/Widgets/NumberParameter";
import {PeerParameter} from "Applications/Widgets/PeerParameter";
import {SharedFileSystemParameter} from "Applications/Widgets/SharedFileSystemParameter";
import {TextParameter} from "Applications/Widgets/TextParameter";
import * as React from "react";
import * as Types from "../index";
import RangeParameter, {RangeRef} from "./RangeParameters";

export const Parameter = (props: ParameterProps) => {
    let component = (<div />);
    switch (props.parameter.type) {
        case Types.ParameterTypes.InputFile: {
            const p = {...props, parameterRef: props.parameterRef as React.RefObject<HTMLInputElement>};
            component = <InputFileParameter {...p} />;
            break;
        }
        case Types.ParameterTypes.InputDirectory: {
            const p = {...props, parameterRef: props.parameterRef as React.RefObject<HTMLInputElement>};
            component = <InputDirectoryParameter {...p} />;
            break;
        }
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
        case Types.ParameterTypes.Range: {
            component = <RangeParameter
                parameter={props.parameter}
                onParamRemove={props.onParamRemove}
                application={props.application}
                parameterRef={props.parameterRef as React.RefObject<RangeRef>}
                initialSubmit={props.initialSubmit}
            />;
        }
    }

    return component;
};

