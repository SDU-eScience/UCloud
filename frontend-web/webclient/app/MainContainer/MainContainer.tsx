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
    const leftSidebarSize = getComputedStyle(document.documentElement).getPropertyValue("--sidebarWidth");
    const pad = 16; // padding unit

    const mainYpad = header ? headerSize : pad;
    const mainXpad = sidebar && responsiveState!.greaterThan.md ? sidebarSize : pad;

    useEffect(() => {
        setHeaderSize(mainYpad);
    }, [mainYpad]);

    return (
        <Box data-component={"main"} backgroundColor="white" ml={leftSidebarSize} pb={pad} pl={pad} pr="0">
            {header && (
                <HeaderContainer
                    left="0"
                    py={pad}
                    pl={`calc(${leftSidebarSize} + ${pad}px`}
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
                    <SidebarScroll>
                        <SidebarContainer
                            data-component={"sidebar"}
                            height="calc(100% - var(--termsize, 0px))"
                            data-tag="sidebar"
                            pt={mainYpad}
                            top="0"
                            right="0"
                            px={pad}
                            width={"240px"}
                        >
                            {sidebar}
                        </SidebarContainer>
                    </SidebarScroll>
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

/* abandon hope all ye who enter here */
function SidebarScroll(props: {children: React.ReactNode}): JSX.Element {
    React.useEffect(() => {
        const element = document.querySelector<HTMLInputElement>(
            `div[data-component="sidebar"]`
        );
        if (!element) return;
        const old = element.onwheel;
        element.onwheel = scrollMainContainer;
        return () => {
            element.onwheel = old;
        }
    }, []);

    function scrollMainContainer(this: GlobalEventHandlers, e: WheelEvent) {
        var elmnt = document.querySelector<HTMLInputElement>(
            `div[data-component="router-wrapper"]`
        );
        if (!elmnt) return;
        elmnt.scrollBy(0, e.deltaY);
    }
    return <Box>{props.children}</Box>;
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

export default MainContainer;
