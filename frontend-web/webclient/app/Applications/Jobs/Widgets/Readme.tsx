import * as React from "react";
import {useLayoutEffect} from "react";
import {Box, Flex, Markdown} from "@/ui-components";
import {widgetId, WidgetProps, WidgetSetter, WidgetValidator} from "@/Applications/Jobs/Widgets/index";
import {ApplicationParameterNS} from "@/Applications/AppStoreApi";

interface ReadmeProps extends WidgetProps {
    parameter: ApplicationParameterNS.Readme;
}

export const ReadmeParameter: React.FunctionComponent<ReadmeProps> = props => {
    return (<Flex flexDirection={"column"}>
        <Markdown>{props.parameter.description}</Markdown>
    </Flex>);
}
