import * as React from "react";
import {Box, Absolute, Hide} from "ui-components";
import * as Heading from "ui-components/Heading";
import {LoadableContent} from "LoadableContent";
import Spinner from "LoadingIcon/LoadingIcon";
import styled from "styled-components";
import {ReduxObject, ResponsiveReduxObject} from "DefaultObjects"
import {connect} from 'react-redux'

export interface MainContainerStateProps {
    responsiveState?: ResponsiveReduxObject
}

export interface MainContainerProps extends MainContainerStateProps {
    sidebar?: React.ReactNode,
    sidebarSize?: number,
    main: React.ReactNode,
    additional?: React.ReactNode,
    header?: React.ReactNode,
    headerSize?: number,
}

export const _MainContainer = ({sidebar, main, additional, header, sidebarSize = 240, headerSize = 96, responsiveState}: MainContainerProps) => {
    const leftSidebarSize = responsiveState!.greaterThan.xl ? 190 : 68; //main website sidebar H size
    const topMenuSize = 48; //main website top menu V size
    const pad = 14; //padding unit

    const mainYpad = header ? headerSize : pad;
    const mainXpad = sidebar && responsiveState!.greaterThan.md ? sidebarSize : pad;


    return (
        <React.StrictMode>
            <Box ml={leftSidebarSize} pt={topMenuSize} pb={pad} pl={pad} pr="0">
                {header &&
                <HeaderContainer
                    top={topMenuSize} left="0"
                    py={pad} pl={leftSidebarSize + pad} pr={pad}
                    width={1} height={headerSize}
                    bg="white">
                    {header}
                </HeaderContainer>
                }
                {sidebar &&
                <Hide sm xs md>
                    <SidebarContainer
                        height="100%" pt={topMenuSize + mainYpad}
                        top="0" right="0"
                        px={pad}
                        width={sidebarSize}
                    >
                        {sidebar}
                    </SidebarContainer>
                </Hide>
                }
                <Box pt={mainYpad} pr={mainXpad}>
                    <Hide lg xl xxl>
                        {sidebar}
                    </Hide>
                    {main}
                </Box>
                {additional}
            </Box>
        </React.StrictMode>
    );
};

const mapStateToProps = ({responsive}: ReduxObject): MainContainerStateProps => ({
    responsiveState: responsive
});

export const MainContainer = connect<MainContainerStateProps>(mapStateToProps)(_MainContainer);

export interface LoadingMainContainerProps extends MainContainerProps {
    loading?: boolean
    error?: string
    fallbackHeader?: JSX.Element
    fallbackSidebar?: JSX.Element
}

export const LoadingMainContainer: React.FunctionComponent<LoadingMainContainerProps> = props => {
    let main: React.ReactNode;
    if (props.loading) {
        main = <Spinner size={24}/>;
    } else if (props.error !== undefined) {
        main = <Heading.h2>{props.error}</Heading.h2>;
    } else {
        main = props.main;
    }

    const header = props.loading || props.error !== undefined ? props.fallbackHeader : props.header;
    const sidebar = props.loading || props.error !== undefined ? props.fallbackSidebar : props.sidebar;

    return <MainContainer
        {...props}
        main={main}
        header={header}
        sidebar={sidebar}
    />;
};

export interface LoadableMainContainerProps<T = any> extends MainContainerProps {
    loadable: LoadableContent<T>
    fallbackHeader?: JSX.Element
    fallbackSidebar?: JSX.Element
}

export function LoadableMainContainer(props: LoadableMainContainerProps): JSX.Element {
    if (!props.loadable.content) {
        const main = !!props.loadable.error ?
            <Heading.h2>{props.loadable.error.statusCode} - {props.loadable.error.errorMessage}</Heading.h2> :
            <Spinner size={24}/>
        return <MainContainer
            header={props.fallbackHeader}
            sidebar={props.fallbackSidebar}
            main={main}/>;
    } else {
        return <MainContainer {...props} />
    }
}


const HeaderContainer = styled(Absolute)`
    position: fixed;
    z-index: 50;
`;

const SidebarContainer = styled(Absolute)`
    overflow-y: auto;
    position: fixed;
    z-index: 45;
`;