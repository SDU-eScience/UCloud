import Spinner from "@/LoadingIcon/LoadingIcon";
import * as React from "react";
import {Box} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {useGlobal} from "@/Utilities/ReduxHooks";
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
    const [, setHeaderSize] = useGlobal("mainContainerHeaderSize", headerSize);
    const pad = 16; // padding unit

    const mainYpad = header ? headerSize : pad;
    const mainXpad = pad;

    useEffect(() => {
        setHeaderSize(mainYpad);
    }, [mainYpad]);

    return (
        <Box zIndex={-1} data-component={"main"} pb={pad} pl={pad} pr="0" marginX="auto" maxWidth={"2000px"} pt={"13px"}>
            {header && (
                <Box
                    className={HeaderContainer}
                    pt="8px"
                    zIndex={1}
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
};

const HeaderContainer = injectStyleSimple("header-container", `
    position: sticky;
    top: 0;
    margin-left: -10px;
`);

export default MainContainer;
