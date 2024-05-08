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
}: MainContainerProps): React.ReactNode {
    const pad = 16; // padding unit

    const mainYpad = header ? headerSize : pad;

    useEffect(() => {
        // HACK(Dan): Under no circumstances should the body be scrolling if we are mounting this element.
        document.body.style.overflow = "hidden";
    }, [mainYpad]);

    return (
        <Box zIndex={-1} data-component={"main"} p={pad} marginX="auto" maxWidth={"1400px"}>
            {header && (
                <Box
                    className={HeaderContainer}
                    zIndex={1}
                    height={headerSize}
                    bg="var(--backgroundDefault)"
                >
                    {header}
                </Box>
            )}
            {main}
            {additional}
        </Box>
    );
}

export interface LoadingMainContainerProps extends MainContainerProps {
    loading?: boolean;
    error?: string;
    fallbackHeader?: React.ReactNode;
}

export function LoadingMainContainer(props: LoadingMainContainerProps): React.ReactNode {
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
