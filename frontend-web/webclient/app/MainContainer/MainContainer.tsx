import * as React from "react";
import { Flex, Box, Hide } from "ui-components";

interface MainContainer { sidebar?: React.ReactNode, main?: React.ReactNode, additional?: React.ReactNode, header?: React.ReactNode }
export const MainContainer = ({ sidebar, main, additional, header }: MainContainer) => (
    <React.StrictMode>
        <Flex flexDirection="row">
            <Box width={[1, sidebar != null ? 13 / 16 : 1]}>
                <Hide lg xl>
                    {sidebar}
                </Hide>
                {header}
                {main}
            </Box>
            <Hide xs sm md width={[0, sidebar != null ? 3 / 16 : 0]}>
                <Box ml="0.4em" mr="0.4em">
                    {sidebar}
                </Box>
            </Hide>
            {additional}
        </Flex>
    </React.StrictMode>
);
