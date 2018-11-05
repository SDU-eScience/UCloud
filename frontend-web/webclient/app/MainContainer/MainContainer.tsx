import * as React from "react";
import { Flex, Box, Hide } from "ui-components";

interface MainContainer { sidebar?: React.ReactNode, main?: React.ReactNode, additional?: React.ReactNode, header?: React.ReactNode }
export const MainContainer = ({ sidebar, main, additional, header }: MainContainer) => (
    <Flex flexDirection="row">
        <Box width={[1, 13 / 16]}>
            <Hide lg xl>
                {sidebar}
            </Hide>
            {header}
            {main}
        </Box>
        <Hide xs sm md width={3 / 16}>
            {sidebar}
        </Hide>
        {additional}
    </Flex>
);
