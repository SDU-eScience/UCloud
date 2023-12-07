import * as React from "react";
import ManualTestingEntries from "@/Playground/manual-test-suite.json";
import {Box, Checkbox, Label} from "@/ui-components";
import {snackbarStore} from "@/Snackbar/SnackbarStore";

export default function ManualTestingOverview() {
    React.useEffect(() => {
        window.onbeforeunload = () => {
            snackbarStore.addInformation(
                "You currently have uploads in progress. Are you sure you want to leave UCloud?",
                true
            );
            return false;
        };
    }, []);

    return <TestingEntry depth={0} entry={ManualTestingEntries} />;
}

function TestingEntry(props): JSX.Element {
    const type = Array.isArray(props.entry) ? "array" : typeof props.entry;
    const depth = props.depth + 1;

    switch (type) {
        case "array": {
            return <Box ml={`${10 * depth}px`}>{props.entry.map(it => <TestingEntry depth={depth} entry={it} />)}</Box>
        }
        case "string": {
            return <Label ml={`${10 * depth}px`}><Checkbox />{props.entry}</Label>
        }
        case "object": {
            const keys = Object.keys(props.entry);
            return (<>
                {keys.map(it => <>
                    <h3 style={{marginLeft: `${10 * depth}px`}}>{it}</h3>
                    <TestingEntry depth={depth} entry={props.entry[it]} />
                </>)
                }
            </>);
        }
        default: {
            console.log("unhandled")
        } break;
    }
    return <div />
}