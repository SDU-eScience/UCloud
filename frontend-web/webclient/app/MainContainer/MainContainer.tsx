import * as React from "react";
import { Flex, Box, Hide } from "ui-components";
import * as Heading from "ui-components/Heading";
import { LoadableContent } from "LoadableContent";
import Spinner from "LoadingIcon/LoadingIcon";

export interface MainContainerProps { sidebar?: React.ReactNode, main?: React.ReactNode, additional?: React.ReactNode, header?: React.ReactNode }
export const MainContainer = ({ sidebar, main, additional, header }: MainContainerProps) => (
    <React.StrictMode>
        <Box mb={16}>
            {header}
        </Box>
        <Flex flexDirection="row">
            <Box width={1}>
                <Hide xl>
                    {sidebar}
                </Hide>
                {main}
            </Box>
            <Hide xs sm md lg width={[0, sidebar != null ? 3 / 16 : 0]}>
                <Box pl="1.0em">
                    {sidebar}
                </Box>
            </Hide>
            {additional}
        </Flex>
    </React.StrictMode>
);

export interface LoadingMainContainerProps<T = any> extends MainContainerProps {
    loadable: LoadableContent<T>
    fallbackHeader?: JSX.Element
    fallbackSidebar?: JSX.Element
}

export function LoadingMainContainer(props: LoadingMainContainerProps): JSX.Element {
    if (!props.loadable.content) {
        const main = !!props.loadable.error ?
            <Heading.h2>{props.loadable.error.statusCode} - {props.loadable.error.errorMessage}</Heading.h2> :
            <Spinner size={24} />
        return <MainContainer
            header={props.fallbackHeader}
            sidebar={props.fallbackSidebar}
            main={main} />;
    } else {
        return <MainContainer {...props} />
    }
}
