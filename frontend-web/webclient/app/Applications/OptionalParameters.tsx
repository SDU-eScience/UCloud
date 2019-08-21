import * as React from "react";
import {SyntheticEvent} from "react";
import * as Fuse from "fuse.js";
import * as Types from "Applications/index";
import {Box, Button, Flex, Markdown} from "ui-components";
import * as Heading from "ui-components/Heading";
import Input from "ui-components/Input";
import styled from "styled-components";
import {EllipsedText} from "ui-components/Text";

interface OptionalParameterProps {
    parameters: Types.ApplicationParameter[]
    onUse: (aP: Types.ApplicationParameter) => void
}

interface OptionalParametersState {
    results: Types.ApplicationParameter[]
}

const OptionalParamsBox = styled(Box)`
    max-height: 35em;
    padding-top: 8px;
    padding-right: 8px;
    padding-bottom: 8px;
    overflow-y: auto;
`;

export class OptionalParameters extends React.Component<OptionalParameterProps, OptionalParametersState> {
    private fuse: Fuse<Types.ApplicationParameter>;
    private currentTimeout: number = -1;
    private searchField = React.createRef<HTMLInputElement>();

    constructor(props: OptionalParameterProps) {
        super(props);
        this.state = {results: props.parameters};
        this.initFuse();
    }

    private initFuse() {
        this.fuse = new Fuse(this.props.parameters, {
            shouldSort: true,
            threshold: 0.6,
            location: 0,
            distance: 100,
            maxPatternLength: 32,
            minMatchCharLength: 1,
            keys: [
                "title",
                "description"
            ]
        });

    }

    componentDidUpdate(prevProps: OptionalParameterProps) {
        if (this.props.parameters !== prevProps.parameters) {
            this.initFuse();
            const current = this.searchField.current;
            if (current != null) this.search(current.value);
        }
    }

    private search(searchTerm: string) {
        if (this.currentTimeout !== -1) clearTimeout(this.currentTimeout);

        if (searchTerm === "") {
            this.setState({results: this.props.parameters});
        } else {
            this.currentTimeout = setTimeout(() => {
                const results = this.fuse.search(searchTerm);
                this.setState({results});
            }, 300);
        }
    }

    render() {
        const {onUse} = this.props;
        const {results} = this.state;
        const components = results.map((p, i) => <OptionalParameter key={i} parameter={p} onUse={() => onUse(p)}/>);

        return (
            <>
                <Flex mb={16} alignItems={"center"}>
                    <Box flexGrow={1}>
                        <Heading.h4>Optional Parameters ({results.length})</Heading.h4>
                    </Box>
                    <Box flexShrink={0}>
                        <Input placeholder={"Search..."} ref={this.searchField}
                               onChange={e => this.search(e.target.value)}/>
                    </Box>
                </Flex>
                <OptionalParamsBox>{components}</OptionalParamsBox>
            </>
        );

    }
}

class OptionalParameter extends React.Component<{ parameter: Types.ApplicationParameter, onUse: () => void }, { open: boolean }> {
    state = {open: false};

    private static Base = styled(Flex)`
        align-items: center;
        cursor: pointer;

        strong, & > ${EllipsedText} {
            user-select: none;
        }

        strong {
            margin-right: 16px;
            font-weight: bold;
            flex-shrink: 0;
        }

        & > ${EllipsedText} {
            color: ${(props) => props.theme.colors.gray};
            flex-grow: 1;
        }

        & > ${EllipsedText} > p {
            margin: 0;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        & > ${Button} {
            margin-left: 16px;
            flex-shrink: 0;
        }
    `;

    render() {
        const {parameter, onUse} = this.props;
        const {open} = this.state;

        const toggleOpen = ({preventDefault}: { preventDefault: () => void }) => {
            preventDefault();
            this.setState({open: !open});
        };

        const use = (e: SyntheticEvent<HTMLButtonElement>) => {
            e.stopPropagation();
            e.preventDefault();
            onUse();
        };

        return (
            <Box mb={8}>
                <OptionalParameter.Base onClick={toggleOpen}>
                    <strong>{parameter.title}</strong>
                    {!open ?
                        <EllipsedText>
                            <Markdown
                                source={parameter.description}
                                allowedTypes={["text", "root", "paragraph"]}/>
                        </EllipsedText>
                        : <Box flexGrow={1}/>}

                    <Button
                        type="button"
                        lineHeight={"16px"}
                        onClick={use}>
                        Use
                    </Button>
                </OptionalParameter.Base>
                {open ? <Markdown source={parameter.description}/> : null}
            </Box>
        );
    }
}