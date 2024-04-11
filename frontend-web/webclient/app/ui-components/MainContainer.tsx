import Spinner from "@/LoadingIcon/LoadingIcon";
import * as React from "react";
import Box from "./Box";
import * as Heading from "@/ui-components/Heading";
import {useEffect} from "react";
import {injectStyleSimple} from "@/Unstyled";

export interface MainContainerProps {
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
    const pad = 16; // padding unit

    const mainYpad = header ? headerSize : pad;
    const mainXpad = pad;

    useEffect(() => {
        // HACK(Dan): Under no circumstances should the body be scrolling if we are mounting this element.
        document.body.style.overflow = "hidden";
    }, [mainYpad]);

    return (
        <Box zIndex={-1} data-component={"main"} pb={pad} pl={pad} pr="0" marginX="auto" maxWidth={"1400px"} pt={"13px"}>
            {header && (
                <Box
                    className={HeaderContainer}
                    pt="8px"
                    zIndex={1}
                    height={headerSize}
                    bg="var(--backgroundDefault)"
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
}

export interface LoadingMainContainerProps extends MainContainerProps {
    loading?: boolean;
    error?: string;
    fallbackHeader?: JSX.Element;
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

    return (
        <MainContainer
            {...props}
            main={main}
            header={header}
        />
    );
}

const HeaderContainer = injectStyleSimple("header-container", `
    position: sticky;
    top: 0;
    margin-left: -10px;
`);

export default MainContainer;
