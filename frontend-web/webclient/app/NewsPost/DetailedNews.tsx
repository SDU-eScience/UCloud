import * as React from "react";
import {useNavigate, useParams} from "react-router";
import {Box, Markdown, Flex, Text, Link, Button, Input, TextArea, ButtonGroup, Label} from "@/ui-components";
import * as Heading from "@/ui-components/Heading";
import Loading from "@/LoadingIcon/LoadingIcon";
import {useCloudAPI} from "@/Authentication/DataHook";
import {buildQueryString} from "@/Utilities/URIUtilities";
import {MainContainer} from "@/ui-components/MainContainer";
import {NewsPost} from "@/Dashboard/Dashboard";
import {Tag} from "@/Applications/Card";
import {format} from "date-fns/esm";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import {Client} from "@/Authentication/HttpClientInstance";
import {Spacer} from "@/ui-components/Spacer";
import {DatePicker} from "@/ui-components/DatePicker";
import {Categories, DATE_FORMAT} from "@/Admin/NewsManagement";
import {capitalized, errorMessageOrDefault} from "@/UtilityFunctions";
import {snackbarStore} from "@/Snackbar/SnackbarStore";
import {addStandardDialog} from "@/UtilityComponents";
import Fuse from "fuse.js";
import {colorFromTitle} from "@/ui-components/theme";
import {Toggle} from "@/ui-components/Toggle";

function getByIdRequest(payload: {id: string}): APICallParameters<{id: string}> {
    return {
        path: buildQueryString("/news/byId", payload),
        payload
    };
}

export const DetailedNews: React.FC = () => {
    const id = useParams<{id: string}>().id!;
    const [newsPost, setParams, params] = useCloudAPI<NewsPost | null, {id: string}>(getByIdRequest({id}), null);
    const [editing, setEditing] = React.useState(false);
    const navigate = useNavigate();
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
                        <Text><Flex>By: <Text mx="6px" bold>{newsPost.data.postedBy}</Text></Flex></Text>
                        <Text><Flex>Posted {format(newsPost.data.showFrom, "HH:mm dd/MM/yy")}</Flex></Text>
                        <Link to={`/news/list/${newsPost.data.category}`}>
                            <Tag
                                label={newsPost.data.category}
                                bg={colorFromTitle(newsPost.data.category)}
                            />
                        </Link>
                    </Box>
                    <Markdown unwrapDisallowed>
                        {newsPost.data.body}
                    </Markdown>
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
                    navigate("/news/list/");
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
    const categoryRef = React.useRef<HTMLInputElement>(null);
    const [preview, setPreview] = React.useState(false);

    const [categories] = useCloudAPI<string[]>({
        path: "/news/listCategories"
    }, []);

    const [fuse, setFuse] = React.useState(new Fuse([] as string[], {
        shouldSort: true,
        threshold: 0.2,
        location: 0,
        distance: 100,
        minMatchCharLength: 1,
    }));

    React.useEffect(() => {
        setFuse(new Fuse(categories.data, {
            shouldSort: true,
            threshold: 0.2,
            location: 0,
            distance: 100,
            minMatchCharLength: 1,
        }));
    }, [categories.data, categories.data.length]);

    const [results, setResults] = React.useState<string[]>([]);
    const onKeyUp = React.useCallback((e) => {
        const category = e.target?.value ?? "";
        setResults(fuse.search(category).map(it => capitalized(it.item)));
    }, [categoryRef.current, categories.data, categories.data.length, fuse]);

    return <MainContainer
        headerSize={0}
        main={
            <form onSubmit={onSubmit}>
                <Box m="0 auto" maxWidth="1200px">
                    <Spacer mb="12px" left={null} right={
                        <Button type="button" onClick={() => props.stopEditing(false)} color="red">Cancel</Button>
                    } />
                    <Label>
                        Title
                        <Input pt={0} pb={0} noBorder defaultValue={props.post.title} inputRef={titleRef} />
                    </Label>
                    <Label>
                        Subtitle
                        <Input pt={0} pb={0} noBorder defaultValue={props.post.subtitle} inputRef={subtitleRef} />
                    </Label>
                    <Box>
                        <Text my="8px"><Flex>By: <Text mx="6px" bold>{props.post.postedBy}</Text></Flex></Text>
                        <Label>
                            Visible from
                            <DatePicker
                                placeholderText="Show from"
                                selected={showFrom}
                                dateFormat={DATE_FORMAT}
                                onChange={d => setShowFrom(d as Date)}
                                minDate={new Date()}
                                py={0}
                                selectsStart
                                required
                                showTimeSelect
                                endDate={hideFrom != null ? new Date(hideFrom) : null}
                            />
                        </Label>
                        <Label>
                            Hidden from
                            <DatePicker
                                placeholderText="Hide from"
                                selected={hideFrom}
                                py={0}
                                isClearable
                                dateFormat={DATE_FORMAT}
                                onChange={d => setHideFrom(d as (Date | null))}
                                minDate={showFrom}
                                selectsEnd
                                showTimeSelect
                                startDate={showFrom}
                            />
                        </Label>
                        <Input
                            width={"100%"}
                            autoComplete="off"
                            onKeyUp={onKeyUp}
                            defaultValue={props.post.category}
                            my="3px"
                            placeholder="Category"
                            required
                            inputRef={categoryRef}
                        />
                        <Categories categories={results} onSelect={category => {
                            categoryRef.current!.value = category;
                            setResults([]);
                        }} />
                    </Box>
                    <Flex my="12px">
                        <Toggle
                            checked={preview}
                            onChange={() => setPreview(p => !p)}
                        />
                        <Text fontSize="16px" ml="12px">Preview post</Text>
                    </Flex>
                    {preview ? <Markdown unwrapDisallowed>{body}</Markdown> :
                        <TextArea my="6px" resize="vertical" width={1} rows={12} value={body} onChange={e => setBody(e.target.value)} />}
                    <Box mt="auto" />
                    <Button fullWidth>Update post</Button>
                </Box>
            </form>
        }
    />;

    async function onSubmit(e: React.SyntheticEvent): Promise<void> {
        e.preventDefault();

        const title = titleRef.current?.value;
        const subtitle = subtitleRef.current?.value;
        let category = categoryRef.current?.value;

        if (title == null || title === "") {
            snackbarStore.addFailure("Title can't be empty", false);
            return;
        } else if (subtitle == null || subtitle === "") {
            snackbarStore.addFailure("Subtitle can't be empty", false);
            return;
        } else if (body == null || body === "") {
            snackbarStore.addFailure("Body can't be empty.", false);
            return;
        } else if (hideFrom != null && showFrom.getTime() > hideFrom.getTime()) {
            snackbarStore.addFailure("End time cannot be before start.", false);
            return;
        } else if (category == null || category === "") {
            snackbarStore.addFailure("Please add a category.", false);
            return;
        }

        try {
            if (!(categories.data).map(it => it.toLocaleLowerCase()).includes(category.toLocaleLowerCase())) {
                const proceed = await new Promise(resolve => addStandardDialog({
                    title: "Create category?",
                    message: `${category} doesn't exist, create it?`,
                    onConfirm: () => resolve(true),
                    onCancel: () => resolve(false)
                }));
                if (!proceed) return;
            } else {
                category = categories.data.find(it => it.toLowerCase() === category?.toLowerCase());
            }

            await Client.post("/news/update", {
                id: props.post.id,
                title,
                subtitle,
                body,
                showFrom: showFrom.getTime(),
                hideFrom: hideFrom?.getTime() ?? null,
                category
            } as NewsPost);
            props.stopEditing(true);
        } catch (err) {
            snackbarStore.addFailure(errorMessageOrDefault(err, "Failed to update post."), false);
        }
    }
}

export default DetailedNews;
