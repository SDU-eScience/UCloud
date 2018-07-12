import * as React from "react";
import { Link } from "react-router-dom";
import { Label, Icon, Card, Header, Responsive, Input } from "semantic-ui-react";
import { simpleSearch, ProjectMetadata } from "./api";
import { Page } from "../../Types";
import { emptyPage } from "../../DefaultObjects";
import { withRouter } from "react-router-dom";
import { PropTypes } from "prop-types";
import * as Pagination from "../Pagination";
import { KeyCode } from "../../DefaultObjects";
import { History } from "history";
import { updatePageTitle } from "../Navigation/Redux/StatusActions";

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
        // console.log(window.location.search);
        // const params = new URLSearchParams(window.location.search);
        // const rawQuery = params.get("query");
        // const query = rawQuery ? rawQuery : "";
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
                <Responsive as={Input} maxWidth={999}
                    fluid
                    placeholder="Search..."
                    onKeyDown={(e) => { if (e.keyCode === KeyCode.ENTER) history.push(`/metadata/search/${e.target.value}`) }}
                />
                <Header as="h2">Results matching '{this.state.query}'</Header>

                <Pagination.ManagedList
                    dataProvider={this.state.dataProvider}
                    pageRenderer={this.pageRenderer}
                />
            </div>
        );
    }

    pageRenderer(page: Page<ProjectMetadata>): React.ReactNode {
        return <React.Fragment>
            {page.items.map((item, index) => <SearchItem item={item} key={index} />)}
        </React.Fragment>;
    }
}

const SearchItem = ({ item }: { item: ProjectMetadata }) => (
    <Card fluid>
        <Card.Content>
            <Header><Link to={`/metadata/${item.sduCloudRoot}`}>{item.title}</Link></Header>
        </Card.Content>

        <Card.Content
            description={
                firstParagraphWithLimitedLength(
                    defaultIfBlank(item.description, "No description"),
                    800
                )
            }
        />
        <Card.Content extra>
            <Label color='green'>
                <Icon name='folder open' />
                Open Access
            </Label>
            <Label color='blue'>
                <Icon name='book' />
                MIT
                <Label.Detail>License</Label.Detail>
            </Label>
            <Label basic>
                <Icon name='file' />
                {item.files.length}
                <Label.Detail>Files</Label.Detail>
            </Label>
        </Card.Content>
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