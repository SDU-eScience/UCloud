import Spinner from "@/LoadingIcon/LoadingIcon";
import * as React from "react";
import {useSelector} from "react-redux";
import styled from "styled-components";
import {Absolute, Box, Hide} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {ResponsiveReduxObject} from "@/DefaultObjects";
import {useGlobal} from "@/Utilities/ReduxHooks";
import {useEffect} from "react";

export interface MainContainerProps {
    sidebar?: React.ReactNode;
    sidebarSize?: number;
    main: React.ReactNode;
    additional?: React.ReactNode;
    header?: React.ReactNode;
    headerSize?: number;
}

export const MainContainer = ({
    sidebar,
    main,
    additional,
    header,
    sidebarSize = 240,
    headerSize = 96
}: MainContainerProps): JSX.Element => {
    const [, setHeaderSize] = useGlobal("mainContainerHeaderSize", headerSize);
    const responsiveState = useSelector<ReduxObject, ResponsiveReduxObject>(it => it.responsive!);
    const leftSidebarSize = responsiveState!.greaterThan.xl ? 190 : 68; // main website sidebar H size
    const topMenuSize = 48; // main website top menu V size
    const pad = 16; // padding unit

    const mainYpad = header ? headerSize : pad;
    const mainXpad = sidebar && responsiveState!.greaterThan.md ? sidebarSize : pad;

    useEffect(() => {
        setHeaderSize(mainYpad);
    }, [mainYpad]);

    return (
        <React.StrictMode>
            <Box data-component={"main"} backgroundColor="white" ml={leftSidebarSize} pt={topMenuSize} pb={pad} pl={pad} pr="0">
                {header && (
                    <HeaderContainer
                        top={topMenuSize}
                        left="0"
                        py={pad}
                        pl={leftSidebarSize + pad}
                        pr={pad}
                        width={1}
                        height={headerSize}
                        bg="white"
                    >
                        {header}
                    </HeaderContainer>
                )}
                {sidebar && (
                    <Hide sm xs md>
                        <SidebarContainer
                            data-component={"sidebar"}
                            height="calc(100% - var(--termsize, 0px))"
                            data-tag="sidebar"
                            pt={topMenuSize + mainYpad}
                            top="0"
                            right="0"
                            px={pad}
                            width={sidebarSize}
                        >
                            {sidebar}
                        </SidebarContainer>
                    </Hide>
                )}
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
