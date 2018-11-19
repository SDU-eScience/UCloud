import * as React from "react";
import { Link } from "react-router-dom";
import { simpleSearch, ProjectMetadata } from "./api";
import { Page } from "Types";
import { emptyPage } from "DefaultObjects";
import { withRouter } from "react-router-dom";
import * as PropTypes from "prop-types";
import * as Pagination from "Pagination";
import { KeyCode } from "DefaultObjects";
import { History } from "history";
import { updatePageTitle } from "Navigation/Redux/StatusActions";
import * as Heading from "ui-components/Heading";
import { Hide, Input, Box, Flex, Text, Stamp, Divider, Card } from "ui-components";

interface SearchState {
    query: string
    dataProvider: (page: number, itemsPerPage: number) => Promise<Page<ProjectMetadata>>
}

interface SearchProps {
    history: History
    match: {
        params: {
            query?: string
        }
    }
}

class SearchComponent extends React.Component<SearchProps, SearchState> {
    constructor(props, ctx) {
        super(props);
        this.state = {
            query: "",
            dataProvider: (page: number, itemsPerPage: number) => Promise.resolve(emptyPage)
        };
        ctx.store.dispatch(updatePageTitle("Search"));
    }

    static contextTypes = {
        store: PropTypes.object
    }

    componentDidMount() {
        this.checkQueryParams();
    }

    componentDidUpdate() {
        // TODO Doesn't work correctly with react router on back/forwards browser navigation
        this.checkQueryParams();
    }

    private checkQueryParams() {
        const query = this.props.match.params.query;
        if (query == null) {
            return;
        } else if (this.state.query != query) {
            this.setState(() => ({
                query,
                dataProvider: (page: number, itemsPerPage: number) => simpleSearch(query.toLowerCase(), page, itemsPerPage)
            }));
        }
    }

    render() {
        const { history } = this.props;
        return (
            <div>
                <Hide xl lg md>
                    <Input
                        width="100%"
                        placeholder="Search..."
                        onKeyDown={(e: any) => { if (e.keyCode === KeyCode.ENTER) history.push(`/metadata/search/${e.target.value}`) }}
                    />
                </Hide>
                <Heading.h2>Results matching '{this.state.query}'</Heading.h2>

                <Pagination.ManagedList
                    dataProvider={this.state.dataProvider}
                    pageRenderer={this.pageRenderer}
                />
            </div>
        );
    }

    pageRenderer = (page: Page<ProjectMetadata>): React.ReactNode => (
        <>{page.items.map((item, index) => <SearchItem item={item} key={index} />)}</>
    )
}

export const SearchItem = ({ item }: { item: ProjectMetadata }) => (
    <Card height="154px" p="12px" mb="0.5em" mt="0.5em" borderRadius=".28571429rem">
        <Heading.h3><Link to={`/metadata/${item.sduCloudRoot}`}>{item.title}</Link></Heading.h3>

        <Divider />
        <Box mt="1em" mb="1em">
            <Text>
                {firstParagraphWithLimitedLength(
                    defaultIfBlank(item.description, "No description"),
                    800
                )}
            </Text>
        </Box>
        <Divider mb="1em" />
        <Stamp bg="green" color="white" borderColor="green">
            <Box pl="0.5em" pr="0.5em">
                <i className="fas fa-folder-open" />
            </Box>
            Open Access
            </Stamp>
        <Stamp bg="blue" color="white" borderColor="blue" ml="0.2em">
            <Flex>
                <Box pl="0.5em" pr="0.5em">
                    <i className="fas fa-book"></i>
                </Box>
                MIT
                <Text pl="0.2em" color="lightGrey">License</Text>
            </Flex>
        </Stamp>
    </Card>
);

const defaultIfBlank = (text: string, defaultValue: string): string => {
    if (text.length == 0) return defaultValue;
    else return text;
};

const firstParagraphWithLimitedLength = (text: string, maxLength: number): string => {
    const lines = text.split("\n");
    const paragraphEndsAt = lines.findIndex((line) => /^\s*$/.test(line));

    let firstParagraph: string;
    if (paragraphEndsAt == -1) firstParagraph = text;
    else firstParagraph = lines.slice(0, paragraphEndsAt).join("\n");

    if (firstParagraph.length > maxLength) {
        return firstParagraph.substring(0, maxLength) + "...";
    } else {
        return firstParagraph;
    }
};

export const Search = withRouter(SearchComponent);