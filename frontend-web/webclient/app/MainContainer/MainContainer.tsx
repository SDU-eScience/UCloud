import Spinner from "@/LoadingIcon/LoadingIcon";
import * as React from "react";
import styled from "styled-components";
import {Box} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import {useGlobal} from "@/Utilities/ReduxHooks";
import {useEffect} from "react";

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
    headerSize = 64
}: MainContainerProps): JSX.Element => {
    const [, setHeaderSize] = useGlobal("mainContainerHeaderSize", headerSize);
    const pad = 16; // padding unit

    const mainYpad = header ? headerSize : pad;
    const mainXpad = pad;

    useEffect(() => {
        setHeaderSize(mainYpad);
    }, [mainYpad]);

    return (
        <Box data-component={"main"} backgroundColor="var(--white)" pb={pad} pl={pad} pr="0">
            {header && (
                <HeaderContainer
                    height={headerSize}
                    bg="var(--white)"
                >
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

const HeaderContainer = styled(Box)`
    position: sticky;
    padding-top: 8px;
    top: 0;
`;

export default MainContainer;
