import {BaseParameter, ParameterProps} from "Applications/Widgets/BaseParameter";
import {Flex, Select} from "ui-components";
import {InputLabel} from "ui-components/Input";
import * as React from "react";
import * as Types from "Applications";

interface BooleanParameter extends ParameterProps {
    parameter: Types.BooleanParameter
    parameterRef: React.RefObject<HTMLSelectElement>
}

export const BooleanParameter = (props: BooleanParameter) => {
    const defaultValue: boolean | null = props.parameter.defaultValue ? props.parameter.defaultValue.value : null;
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
                >
                    <option/>
                    <option selected={defaultValue === true}>Yes</option>
                    <option selected={defaultValue === false}>No</option>
                </Select>
                {hasUnitName ? <InputLabel rightLabel margin="0">{props.parameter.unitName}</InputLabel> : null}
            </Flex>
        </BaseParameter>
    );
};