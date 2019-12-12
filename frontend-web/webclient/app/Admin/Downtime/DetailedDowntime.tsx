import {DowntimeList} from "Admin/DowntimeManagement";
import {Client} from "Authentication/HttpClientInstance";
import LoadingIcon from "LoadingIcon/LoadingIcon";
import {MainContainer} from "MainContainer/MainContainer";
import {usePromiseKeeper} from "PromiseKeeper";
import * as React from "react";
import {useParams} from "react-router";
import {Box, Flex, Heading} from "ui-components";
import Markdown from "ui-components/Markdown";
import {displayErrorMessageOrDefault} from "UtilityFunctions";

export function DetailedDowntime() {
    const [downtime, setDowntime] = React.useState({id: -1, start: -1, end: -1, text: ""});
    const [loading, setLoading] = React.useState(false);
    const promises = usePromiseKeeper();
    const {id} = useParams<{id: string}>();
    React.useEffect(() => {
        fetchDowntime(id);
    }, []);


    return (
        <MainContainer
            main={
                loading ? <LoadingIcon size={18} /> : (
                    <Flex justifyContent="center">
                        <Box>
                            <Heading>Downtime</Heading>
                            <DowntimeList downtimes={[downtime]} name="" />
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
        try {
            setLoading(true);
            const result = await promises.makeCancelable(Client.get(`/downtime/getById?id=${downtimeId}`)).promise;
            setDowntime(result.response);
        } catch (err) {
            displayErrorMessageOrDefault(err, "Could not find downtime.");
        } finally {
            setLoading(false);
        }
    }
}
