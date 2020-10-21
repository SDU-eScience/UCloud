import * as React from "react";
import {useParams} from "react-router";
import {Box, Markdown, Flex, Text, Link, theme, Button, Input, TextArea, SelectableTextWrapper, SelectableText} from "ui-components";
import * as Heading from "ui-components/Heading";
import Loading from "LoadingIcon/LoadingIcon";
import {useCloudAPI} from "Authentication/DataHook";
import {buildQueryString} from "Utilities/URIUtilities";
import {MainContainer} from "MainContainer/MainContainer";
import {NewsPost} from "Dashboard/Dashboard";
import {Tag, appColor, hashF} from "Applications/Card";
import {format} from "date-fns/esm";
import {useTitle} from "Navigation/Redux/StatusActions";
import {Client} from "Authentication/HttpClientInstance";
import {Spacer} from "ui-components/Spacer";

function getByIdRequest(payload: {id: string}): APICallParameters<{id: string}> {
    return {
        path: buildQueryString("/news/byId", payload),
        payload
    };
}

export const DetailedNews: React.FC = () => {
    const {id} = useParams<{id: string}>();
    const [newsPost, setParams] = useCloudAPI<NewsPost | null, {id: string}>(getByIdRequest({id}), null);
    const [editing, setEditing] = React.useState(false);
    const isAdmin = Client.userIsAdmin;

    React.useEffect(() => {
        setParams(getByIdRequest({id}));
    }, [id]);

    useTitle("Post: " + newsPost.data?.title ?? "Detailed News");

    if (newsPost.loading) return <MainContainer headerSize={0} main={<Loading size={24} />} />;
    if (newsPost.data == null) return <MainContainer headerSize={0} main="News post not found" />;

    if (editing) return <Editing post={newsPost.data} stopEditing={() => setEditing(false)} />;

    return (
        <MainContainer
            headerSize={0}
            main={
                <Box m={"0 auto"} maxWidth={"1200px"}>
                    <Spacer
                        left={<Heading.h2>{newsPost.data.title}</Heading.h2>}
                        right={isAdmin ? <Button onClick={() => setEditing(true)}>Edit post</Button> : null}
                    />
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

function Editing(props: {post: NewsPost; stopEditing: () => void;}): JSX.Element {
    const titleRef = React.useRef(null);
    const subtitleRef = React.useRef(null);
    const [body, setBody] = React.useState(props.post.body);
    const showFromRef = React.useRef(null);
    const hideFromRef = React.useRef(null);
    const categoryRef = React.useRef(null);
    const [preview, setPreview] = React.useState(false);

    return <MainContainer
        headerSize={0}
        main={
            <Box m="0 auto" maxWidth="1200px">
                <Flex><Text fontSize={5}>Title: </Text><Input pt={0} pb={0} noBorder defaultValue={props.post.title} ref={titleRef} fontSize={5} /></Flex>
                <Flex><Text fontSize={3}>Subtitle: </Text><Input pt={0} pb={0} noBorder defaultValue={props.post.subtitle} ref={subtitleRef} fontSize={3} /></Flex>
                <Box>
                    <Text fontSize={1}><Flex>By: <Text mx="6px" bold>{props.post.postedBy}</Text></Flex></Text>
                    <Text fontSize={1}><Flex>Visible from {format(props.post.showFrom, "HH:mm dd/MM/yy")}</Flex></Text>
                    <Text fontSize={1}><Flex>Hidden from {props.post.hideFrom ? format(props.post.hideFrom, "HH:mm dd/MM/yy") : "NOT SET"}</Flex></Text>
                    <Tag
                        label={props.post.category}
                        bg={theme.appColors[appColor(hashF(props.post.category))][0]}
                    />
                </Box>
                <SelectableTextWrapper>
                    <SelectableText onClick={() => setPreview(false)} selected={preview === false}>Edit</SelectableText>
                    <SelectableText ml="1em" onClick={() => setPreview(true)} selected={preview}> Preview</SelectableText>
                </SelectableTextWrapper>
                {preview ? <Markdown
                    source={body}
                    unwrapDisallowed
                /> : <TextArea width={1} value={body} onChange={e => setBody(e.target.value)} />}
                <Box mt="auto" />
                <Button fullWidth>Update post</Button>
            </Box>
        }
    />;
}
