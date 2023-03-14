import Spinner from "@/LoadingIcon/LoadingIcon";
import * as React from "react";
import styled from "styled-components";
import {Absolute, Box, Hide} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {useGlobal} from "@/Utilities/ReduxHooks";
import {useEffect} from "react";
import {UtilityBar} from "@/Playground/Playground";

export interface MainContainerProps {
    sidebar?: React.ReactNode;
    main: React.ReactNode;
    additional?: React.ReactNode;
    header?: React.ReactNode;
    headerSize?: number;
}

export const MainContainer = ({
    main,
    additional,
    header,
    headerSize = 96
}: MainContainerProps): JSX.Element => {
    const [, setHeaderSize] = useGlobal("mainContainerHeaderSize", headerSize);
    const leftSidebarSize = getComputedStyle(document.documentElement).getPropertyValue("--sidebarWidth");
    const pad = 16; // padding unit

    const mainYpad = header ? headerSize : pad;
    const mainXpad = pad;

    useEffect(() => {
        setHeaderSize(mainYpad);
    }, [mainYpad]);

    return (
        <Box data-component={"main"} backgroundColor="white" pb={pad} pl={pad} pr="0">
            {header && (
                <HeaderContainer
                    left="0"
                    py={pad}
                    pl={`calc(${leftSidebarSize} + ${pad}px)`}
                    pr={pad}
                    width={1}
                    height={headerSize}
                    bg="white"
                >
                    <UtilityBar searchEnabled />
                    {header}
                </HeaderContainer>
            )}
            <Box pt={mainYpad} pr={mainXpad}>
                {main}
            </Box>
            {additional}
        </Box>
    );
};

export interface LoadingMainContainerProps extends MainContainerProps {
    loading?: boolean;
    error?: string;
    fallbackHeader?: JSX.Element;
    fallbackSidebar?: JSX.Element;
}

export const LoadingMainContainer: React.FunctionComponent<LoadingMainContainerProps> = props => {
    let main: React.ReactNode;
    if (props.loading) {
        main = <Spinner />;
    } else if (props.error !== undefined) {
        main = <Heading.h2>{props.error}</Heading.h2>;
    } else {
        main = props.main;
    }

    const header = props.loading || props.error !== undefined ? props.fallbackHeader : props.header;
    const sidebar = props.loading || props.error !== undefined ? props.fallbackSidebar : props.sidebar;

    return (
        <MainContainer
            {...props}
            main={main}
            header={header}
            sidebar={sidebar}
        />
    );
};

const HeaderContainer = styled(Absolute)`
    position: fixed;
    z-index: 50;
`;

const SidebarContainer = styled(Absolute)`
    overflow-y: auto;
    position: fixed;
    z-index: 45;
`;

export default MainContainer;
