import * as React from "react";
import { Message, Input, Form, Header, Dropdown, Button } from "semantic-ui-react";
import { simpleSearch, ProjectMetadata } from "./api";
import { Page } from "../../types/types";
import * as Pagination from "../Pagination";

export interface SearchProps {
    match?: any
    location?: { search: string }
}

interface SearchState {
    query: string
    page: number
    itemsPerPage: number
    totalPages: number

    errorMessage?: string
    pageWithRecords: Page<ProjectMetadata>
}

const emptyPage = { itemsInTotal: 0, itemsPerPage: 10, items: [], pageNumber: 0 };

export class Search extends React.Component<SearchProps, SearchState> {
    constructor(props: SearchProps) {
        super(props);
        this.state = {
            query: "",
            page: 0,
            itemsPerPage: 10,
            totalPages: 0,
            pageWithRecords: emptyPage
        };
    }

    componentWillReceiveProps() {
        this.checkQueryParams();
    }

    private checkQueryParams() {
        const params = new URLSearchParams(this.props.location.search);
        const rawQuery = params.get("query");
        const rawPage = params.get("page");

        const query = rawQuery ? rawQuery : "";
        const page = rawPage ? parseInt(rawPage) : 0;

        if (this.state.query != query || this.state.page != page) {
            this.refreshSearch(query, page);
        }
    }

    private refreshResults() {
        this.refreshSearch(this.state.query, this.state.page);
    }

    private refreshSearch(query: string, page: number) {
        this.setState(() => ({ query, page }));

        simpleSearch(query, 0, 10).then(e => {
            this.setState(() => ({
                pageWithRecords: e,
                totalPages: e.itemsInTotal / e.itemsPerPage
            }));
        }).catch(e => {
            this.setState(() => ({
                errorMessage: "Unable to retrieve search results",
                pageWithRecords: emptyPage
            }));
        });
    }

    render() {
        const { errorMessage } = this.state;
        const results = (this.state.pageWithRecords.items.length == 0) ?
            <div>No results</div>
            :
            <div />;
        return (
            <div>
                <Input fluid icon='search' placeholder='Search...' />
                {errorMessage ?
                    <Message color='red' onDismiss={() => this.setState({ errorMessage: null })}>
                        {errorMessage}
                    </Message>
                    : null
                }

                <div>
                    Query: {this.state.query} <br />
                    Page: {this.state.page} <br />
                </div>

                {results}

                <Pagination.Buttons
                    currentPage={this.state.page}
                    totalPages={this.state.totalPages}
                    toPage={(newPage: number) => { }}
                />

                <Pagination.EntriesPerPageSelector
                    entriesPerPage={this.state.itemsPerPage}
                    onChange={() => { }}
                    content="Results per page"
                />
            </div>
        );
    }
}

class SearchItem extends React.Component<any, any> {
    constructor(props: any) {
        super(props);
    }

    render() {
        return <div />;
    }
}