import * as React from "react";
import {useParams} from "react-router";
import {Box, Markdown, Flex, Text, Link, theme} from "ui-components";
import * as Heading from "ui-components/Heading";
import Loading from "LoadingIcon/LoadingIcon";
import {useCloudAPI} from "Authentication/DataHook";
import {buildQueryString} from "Utilities/URIUtilities";
import {MainContainer} from "MainContainer/MainContainer";
import {NewsPost} from "Dashboard/Dashboard";
import {Tag, appColor, hashF} from "Applications/Card";
import {format} from "date-fns/esm";
import {useTitle} from "Navigation/Redux/StatusActions";

function getByIdRequest(payload: {id: string}): APICallParameters<{id: string}> {
    return {
        path: buildQueryString("/news/byId", payload),
        payload
    };
}

export const DetailedNews: React.FC = () => {
    const {id} = useParams<{id: string}>();
    const [newsPost, setParams] = useCloudAPI<NewsPost | null, {id: string}>(getByIdRequest({id}), null);

    React.useEffect(() => {
        setParams(getByIdRequest({id}));
    }, [id]);

    useTitle("Post: " + newsPost.data?.title ?? "Detailed News");

    if (newsPost.loading) return <MainContainer headerSize={0} main={<Loading size={24} />} />;
    if (newsPost.data == null) return <MainContainer headerSize={0} main="News post not found" />;
    return (
        <MainContainer
            headerSize={0}
            main={
                <Box m={"0 auto"} maxWidth={"1200px"}>
                    <Heading.h2>{newsPost.data.title}</Heading.h2>
                    <Heading.h4>{newsPost.data.subtitle}</Heading.h4>
                    <Box>
                        <Text fontSize={1}><Flex>By: <Text mx="6px" bold>{newsPost.data.postedBy}</Text></Flex></Text>
                        <Text fontSize={1}><Flex>Posted {format(newsPost.data.showFrom, "HH:mm dd/MM/yy")}</Flex></Text>
                        <Link to={`/news/list/${newsPost.data.category}`}>
                            <Tag
                                label={newsPost.data.category}
                                bg={theme.appColors[appColor(hashF(newsPost.data.category))][0]}
                            />
                        </Link>
                    </Box>
                    <Markdown
                        source={newsPost.data.body}
                        unwrapDisallowed
                    />
                </Box>
            }
        />
    );
};

