import {ToolReference} from "Applications";
import {listTools} from "Applications/api";
import {SmallAppToolCard} from "Applications/Studio/SmallAppToolCard";
import {useCloudAPI} from "Authentication/DataHook";
import {Cloud} from "Authentication/SDUCloudObject";
import {emptyPage} from "DefaultObjects";
import {MainContainer} from "MainContainer/MainContainer";
import * as Pagination from "Pagination";
import * as React from "react";
import {Page} from "Types";
import Box from "ui-components/Box";
import Button from "ui-components/Button";
import Flex from "ui-components/Flex";
import * as Heading from "ui-components/Heading";
import Truncate from "ui-components/Truncate";
import VerticalButtonGroup from "ui-components/VerticalButtonGroup";
import {AppToolLogo} from "../AppToolLogo";

const Studio: React.FunctionComponent = props => {
    const [tools, setToolParameters, toolParameters] =
        useCloudAPI<Page<ToolReference>>(listTools({page: 0, itemsPerPage: 50}), emptyPage);

    if (Cloud.userRole !== "ADMIN") return null;

    return <MainContainer
        header={
            <Heading.h1>Application Studio</Heading.h1>
        }

        sidebar={
            <VerticalButtonGroup>
                <Button type={"button"}>Upload Application</Button>
                <Button type={"button"}>Upload Tool</Button>
            </VerticalButtonGroup>
        }

        main={
            <Pagination.List
                loading={tools.loading}
                page={tools.data}
                onPageChanged={page => {
                    setToolParameters(listTools({...toolParameters.parameters, page}));
                }}
                pageRenderer={page => {
                    return <Flex flexWrap={"wrap"} justifyContent={"center"}>
                        {page.items.map(tool =>
                            <SmallAppToolCard key={tool.description.info.name} to={`/applications/studio/t/${tool.description.info.name}`}>
                                <Flex>
                                    <AppToolLogo type={"TOOL"} name={tool.description.info.name}/>
                                    <Box ml={8}>
                                        <Truncate width={300} cursor={"pointer"}>
                                            <b>
                                                {tool.description.title}
                                            </b>
                                        </Truncate>
                                        <Box cursor={"pointer"}>{tool.description.info.name}</Box>
                                    </Box>
                                </Flex>
                            </SmallAppToolCard>
                        )}
                    </Flex>;
                }}
            />
        }
    />;
};

export default Studio;
