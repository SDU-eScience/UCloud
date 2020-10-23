import * as React from "react";
import {useHistory, useParams} from "react-router";
import {Box, Markdown, Flex, Text, Link, theme, Button, Input, TextArea, SelectableTextWrapper, SelectableText, ButtonGroup} from "ui-components";
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
import {DatePicker} from "ui-components/DatePicker";
import {DATE_FORMAT} from "Admin/NewsManagement";
import {errorMessageOrDefault} from "UtilityFunctions";
import {snackbarStore} from "Snackbar/SnackbarStore";
import {addStandardDialog} from "UtilityComponents";

function getByIdRequest(payload: {id: string}): APICallParameters<{id: string}> {
    return {
        path: buildQueryString("/news/byId", payload),
        payload
    };
}

export const DetailedNews: React.FC = () => {
    const {id} = useParams<{id: string}>();
    const [newsPost, setParams, params] = useCloudAPI<NewsPost | null, {id: string}>(getByIdRequest({id}), null);
    const [editing, setEditing] = React.useState(false);
    const history = useHistory();
    const isAdmin = Client.userIsAdmin;

    React.useEffect(() => {
        setParams(getByIdRequest({id}));
    }, [id]);

    useTitle("Post: " + newsPost.data?.title ?? "Detailed News");

    if (newsPost.loading) return <MainContainer headerSize={0} main={<Loading size={24} />} />;
    if (newsPost.data == null) return <MainContainer headerSize={0} main="News post not found" />;

    if (editing) return <Editing post={newsPost.data} stopEditing={reload => {
        if (reload) setParams({...params});
        setEditing(false);
    }} />;

    return (
        <MainContainer
            headerSize={0}
            main={
                <Box m={"0 auto"} maxWidth={"1200px"}>
                    <Spacer
                        left={<Heading.h2>{newsPost.data.title}</Heading.h2>}
                        right={isAdmin ?
                            <ButtonGroup width="150px">
                                <Button onClick={() => setEditing(true)}>Edit</Button>
                                <Button color="red" onClick={deletePost}>Delete</Button>
                            </ButtonGroup> : null}
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

    async function deletePost(): Promise<void> {
        addStandardDialog({
            title: "Delete news post?",
            message: `Permanently remove ${newsPost.data?.title}?`,
            onConfirm: async () => {
                try {
                    await Client.delete("/news/delete", {id});
                    snackbarStore.addSuccess("Deleted news post.", false);
                    history.push("/news/list/");
                } catch (err) {
                    snackbarStore.addFailure(errorMessageOrDefault(err, "Failed to deleted news post."), false);
                }
            }
        });
    }
};

function Editing(props: {post: NewsPost; stopEditing: (reload: boolean) => void;}): JSX.Element {
    const titleRef = React.useRef<HTMLInputElement>(null);
    const subtitleRef = React.useRef<HTMLInputElement>(null);
    const [body, setBody] = React.useState(props.post.body);
    const [showFrom, setShowFrom] = React.useState(new Date(props.post.showFrom));
    const [hideFrom, setHideFrom] = React.useState(props.post.hideFrom != null ? new Date(props.post.hideFrom) : null);
    const categoryRef = React.useRef(props.post.category);
    const [preview, setPreview] = React.useState(false);

    return <MainContainer
        headerSize={0}
        main={
            <form onSubmit={onSubmit}>
                <Box m="0 auto" maxWidth="1200px">
                    <Spacer
                        left={
                            <Flex>
                                <Text fontSize={5}>Title: </Text><Input pt={0} pb={0} noBorder defaultValue={props.post.title} ref={titleRef} fontSize={5} />
                            </Flex>}
                        right={<Button type="button" onClick={() => props.stopEditing(false)} color="red">Cancel</Button>}
                    />
                    <Flex><Text fontSize={3}>Subtitle: </Text><Input pt={0} pb={0} noBorder defaultValue={props.post.subtitle} ref={subtitleRef} fontSize={3} /></Flex>
                    <Box>
                        <Text fontSize={1}><Flex>By: <Text mx="6px" bold>{props.post.postedBy}</Text></Flex></Text>
                        <Text fontSize={1}>
                            <Flex>
                                <Text fontSize="18px">Visible from </Text><DatePicker
                                    placeholderText="Show from"
                                    fontSize="18px"
                                    selected={showFrom}
                                    dateFormat={DATE_FORMAT}
                                    onChange={(d: Date) => setShowFrom(d)}
                                    minDate={new Date()}
                                    py={0}
                                    selectsStart
                                    required
                                    showTimeSelect
                                    endDate={hideFrom != null ? new Date(hideFrom) : null}
                                />
                            </Flex>
                        </Text>
                        <Text fontSize={1}>
                            <Flex>
                                <Text fontSize="18px">Hidden from </Text><DatePicker
                                    placeholderText="Hide from"
                                    fontSize="18px"
                                    selected={hideFrom}
                                    py={0}
                                    isClearable
                                    dateFormat={DATE_FORMAT}
                                    onChange={(d: Date | null) => setHideFrom(d)}
                                    minDate={showFrom}
                                    selectsEnd
                                    showTimeSelect
                                    startDate={showFrom}
                                />
                            </Flex>
                        </Text>
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
                    /> : <TextArea style={{marginTop: "6px", marginBottom: "6px"}} width={1} value={body} onChange={e => setBody(e.target.value)} />}
                    <Box mt="auto" />
                    <Button fullWidth>Update post</Button>
                </Box>
            </form>
        }
    />;

    async function onSubmit(e: React.SyntheticEvent): Promise<void> {
        e.preventDefault();

        try {
            await Client.post("/news/update", {
                id: props.post.id,
                title: titleRef.current?.value ?? "",
                subtitle: subtitleRef.current?.value ?? "",
                body,
                showFrom: showFrom.getTime(),
                hideFrom: hideFrom?.getTime() ?? null,
                category: props.post.category
            } as NewsPost);
            props.stopEditing(true);
        } catch (err) {
            snackbarStore.addFailure(errorMessageOrDefault(err, "Failed to update post."), false);
        }
    }
}
