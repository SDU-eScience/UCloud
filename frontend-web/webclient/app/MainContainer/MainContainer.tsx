import Spinner from "@/LoadingIcon/LoadingIcon";
import * as React from "react";
import {Box} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {useGlobal} from "@/Utilities/ReduxHooks";
import {useEffect} from "react";
import {injectStyleSimple} from "@/Unstyled";

export interface MainContainerProps {
    sidebar?: React.ReactNode;
    main: React.ReactNode;
    additional?: React.ReactNode;
    header?: React.ReactNode;
    headerSize?: number;
}

export function MainContainer({
    main,
    additional,
    header,
    headerSize = 64
}: MainContainerProps): JSX.Element {
    const [, setHeaderSize] = useGlobal("mainContainerHeaderSize", headerSize);
    const pad = 16; // padding unit

    const mainYpad = header ? headerSize : pad;
    const mainXpad = pad;

    useEffect(() => {
        setHeaderSize(mainYpad);
    }, [mainYpad]);

    return (
        <Box data-component={"main"} pb={pad} pl={pad} pr="0">
            {header && (
                <Box
                    className={HeaderContainer}
                    pt="8px"
                    height={headerSize}
                    bg="var(--white)"
                >
                    {header}
                </Box>
            )}
            <Box pr={mainXpad}>
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

export function LoadingMainContainer(props: LoadingMainContainerProps): JSX.Element {
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

const HeaderContainer = injectStyleSimple("header-container", `
    position: sticky;
    top: 0;
`);

export default MainContainer;
