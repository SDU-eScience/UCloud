import * as React from "react";
import {useState, useEffect, useRef} from "react";
import {Client} from "Authentication/HttpClientInstance";
import {APICallState, useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {MainContainer} from "MainContainer/MainContainer";
import * as Heading from "ui-components/Heading";
import {
    AccountType,
    grantCredits,
    retrieveBalance,
    RetrieveBalanceResponse,
    setCredits
} from "Accounting/Compute/index";
import {Box, Flex, Input} from "ui-components";
import styled from "styled-components";

const WalletContainer = styled.div`
    display: grid;
    margin-bottom: 16px;
    grid-gap: 20px;
    grid-template-columns: repeat(2, 2fr);
`;

export const Credits: React.FunctionComponent = () => {
    console.log("Hello!");
    // Note(Dan): This is a really bad UI meant only for prototyping purposes.
    const usernameRef = useRef<HTMLInputElement>(null);
    const projectRef = useRef<HTMLInputElement>(null);
    const [username, setUsername] = useState<string>("");
    const [project, setProject] = useState<string>("");
    const [loading, runCommand] = useAsyncCommand();
    const [currentUserBalance, fetchUserBalance] = useCloudAPI<RetrieveBalanceResponse>(
        {noop: true},
        {balance: []}
    );

    const [currentProjectBalance, fetchProjectBalance] = useCloudAPI<RetrieveBalanceResponse>(
        {noop: true},
        {balance: []}
    );

    useEffect(() => {
        if (username === "") return;
        fetchUserBalance(retrieveBalance({id: username, type: "USER"}));
    }, [username]);

    useEffect(() => {
        if (project === "") return;
        fetchProjectBalance(retrieveBalance({id: project, type: "PROJECT"}));
    }, [project]);

    const reload = (): void => {
        fetchUserBalance(retrieveBalance({id: username, type: "USER"}));
        fetchProjectBalance(retrieveBalance({id: project, type: "PROJECT"}));
    };

    return <MainContainer
        header={<Heading.h2>Compute Credits (Admin Prototype UI)</Heading.h2>}
        main={(
            <>
                <Heading.h3>Credits for Users</Heading.h3>
                <form onSubmit={e => {
                    e.preventDefault();
                    setUsername(usernameRef.current!.value);
                }}>
                    <Input placeholder={"Username"} ref={usernameRef}/>
                </form>
                {username === "" ? null : (
                    <BalanceForAccount reload={reload} id={username} type={"USER"} balance={currentUserBalance} />
                )}

                <Box mt={16}>
                    <Heading.h4>Credits for Projects</Heading.h4>
                    <form onSubmit={e => {
                        e.preventDefault();
                        setProject(projectRef.current!.value);
                    }}>
                        <Input placeholder={"Project"} ref={projectRef}/>
                    </form>
                    {project === "" ? null : (
                        <BalanceForAccount reload={reload} id={project} type={"PROJECT"}
                                           balance={currentProjectBalance} />
                    )}
                </Box>
            </>
        )}
    />;
};

export const BalanceForAccount: React.FunctionComponent<{
    reload: () => void;
    id: string;
    type: AccountType;
    balance: APICallState<RetrieveBalanceResponse>;
}> = props => {
    const [, runCommand] = useAsyncCommand();
    return <div>
        {!props.balance.error ? null : <>{props.balance.error.why}</>}

        {props.balance.error ? null : props.balance.data.balance.map(it => (
            <Box key={it.type} mt={8} mb={8}>
                <Heading.h4>Balance ({it.type}): {it.creditsRemaining}</Heading.h4>

                <WalletContainer>
                    <form onSubmit={async e => {
                        e.preventDefault();
                        const field = (e.target as HTMLFormElement)
                            .querySelector("input") as HTMLInputElement;

                        const value = parseInt(field.value, 10);
                        await runCommand(grantCredits({
                            account: {id: props.id, type: props.type, machineType: it.type},
                            credits: value
                        }));
                        props.reload();
                    }}>
                        <Input placeholder={"Add credits"} />
                    </form>

                    <form onSubmit={async e => {
                        e.preventDefault();
                        const field = (e.target as HTMLFormElement)
                            .querySelector("input") as HTMLInputElement;
                        const value = parseInt(field.value, 10);
                        await runCommand(setCredits({
                            account: {id: props.id, type: props.type, machineType: it.type},
                            lastKnownBalance: it.creditsRemaining,
                            newBalance: value
                        }));
                        props.reload();
                    }}>
                        <Input placeholder={"Set credits"} />
                    </form>
                </WalletContainer>
            </Box>
        ))}
    </div>;
};
