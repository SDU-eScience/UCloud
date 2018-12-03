import * as React from "react";
import { Link } from "react-router-dom";
import * as ReactMarkdown from "react-markdown";
import { DefaultLoading } from "LoadingIcon/LoadingIcon";
import { Application } from "Applications";
import { Image, OutlineButton, Button, Box } from "ui-components";
import { TextSpan } from "ui-components/Text";
import * as Heading from "ui-components/Heading"
import styled from "styled-components";
import { dateToString } from "Utilities/DateUtilities";
import { toLowerCaseAndCapitalize } from "UtilityFunctions"
import ContainerForText from "ui-components/ContainerForText";
import { MainContainer } from "MainContainer/MainContainer";
import { ApplicationCardContainer, SlimApplicationCard } from "./Card";
import { Page } from "Types";
import { connect } from "react-redux";
import { ReduxObject } from "DefaultObjects";
import { Dispatch } from "redux";
import * as ViewObject from "./Redux/ViewObject";

import * as Actions from "./Redux/ViewActions";
import { updatePageTitle, UpdatePageTitleAction } from "Navigation/Redux/StatusActions";

interface MainContentProps {
    onFavorite?: () => void
    application: Application
}

interface OperationProps {
    onInit: (name: string, version: string) => void
}

type StateProps = ViewObject.Type;

interface OwnProps {
    match: any;
}

type ViewProps = OperationProps & StateProps & OwnProps;

class View extends React.Component<ViewProps> {
    constructor(props) {
        super(props);
    }

    componentDidMount() {
        const { appName, appVersion } = this.props.match.params;
        this.props.onInit(appName, appVersion);
    }

    render() {
        const { application } = this.props;
        return (
            <MainContainer
                header={!application ? null : <AppHeader application={application} />}

                main={!application ?
                    <DefaultLoading loading={this.props.loading} /> :
                    <ContainerForText>
                        <Content
                            application={application}
                            previousVersions={this.props.previous} />
                    </ContainerForText>
                }

                sidebar={!application ? null : <Sidebar application={application} />}
            />
        );
    }
}

const AppHeaderBase = styled.div`
    display: flex;
    flex-direction: row;

    & > ${Image} {
        width: 128px;
        height: 128px;
        border-radius: 8px;
        object-fit: cover;
        margin-right: 16px;
    }
`;

const AppHeaderDetails = styled.div`
    display: flex;
    flex-direction: column;

    & > h1, h2 {
        margin: 0;
    }
`;

const AppHeader: React.StatelessComponent<MainContentProps> = props => (
    <AppHeaderBase>
        <Image src={props.application.imageUrl} />
        <AppHeaderDetails>
            <Heading.h2>{props.application.description.title}</Heading.h2>
            <Heading.h3>v{props.application.description.info.version}</Heading.h3>
            <TextSpan>{props.application.description.authors.join(", ")}</TextSpan>
            <Tags tags={["Test Tag 1", "Test Tag 2", "Test Tag 3"]} />
        </AppHeaderDetails>
    </AppHeaderBase>
);

const Sidebar: React.StatelessComponent<MainContentProps> = props => (
    <>
        <ButtonGroup>
            <Button color={"blue"}>Add to My Applications</Button>
            <Link to={`/applications/${props.application.description.info.name}/${props.application.description.info.version}`}><OutlineButton color={"blue"}>Run Application</OutlineButton></Link>
            <a target="_blank" href="https://duckduckgo.com" rel="noopener"><OutlineButton color={"blue"}>Website</OutlineButton></a>
        </ButtonGroup>
    </>
);

const AppSection = styled(Box)`
    margin-bottom: 16px;
`;

function Content(props: MainContentProps & { previousVersions?: Page<Application> }): JSX.Element {
    return (
        <>
            <AppSection>
                <ReactMarkdown
                    unwrapDisallowed
                    source={props.application.description.description}
                    disallowedTypes={[
                        "image",
                        "heading"
                    ]}
                />
            </AppSection>

            <AppSection>
                <PreviousVersions previousVersions={props.previousVersions} />
            </AppSection>

            <AppSection>
                <Information application={props.application} />
            </AppSection>
        </>
    );
}

const PreviousVersions: React.StatelessComponent<{ previousVersions?: Page<Application> }> = props => (
    <>
        <Heading.h4>Previous Versions</Heading.h4>
        {!props.previousVersions ? null :
            <ApplicationCardContainer>
                {props.previousVersions.items.map((it, idx) => (
                    <SlimApplicationCard app={it} key={idx} />
                ))}
            </ApplicationCardContainer>
        }
    </>
);

const ButtonGroup = styled.div`
    display: flex;
    flex-direction: column;

    & button {
        width: 100%;
        margin-bottom: 8px;
    }
`;

const TagStyle = styled.a`
    text-decoration: none;
    padding: 6px;
    margin-right: 3px;
    border: 1px solid ${props => props.theme.colors.gray};
    border-radius: 5px;
`;

const TagBase = styled.div`
    display: flex;
    flex-direction: row;
`;

function Tags({ tags }: { tags: string[] }) {
    if (!tags) return null;

    return <div>
        <TagBase>
            {
                tags.slice(0, 5).map(tag => (
                    <TagStyle href={`foo/${tag}`}>{tag}</TagStyle>
                ))
            }
        </TagBase>
    </div>;
}

function InfoAttribute(props: {
    name: string,
    value?: string,
    children?: JSX.Element
}) {
    return <Box mb={8} mr={32}>
        <Heading.h5>{props.name}</Heading.h5>
        {props.value}
        {props.children}
    </Box>;
}

const pad = (value, length) =>
    (value.toString().length < length) ? pad("0" + value, length) : value;

const InfoAttributes = styled.div`
    display: flex;
    flex-direction: row;
`;

function Information({ application }: { application: Application }) {
    const time = application.tool.description.defaultMaxTime;
    const timeString = time ? `${pad(time.hours, 2)}:${pad(time.minutes, 2)}:${pad(time.seconds, 2)}` : "";

    return <>
        <Heading.h4>Information</Heading.h4>

        <InfoAttributes>
            <InfoAttribute
                name="Release Date"
                value={dateToString(application.createdAt)} />

            <InfoAttribute
                name="Default Time Allocation"
                value={timeString} />

            <InfoAttribute
                name="Default Nodes"
                value={`${application.tool.description.defaultNumberOfNodes}`} />

            <InfoAttribute
                name="Container Type"
                value={toLowerCaseAndCapitalize(application.tool.description.backend)} />
        </InfoAttributes>
    </>;
}

const mapDispatchToProps = (dispatch: Dispatch<Actions.Type | UpdatePageTitleAction>): OperationProps => ({
    onInit: async (name: string, version: string) => {
        dispatch(updatePageTitle(`${name} v${version}`));

        const loadApplications = async () => {
            dispatch({ type: Actions.Tag.SET_LOADING, payload: { loading: true } });
            dispatch(await Actions.fetchApplication(name, version));
        };

        const loadPrevious = async () => {
            dispatch({ type: Actions.Tag.SET_PREV_LOADING, payload: { previousLoading: true } });
            dispatch(await Actions.fetchPreviousVersions(name));
        };

        await Promise.all([loadApplications(), loadPrevious()]);
    }
})

const mapStateToProps = (state: ReduxObject): StateProps => {
    return { ...state.applicationView };
}

export default connect(mapStateToProps, mapDispatchToProps)(View);