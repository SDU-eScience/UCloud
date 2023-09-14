import MainContainer from "@/MainContainer/MainContainer";
import * as Heading from "@/ui-components/Heading";
import React, {useEffect} from "react";
import {useParams} from "react-router";
import {RetrieveGroupResponse, retrieveGroup} from "./api";
import {useCloudAPI} from "@/Authentication/DataHook";
import {AppToolLogo} from "./AppToolLogo";
import {Absolute, Box, Flex, Grid, Link} from "@/ui-components";
import {AppCard, ApplicationCardType} from "./Card";
import * as Pages from "./Pages";
import {LargeSearchBox} from "./Overview";
import {ContextSwitcher} from "@/Project/ContextSwitcher";


const ApplicationsGroup: React.FunctionComponent = () => {
    const {id} = useParams<{id: string}>();

    const [appGroup, fetchAppGroup] = useCloudAPI<RetrieveGroupResponse | null>(
        {noop: true},
        null
    );

    useEffect(() => {
        fetchAppGroup(retrieveGroup({id: id}));
    }, [id]);

    if (!appGroup.data) return <>Not found</>;

    return <MainContainer 
        header={
            <>
                <Flex justifyContent="space-between">
                    <Heading.h2>
                        <AppToolLogo name={appGroup.data.group.id.toString()} type="GROUP" size="45px" />
                        {" "}
                        {appGroup.data.group.title}
                    </Heading.h2>
                    <Box ml="auto" mt="30px">
                        <ContextSwitcher />
                    </Box>
                </Flex>
                <Absolute top="0px" left="calc(50% - 200px)">
                    <LargeSearchBox />
                </Absolute>
            </>
        }
        headerSize={120}
        main={
            <>
                <Grid
                    mt="30px"
                    gridGap="25px"
                    gridTemplateColumns={"repeat(auto-fill, 312px)"}
                >
                    {appGroup.data.applications.map(app => (
                        <Link key={app.metadata.name + app.metadata.version} to={Pages.run(app.metadata.name, app.metadata.version)}>
                            <AppCard
                                type={ApplicationCardType.WIDE}
                                title={app.metadata.title} 
                                description={app.metadata.description}
                                logo={app.metadata.name}
                                logoType="APPLICATION"
                            />
                        </Link>
                    ))}
                </Grid>
            </>
        } 
    />;
}

export default ApplicationsGroup;