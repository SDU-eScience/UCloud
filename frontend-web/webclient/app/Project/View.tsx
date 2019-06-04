import * as React from "react";
import {match} from "react-router";
import {LoadingMainContainer, MainContainer} from "MainContainer/MainContainer";
import {useCloudAPI} from "Authentication/DataHook";
import {emptyProject, Project, viewProject} from "Project/index";
import Box from "ui-components/Box";
import {useEffect} from "react";

const View: React.FunctionComponent<{ match: match<{ id: string }> }> = props => {
    const id = props.match.params.id;
    const [members, setMemberParams] = useCloudAPI<Project>(viewProject({id}), emptyProject(id));

    useEffect(() => {
        setMemberParams(viewProject({id}));
    }, [id]);

    return <LoadingMainContainer
        headerSize={0}
        header={null}
        sidebar={null}
        loading={members.loading}
        error={members.error ? members.error.why : undefined}
        main={
            members.data.members.map((e, idx) => (
                <Box key={idx}>{e.username} {e.role}</Box>
            ))
        }
    />
};

export default View;