import * as React from "react";
import {useState} from "react";
import MainContainer from "@/ui-components/MainContainer";
import {ListRow} from "@/ui-components/List";
import List from "@/ui-components/List";
import {Button, ExternalLink} from "@/ui-components";

const Demo: React.FunctionComponent = () => {
    const [page, setPage] = useState(0);

    switch (page) {
        case 0:
        case 1:
            return <MainContainer
                main={
                    <List>
                        {["A", "B", "C", "D", "E", "F", "G"].map(it => {
                            const connected = page == 1 && it === "A";
                            return <ListRow
                                key={it}
                                icon={<img src={"https://placekitten.com/48/48"} />}
                                left={<>Provider {it}</>}
                                right={
                                    connected ? <Button disabled>Connected</Button> :
                                    <ExternalLink href={"https://deic-adm.sdu.dk/admin"} onClick={() => setPage(1)}>
                                        <Button>Connect</Button>
                                    </ExternalLink>
                                }
                            />;
                        })}
                    </List>
                }
            />;
        default:
            return null;
    }
};

export default Demo;
