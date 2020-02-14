import * as React from "react";
import * as Types from "Applications";
import {ParameterProps, BaseParameter} from "./BaseParameter";
import {Flex, Select} from "ui-components";
import {InputLabel} from "ui-components/Input";

interface TextParameterProps extends ParameterProps {
    parameter: Types.EnumerationParameter;
    parameterRef: React.RefObject<HTMLSelectElement>;
}

export function EnumerationParameter(props: TextParameterProps): JSX.Element {
    const defaultValue = props.parameter.default?.value;
    const hasUnitName = !!props.parameter.unitName;
    return (
        <BaseParameter parameter={props.parameter} onRemove={props.onParamRemove}>
            <Flex>
                <Select
                    showError={props.initialSubmit || props.parameter.optional}
                    id="select"
                    selectRef={props.parameterRef}
                    key={props.parameter.name}
                    rightLabel={hasUnitName}
                    required={!props.parameter.optional}
                    defaultValue={defaultValue}
                >
                    {props.parameter.options.map(opt => (
                        <option
                            key={opt.name}
                            value={opt.value}
                        >
                            {opt.name}
                        </option>
                    ))}
                </Select>
                {hasUnitName ? <InputLabel rightLabel margin="0">{props.parameter.unitName}</InputLabel> : null}
            </Flex>
        </BaseParameter>
    );
}
