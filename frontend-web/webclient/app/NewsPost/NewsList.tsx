import * as React from "react";
import {useCloudAPI, APICallParameters} from "Authentication/DataHook";
import {PaginationRequest, Page} from "Types";
import * as Heading from "ui-components/Heading";
import {Link, Text, Flex, Box, Icon, theme} from "ui-components";
import {buildQueryString} from "Utilities/URIUtilities";
import {NewsPost} from "Dashboard/Dashboard";
import {emptyPage} from "DefaultObjects";
import {useParams} from "react-router";
import {MainContainer} from "MainContainer/MainContainer";
import * as Pagination from "Pagination";
import {format} from "date-fns/esm";
import {Tag, hashF, appColor} from "Applications/Card";
import {Spacer} from "ui-components/Spacer";
import {capitalized} from "UtilityFunctions";

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
    const [newsPosts, setParams, params] = useCloudAPI<Page<NewsPost>>(newsPostRequest({
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
                withHidden: false,
                filter
            })
        );
    }, [filter]);

    return (
        <MainContainer
            header={(
                <Flex>
                    <Heading.h2>{filter ? capitalized(filter) : ""} Posts</Heading.h2>
                    {!filter ? null :
                        <Link to="/news/list/">
                            <Text mt="14px" ml="10px" fontSize={1}>Clear category <Icon color="black" name="close" size={12} /></Text>
                        </Link>
                    }
                </Flex>
            )}
            main={(
                <Pagination.List
                    page={newsPosts.data}
                    loading={newsPosts.loading}
                    onPageChanged={page => setParams(newsPostRequest({
                        page,
                        filter,
                        itemsPerPage: newsPosts.data.itemsPerPage,
                        withHidden: false
                    }))}
                    pageRenderer={pageRenderer}
                    customEmptyPage={"No posts found."}
                />
            )}
        />
    );

    function pageRenderer(page: Page<NewsPost>): JSX.Element[] {
        return page.items.map(item => (
            <Link to={`/news/detailed/${item.id}`} key={item.id}>
                <Heading.h3>{item.title}</Heading.h3>
                <Heading.h5>{item.subtitle}</Heading.h5>
                <Flex>
                    <Text fontSize={1}>Posted {format(item.showFrom, "HH:mm dd/MM/yy")}</Text>
                    <Box mt="-3px" ml="4px"><Tag bg={theme.appColors[appColor(hashF(item.category))][0]} label={item.category} /></Box>
                </Flex>
            </Link>
        ));
    }
};
