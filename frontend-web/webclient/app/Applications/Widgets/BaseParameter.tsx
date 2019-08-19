import * as React from "react";
import * as Types from "Applications";
import {Box, Flex, Icon, Label, Markdown, Text} from "ui-components";
import {WithAppMetadata} from "Applications";
import {WithAppInvocation} from "Applications";

export interface ParameterProps {
    initialSubmit: boolean
    parameter: Types.ApplicationParameter
    parameterRef: React.RefObject<HTMLInputElement | HTMLSelectElement>
    onParamRemove?: () => void
    application: WithAppMetadata & WithAppInvocation
}

export const BaseParameter: React.FunctionComponent<{ parameter: Types.ApplicationParameter, onRemove?: () => void }> = ({parameter, children, onRemove}) => (
    <>
        <Label fontSize={1} htmlFor={parameter.name}>
            <Flex>
                <Flex>
                    {parameter.title}
                    {parameter.optional ? null : <Text ml="4px" bold color="red">*</Text>}
                </Flex>
                {!parameter.optional || !onRemove ? null :
                    <>
                        <Box ml="auto"/>
                        <Text cursor="pointer" mb="4px" onClick={onRemove}>
                            Remove
                            <Icon ml="6px" size={16} name="close"/>
                        </Text>
                    </>
                }
            </Flex>
        </Label>
        {children}
        <Markdown source={parameter.description}/>
    </>
);