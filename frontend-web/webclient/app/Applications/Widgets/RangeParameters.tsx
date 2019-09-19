import * as Types from "Applications";
import {BaseParameter, ParameterProps} from "Applications/Widgets/BaseParameter";
import {Range} from "rc-slider";
import "rc-slider/assets/index.css";
import * as React from "react";
import {TextSpan} from "ui-components/Text";

export type RangeRef = React.Component<{}, {bounds: [number, number]}>;

function RangeParameter(props: RangeParameterProps) {
    const {parameter} = props;

    const [values, setValues] = React.useState(() => {
        if (!!props.parameter.defaultValue) {
            const {min, max} = props.parameter.defaultValue;
            return [!!min ? min : props.parameter.min, !!max ? max : props.parameter.max];
        }
        return [props.parameter.min, props.parameter.max];
    });

    if (ref.current != null) {
        const {bounds} = ref.current.state;
        if (bounds[0] !== values[0] || bounds[1] !== values[1]) {
            setValues(bounds);
        }
    }

    return <BaseParameter parameter={props.parameter} onRemove={props.onParamRemove}>
        <TextSpan>{values[0]} to {values[1]}</TextSpan>
        <Range
            max={parameter.max}
            min={parameter.min}
            included
            ref={props.parameterRef}
            value={values}
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
