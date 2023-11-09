import MainContainer from "@/MainContainer/MainContainer";
import * as Heading from "@/ui-components/Heading";
import React, {useEffect} from "react";
import {useParams} from "react-router";
import {RetrieveGroupResponse, retrieveGroup} from "./api";
import {useCloudAPI} from "@/Authentication/DataHook";
import {AppToolLogo} from "./AppToolLogo";
import {Flex, Grid, Link} from "@/ui-components";
import {AppCard, AppCardStyle, AppCardType} from "./Card";
import * as Pages from "./Pages";
import {AppSearchBox} from "./Search";
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
                <Flex justifyContent="space-between" mt="30px">
                    <Heading.h2>
                        <AppToolLogo name={appGroup.data.group.id.toString()} type="GROUP" size="45px" />
                        {" "}
                        {appGroup.data.group.title}
                    </Heading.h2>
                    <Flex justifyContent="right">
                        <AppSearchBox />
                        <ContextSwitcher />
                    </Flex>
                </Flex>
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
                                style={AppCardStyle.WIDE}
                                title={app.metadata.title} 
                                description={app.metadata.description}
                                logo={app.metadata.name}
                                type={AppCardType.APPLICATION}
                            />
                        </Link>
                    ))}
                </Grid>
            </>
        } 
    />;
}

export default ApplicationsGroup;