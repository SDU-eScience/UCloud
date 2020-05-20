import * as React from "react";
import {useState, useEffect, useRef} from "react";
import {Client} from "Authentication/HttpClientInstance";
import {useAsyncCommand, useCloudAPI} from "Authentication/DataHook";
import {MainContainer} from "MainContainer/MainContainer";
import * as Heading from "ui-components/Heading";
import {grantCredits, retrieveBalance, RetrieveBalanceResponse, setCredits} from "Accounting/Compute/index";
import {Input} from "ui-components";

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
        console.log(username);
        fetchUserBalance(retrieveBalance({id: username, type: "USER"}));
    }, [username]);

    useEffect(() => {
        if (project === "") return;
        fetchUserBalance(retrieveBalance({id: project, type: "PROJECT"}));
    }, [project]);

    const reload = (): void => {
        fetchUserBalance(retrieveBalance({id: username, type: "USER"}));
        fetchUserBalance(retrieveBalance({id: project, type: "PROJECT"}));
    };

    return <MainContainer
        header={<Heading.h2>Compute Credits (Admin Prototype UI)</Heading.h2>}
        main={(
            <>
                <Heading.h4>Credits for Users</Heading.h4>
                <form onSubmit={e => {
                    e.preventDefault();
                    setUsername(usernameRef.current!.value)
                }}>
                    <Input placeholder={"Username"} ref={usernameRef}/>
                </form>
                {username === "" ? null : (
                    <>
                        <div>
                            {currentUserBalance.data.balance.map(it => (
                                <div key={it.type}>
                                    <div>Balance ({it.type}): {it.creditsRemaining}</div>
                                    <form onSubmit={async e => {
                                        e.preventDefault();
                                        const field = (e.target as HTMLFormElement)
                                            .querySelector(`#add-${it.type}`) as HTMLInputElement;

                                        const value = parseInt(field.value, 10);
                                        await runCommand(grantCredits({
                                            account: {id: username, type: "USER", machineType: it.type},
                                            credits: value
                                        }));
                                        reload();
                                    }}>
                                        <Input placeholder={"Add credits"} id={`add-${it.type}`}/>
                                    </form>

                                    <form onSubmit={async e => {
                                        e.preventDefault();
                                        const field = (e.target as HTMLFormElement)
                                            .querySelector(`#set-${it.type}`) as HTMLInputElement;
                                        const value = parseInt(field.value, 10);
                                        await runCommand(setCredits({
                                            account: {id: username, type: "USER", machineType: it.type},
                                            lastKnownBalance: it.creditsRemaining,
                                            newBalance: value
                                        }));
                                        reload();
                                    }}>
                                        <Input placeholder={"Set credits"} id={`set-${it.type}`}/>
                                    </form>
                                </div>
                            ))}
                        </div>
                    </>
                )}

                <Heading.h4>Credits for Projects</Heading.h4>
            </>
        )}
    />;
};
