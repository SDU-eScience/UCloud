import * as React from "react";
import {MainContainer} from "MainContainer/MainContainer";
import * as Heading from "ui-components/Heading";
import {Button, Input} from "ui-components";
import {useCallback, useRef} from "react";
import {useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {useProjectStatus} from "Project/cache";
import {createProject} from "Project/index";
import {grantCredits} from "Accounting/Compute";

export const ProjectPlayground: React.FunctionComponent = () => {
    const projectStatus = useProjectStatus();
    const [, runCommand] = useAsyncCommand();
    const projectTitleRef = useRef<HTMLInputElement>(null);
    const projectParentRef = useRef<HTMLInputElement>(null);

    const grantProjectRef = useRef<HTMLInputElement>(null);
    const grantCreditsRef = useRef<HTMLInputElement>(null);

    const onCreateProject = useCallback(async (e) => {
        e.preventDefault();

        await runCommand(createProject({
            title: projectTitleRef.current!.value,
            parent: projectParentRef.current!.value
        }));
        await projectStatus.reload();
    }, [projectTitleRef, projectParentRef, runCommand]);

    const onGrant = useCallback(async (e) => {
        e.preventDefault();
        await runCommand(grantCredits({
            credits: parseInt(grantCreditsRef.current!.value),
            wallet: {
                id: grantProjectRef.current!.value,
                paysFor: {provider: "ucloud", id: "standard"},
                type: "PROJECT"
            }
        }))
    }, [grantCreditsRef, grantProjectRef, runCommand]);

    return <MainContainer
        header={<Heading.h2>Project playground</Heading.h2>}
        main={
            <>
                <b>My projects</b>
                <ul>
                    {projectStatus.fetch().membership.map(p =>
                        <li key={p.projectId}>{p.title} ({p.projectId}) (parent: {p.parentId})</li>
                    )}
                </ul>

                <form onSubmit={onCreateProject}>
                    <b>Create sub-project</b>
                    <Input ref={projectTitleRef} placeholder={"Title"} />
                    <Input ref={projectParentRef} placeholder={"Parent"} />
                    <Button>Submit</Button>
                </form>

                <form onSubmit={onGrant}>
                    <b>Grant credits</b>
                    <Input ref={grantProjectRef} placeholder={"Project ID"}/>
                    <Input ref={grantCreditsRef} placeholder={"Credits"} />
                    <Button>Submit</Button>
                </form>
            </>
        }
    />;
};
