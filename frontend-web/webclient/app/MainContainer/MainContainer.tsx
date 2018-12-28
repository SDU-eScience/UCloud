import * as React from "react";
import { Flex, Box, Hide, Absolute } from "ui-components";
import * as Heading from "ui-components/Heading";
import { LoadableContent } from "LoadableContent";
import Spinner from "LoadingIcon/LoadingIcon";
import styled from "styled-components";

export interface MainContainerProps { sidebar?: React.ReactNode, main?: React.ReactNode, additional?: React.ReactNode, header?: React.ReactNode, sidebarSize?: number, headerSize?: number }
export const MainContainer = ({ sidebar, main, additional, header, sidebarSize=240, headerSize=96 }: MainContainerProps) => {
    const leftSidebarsize=190; //main webside sidebar H size
    const topMenuSize=48; //main website top manu V size
    const pad=14; //padding unit

    const mainYpad = header ? headerSize : pad ;
    const mainXpad = sidebar ? sidebarSize : pad ;


    return (
        <React.StrictMode>
            {header &&
                <HeaderContainer  
                    top={topMenuSize} left="0" 
                    py={pad} pl={leftSidebarsize+pad} pr={pad} 
                    width={1} height={headerSize}
                    bg="white">
                    {header}
                </HeaderContainer>
            }
            {sidebar &&
                <SidebarContainer 
                    style={{overflowY:"scroll"}}
                    // height={"calc(100% - "+(topMenuSize+mainYpad)+"px)"}
                    // top={topMenuSize+mainYpad} right="0"
                    height="100%" pt={topMenuSize+mainYpad}
                    top="0" right="0"
                    px={pad}
                    width={sidebarSize}
                    bg="white">
                    {sidebar}
                </SidebarContainer>
            }
            <Box pt={mainYpad} pr={mainXpad}>
                {main}
            </Box>
            {additional}
        </React.StrictMode>
    );
}


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


const HeaderContainer = styled(Absolute)`
    position: fixed;
    z-index: 50;
`

const SidebarContainer = styled(Absolute)`
    position: fixed;
    z-index: 45;
`