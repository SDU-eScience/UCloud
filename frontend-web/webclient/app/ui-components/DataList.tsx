import Fuse from "fuse.js";
import * as React from "react";
import {Box, Icon, Input} from "@/ui-components";
import ClickableDropdown from "./ClickableDropdown";

interface ContentValuePair {content: string; value: string}

interface DataListProps {
    options: ContentValuePair[];
    onSelect: (value: string) => void;
    onChange?: (value: string) => void;
    placeholder: string;
    width?: number | string;
    clearOnSelect?: boolean;
    rightLabel?: boolean;
    leftLabel?: boolean;
}
export class DataList extends React.PureComponent<DataListProps, {
    text: string;
    fuse: Fuse<ContentValuePair>;
}> {
    private readonly totalShown = 8;

    constructor(props: DataListProps) {
        super(props);
        this.state = {
            text: "",
            fuse: new Fuse(this.props.options, DataList.options)
        };
    }

    static get options(): Fuse.IFuseOptions<ContentValuePair> {
        return {
            shouldSort: true,
            threshold: 0.2,
            location: 0,
            distance: 100,
            minMatchCharLength: 1,
            keys: [
                "content"
            ]
        };
    }

    public render(): JSX.Element {
        const results = this.state.text ?
            this.state.fuse.search(this.state.text).map(it => it.item) : this.props.options.slice(0, this.totalShown);
        return (
            <ClickableDropdown
                colorOnHover={results.length !== 0}
                fullWidth
                trigger={(
                    <div style={{ display: "flex", alignItems: "center" }}>
                        <Input
                            leftLabel={this.props.leftLabel}
                            rightLabel={this.props.rightLabel}
                            placeholder={this.props.placeholder}
                            autoComplete="off"
                            type="text"
                            value={this.state.text}
                            onChange={({target}) => {
                                this.setState(() => ({text: target.value}));
                            }}
                            onKeyUp={() => {
                                if (this.props.onChange) {
                                    this.props.onChange(this.state.text);
                                }
                            }}
                        />
                        <Icon name="chevronDownLight" ml="-32px" size={14} />
                    </div>
                )}
            >
                {results.map(({content, value}) => (
                    <Box key={content} onClick={() => this.onSelect(content, value)}>
                        {content}
                    </Box>
                ))}
                {results.length > this.totalShown ? <Box>...</Box> : null}
                {results.length === 0 ? <Box>No results</Box> : null}
            </ClickableDropdown>
        );
    }

    private onSelect(content: string, value: string): void {
        this.props.onSelect(value);
        if (this.state.text && this.props.clearOnSelect) this.setState(() => ({text: ""}));
        else this.setState(() => ({text: content}));
    }
}

export default DataList;
