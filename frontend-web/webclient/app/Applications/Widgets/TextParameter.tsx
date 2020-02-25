import * as Types from "Applications";
import {BaseParameter, ParameterProps} from "Applications/Widgets/BaseParameter";
import * as React from "react";
import Input, {InputLabel} from "ui-components/Input";

interface TextParameterProps extends ParameterProps {
    parameter: Types.TextParameter;
}

export const TextParameter = (props: TextParameterProps): JSX.Element => {
    const placeholder = props.parameter.defaultValue?.value ?? undefined;
    const hasUnitName = !!props.parameter.unitName;
    return (
        <BaseParameter parameter={props.parameter} onRemove={props.onParamRemove}>
            <Input
                showError={props.initialSubmit || props.parameter.optional}
                key={props.parameter.name}
                ref={props.parameterRef as React.RefObject<HTMLInputElement>}
                placeholder={placeholder}
                required={!props.parameter.optional}
                type="text"
                rightLabel={!!props.parameter.unitName}
            />
            {hasUnitName ? <InputLabel rightLabel>{props.parameter.unitName}</InputLabel> : null}
        </BaseParameter>
    );
};
