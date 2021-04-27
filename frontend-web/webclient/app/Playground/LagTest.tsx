import * as React from "react";
import MainContainer from "MainContainer/MainContainer";
import * as Heading from "ui-components/Heading";
import {useTitle} from "Navigation/Redux/StatusActions";
import {Button, Icon, Input, List} from "ui-components";
import {useCallback, useMemo, useState} from "react";
import {ListRow, ListRowStat, ListStatContainer} from "ui-components/List";

const LagTest: React.FunctionComponent = () => {
    const numberOfInputs = 500;
    const [useStyledComponents, setUseStyledComponents] = useState(true);
    const [states, setStates] = useState<string[]>(() => {
        const initial: string[] = [];
        for (let i = 0; i < numberOfInputs; i++) {
            initial.push("");
        }
        return initial;
    });

    const updateStates: ((e: React.SyntheticEvent) => void)[] = useMemo(() => {
        const res: ((e: React.SyntheticEvent) => void)[] = [];
        for (let i = 0; i < numberOfInputs; i++) {
            res.push((e) => {
                const capturedI = i;
                setStates(prev => {
                    const newState = [...prev];
                    newState[capturedI] = e.target["value"];
                    return newState;
                })
            });
        }
        return res;
    }, []);

    const toggleStyledComponents = useCallback(() => {
        setUseStyledComponents(prev => !prev);
    }, []);

    useTitle("Lag Test");

    return <MainContainer
        header={<Heading.h2>Lag Test</Heading.h2>}
        main={
            <>
                <Button onClick={toggleStyledComponents}>Toggle Styled Components</Button>

                {!useStyledComponents ? null :
                    <List>
                        {states.map((state, idx) => (
                            <ListRow
                                key={idx}
                                left={
                                    <Input
                                        value={state}
                                        onChange={updateStates[idx]}
                                    />
                                }
                                leftSub={
                                    <ListStatContainer>
                                        <ListRowStat icon={"id"}>
                                            {state}
                                        </ListRowStat>
                                        <ListRowStat icon={"activity"}>
                                            {idx}
                                        </ListRowStat>
                                    </ListStatContainer>
                                }
                                right={
                                    <Button>
                                        <Icon name={"cpu"} mr={8}/>
                                        Ignore me
                                    </Button>
                                }
                            />
                        ))}
                    </List>
                }

                {useStyledComponents ? null :
                    <ul>
                        {states.map((state, idx) => (
                            <li key={idx}>
                                <input
                                    value={state}
                                    onChange={updateStates[idx]}
                                />
                                <div>
                                    <div>
                                        {state}
                                    </div>
                                    <div>
                                        {idx}
                                    </div>
                                </div>
                                <button>
                                    <div style={{marginRight: "8px"}}/>
                                    Ignore me
                                </button>
                            </li>
                        ))}
                    </ul>
                }
            </>
        }
    />;
};

export default LagTest;