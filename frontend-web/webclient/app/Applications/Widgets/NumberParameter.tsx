import * as React from "react";
import {Flex} from "ui-components";
import Input, {InputLabel} from "ui-components/Input";
import {BaseParameter, ParameterProps} from "Applications/Widgets/BaseParameter";
import * as Types from "Applications";

const GenericNumberParameter = (props: NumberParameterProps) => {
    const {parameter, parameterRef} = props;
    const optSliderRef = React.useRef<HTMLInputElement>(null);
    const hasUnitName = !!props.parameter.unitName;
    if (optSliderRef.current && parameterRef.current && parameterRef.current.value !== optSliderRef.current!.value)
        optSliderRef.current.value = parameterRef.current.value;

    let baseField = (
        <Flex>
            <Input
                showError={props.initialSubmit || props.parameter.optional}
                required={!props.parameter.optional}
                name={props.parameter.name}
                type="number"
                step="any"
                ref={parameterRef}
                key={parameter.name}
                onChange={e => {
                    if (optSliderRef.current) optSliderRef.current.value = e.target.value
                }}
                max={parameter.max != null ? parameter.max : undefined}
                min={parameter.min != null ? parameter.min : undefined}
                rightLabel={hasUnitName}
            />
            {hasUnitName ? <InputLabel rightLabel>{props.parameter.unitName}</InputLabel> : null}
        </Flex>
    );

    let slider: React.ReactNode = null;
    if (parameter.min !== null && parameter.max !== null) {
        slider = (
            <Input
                showError={props.initialSubmit || props.parameter.optional}
                key={`${parameter.name}-slider`}
                mt="2px"
                noBorder
                ref={optSliderRef}
                min={parameter.min}
                max={parameter.max}
                step={parameter.step!}
                onChange={e => parameterRef.current!.value = e.target.value}
                type="range"
            />
        );
    }

    return (
        <BaseParameter parameter={props.parameter} onRemove={props.onParamRemove}>
            {baseField}
            {slider}
        </BaseParameter>
    );
};

interface NumberParameterProps extends ParameterProps {
    parameter: Types.NumberParameter
    parameterRef: React.RefObject<HTMLInputElement>
    initialSubmit: boolean
}

export const IntegerParameter = (props: NumberParameterProps) => {
    let childProps = {...props};
    return <GenericNumberParameter {...childProps} />;
};
export const FloatingParameter = (props: NumberParameterProps) => {
    let childProps = {...props};
    return <GenericNumberParameter {...childProps} />;
};