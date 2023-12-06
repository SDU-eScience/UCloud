import * as React from "react";
import {useCloudAPI} from "@/Authentication/DataHook";
import * as Heading from "@/ui-components/Heading";
import {Link, Text, Flex, Box, Icon, Grid} from "@/ui-components";
import {buildQueryString} from "@/Utilities/URIUtilities";
import {NewsPost} from "@/Dashboard/Dashboard";
import HighlightedCard from "@/ui-components/HighlightedCard";
import {emptyPage} from "@/DefaultObjects";
import {useNavigate, useParams} from "react-router";
import {MainContainer} from "@/MainContainer/MainContainer";
import * as Pagination from "@/Pagination";
import {format} from "date-fns/esm";
import {Tag} from "@/Applications/Card";
import {capitalized} from "@/UtilityFunctions";
import {Client} from "@/Authentication/HttpClientInstance";
import {useTitle} from "@/Navigation/Redux/StatusActions";
import AppRoutes from "@/Routes";
import {colorFromTitle} from "@/ui-components/theme";

interface NewsPostRequestProps extends PaginationRequest {
    withHidden: boolean;
    filter?: string;
}

function newsPostRequest(payload: NewsPostRequestProps): APICallParameters<NewsPostRequestProps> {
    return {
        path: buildQueryString("/news/list", payload),
        payload
    };
}

export const NewsList: React.FC = () => {
    const {filter} = useParams<{filter?: string}>();
    const [newsPosts, setParams] = useCloudAPI<Page<NewsPost>>(newsPostRequest({
        itemsPerPage: 25,
        page: 0,
        withHidden: false,
        filter
    }), emptyPage);

    React.useEffect(() => {
        setParams(
            newsPostRequest({
                itemsPerPage: newsPosts.data.itemsPerPage,
                page: newsPosts.data.pageNumber,
                withHidden: Client.userIsAdmin,
                filter
            })
        );
    }, [filter]);

    useTitle("News");

    const navigate = useNavigate();

    return (
        <MainContainer
            headerSize={142}
            header={(
                <Box ml="10px">
                    <Heading.h2>News</Heading.h2>
                    {!filter ? null :
                        <Text>
                            {capitalized(filter)} <Icon cursor="pointer" color="black" onClick={() => navigate("/news/list")} name="close" ml="4px" size={12} />
                        </Text>
                    }
                </Box>
            )}
            main={(
                <Pagination.List
                    page={newsPosts.data}
                    loading={newsPosts.loading}
                    onPageChanged={page => setParams(newsPostRequest({
                        page,
                        filter,
                        itemsPerPage: newsPosts.data.itemsPerPage,
                        withHidden: (Client.userIsAdmin)
                    }))}
                    pageRenderer={pageRenderer}
                    customEmptyPage="No posts found."
                />
            )}
        />
    );

    function pageRenderer(page: Page<NewsPost>): React.ReactNode {
        const now = new Date().getTime();
        return <Grid gridTemplateColumns={"repeat(1, auto)"} gridGap={32}>
            {page.items.map(item => (
                <HighlightedCard color={"primary"} isLoading={false} key={item.id}>
                    <Box my={16}>
                        <Link mt="12px" to={AppRoutes.news.detailed(item.id)}>
                            <Flex><Heading.h3>{item.title}</Heading.h3><IsHidden hidden={item.hidden} /></Flex>
                        </Link>
                        <Heading.h5>{item.subtitle}</Heading.h5>
                        <Flex>
                            <Text>Posted {format(item.showFrom, "HH:mm dd/MM/yy")}</Text>
                            <Box mt="-3px" ml="4px">
                                <Tag bg={colorFromTitle(item.category)} label={item.category} />
                            </Box>
                        </Flex>
                        <IsExpired now={now} expiration={item.hideFrom} />
                    </Box>
                </HighlightedCard>
            ))}
        </Grid>;
    }
};

const IsExpired = (props: {now: number, expiration: number | null}): JSX.Element | null => {
    if (props.expiration != null && props.now > props.expiration)
        return <Text color="red">Expired at {format(props.expiration, "HH:mm dd/MM/yy")}</Text>;
    return null;
};

const IsHidden = (props: {hidden: boolean}): JSX.Element | null => {
    if (props.hidden) return <Text ml="8px" mt="8px" color="gray">Hidden</Text>;
    return null;
};

export default NewsList;
