import {Downtime, DowntimeList} from "Admin/DowntimeManagement";
import {useAsyncWork} from "Authentication/DataHook";
import {Client} from "Authentication/HttpClientInstance";
import LoadingIcon from "LoadingIcon/LoadingIcon";
import {MainContainer} from "MainContainer/MainContainer";
import * as React from "react";
import {useParams} from "react-router";
import {Box, Flex, Heading} from "ui-components";
import Markdown from "ui-components/Markdown";
import {displayErrorMessageOrDefault} from "UtilityFunctions";

export function DetailedDowntime(): JSX.Element {
    const [downtime, setDowntime] = React.useState<Downtime>({
        id: -1, start: new Date().getTime(), end: new Date().getTime(), text: "`Downtime could not be fetched`"
    });
    const [isLoading, error, startWork] = useAsyncWork();
    const {id} = useParams<{id: string}>();
    React.useEffect(() => {
        fetchDowntime(id);
    }, [id]);

    React.useEffect(() => {
        if (error != null) displayErrorMessageOrDefault(error, "Could not fetch downtime");
    }, [error]);


    return (
        <MainContainer
            main={
                isLoading ? <LoadingIcon size={18} /> : (
                    <Flex justifyContent="center">
                        <Box>
                            <Heading>Downtime</Heading>
                            <DowntimeList downtimes={[downtime]} title="" />
                            <Markdown
                                source={downtime.text}
                                unwrapDisallowed
                            />
                        </Box>
                    </Flex>
                )}
        />
    );



    async function fetchDowntime(downtimeId: string) {
        startWork(async () => {
            const {response} = await Client.get<Downtime>(`/downtime/getById?id=${downtimeId}`);
            setDowntime(response);
        });
    }
}
