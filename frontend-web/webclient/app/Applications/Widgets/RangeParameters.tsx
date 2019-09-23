import * as Types from "Applications";
import {BaseParameter, ParameterProps} from "Applications/Widgets/BaseParameter";
import {Range} from "rc-slider";
import "rc-slider/assets/index.css";
import * as React from "react";

export type RangeRef = React.Component<{}, {bounds: number[]}>;

function RangeParameter(props: RangeParameterProps) {
    const {parameter} = props;

    const [values, setValues] = React.useState<number[]>(() => {
        if (!!props.parameter.defaultValue) {
            const {min, max} = props.parameter.defaultValue;
            return [!!min ? min : props.parameter.min, !!max ? max : props.parameter.max];
        }
        return [props.parameter.min, props.parameter.max];
    });

    if (props.parameterRef.current != null) {
        const {bounds} = props.parameterRef.current.state;
        if (bounds[0] !== values[0] || bounds[1] !== values[1]) {
            setValues(bounds);
        }
    }

    const [first, second] = values;

    return <BaseParameter parameter={props.parameter} onRemove={props.onParamRemove}>
        {first} to {second}
        <Range
            max={parameter.max}
            min={parameter.min}
            included
            ref={props.parameterRef}
            defaultValue={!parameter.defaultValue ? undefined :
                [parameter.defaultValue.min, parameter.defaultValue.max]}
            onChange={setValues}
        />
    </BaseParameter>;
}

interface RangeParameterProps extends ParameterProps {
    parameter: Types.RangeParameter;
    parameterRef: React.RefObject<RangeRef>;
    initialSubmit: boolean;
}

export default RangeParameter;
