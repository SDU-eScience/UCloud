import {useCallback, useState} from "react";
import * as React from "react";
import styled from "styled-components";
import Box from "ui-components/Box";
import Button from "ui-components/Button";
import * as Heading from "ui-components/Heading";
import Icon from "ui-components/Icon";
import {Spacer} from "ui-components/Spacer";
import {inDevEnvironment} from "UtilityFunctions";
import {DEV_SITE, DEV_WEBDAV_URL, PRODUCT_NAME, PRODUCTION_WEBDAV_URL} from "../../site.config.json";

const win1 = require("Assets/Images/webdav/win_dav_1.png");
const win2 = require("Assets/Images/webdav/win_dav_2.png");
const win3 = require("Assets/Images/webdav/win_dav_3.png");

const macos1 = require("Assets/Images/webdav/macos_dav_1.png");
const macos2 = require("Assets/Images/webdav/macos_dav_2.png");

const nautilus1 = require("Assets/Images/webdav/nautilus_dav_1.png");

export const Instructions: React.FunctionComponent<{token: string}> = props => {
    const server = inDevEnvironment() || window.location.host === DEV_SITE ?
        DEV_WEBDAV_URL : PRODUCTION_WEBDAV_URL;

    return (
        <ContentContainer>
            <Heading.h2 mt={16}>{PRODUCT_NAME} - Mounting Your Files Locally (Beta)</Heading.h2>
            <Heading.h3>To continue select your platform below</Heading.h3>

            <CollapsibleBox
                head={<Heading.h4>Windows</Heading.h4>}
                body={(
                    <Box>
                        <Step>
                            <Heading.h5>Step 1</Heading.h5>
                            <p>
                                Open up Explorer and right-click <i>"This PC"</i> -> select <i>"Map network
                                drive..."</i>
                            </p>
                            <Screenshot src={win1} alt={"Right click 'This PC' and select 'Map network drive...'"} />
                        </Step>

                        <Step>
                            <Heading.h5>Step 2</Heading.h5>
                            <p>
                                Use the following <i>Folder</i>:
                            </p>

                            <code>
                                <pre>{server}</pre>
                            </code>

                            <p>
                                And select the option <i>"Connect using different credentials"</i>
                            </p>
                            <Screenshot src={win2} alt={`Screenshot of connecting to ${PRODUCT_NAME}`} />
                        </Step>

                        <Step>
                            <Heading.h5>Step 3</Heading.h5>
                            <p>
                                Connect with the following credentials:
                            </p>

                            <CredentialsInstructions token={props.token} />
                            <Screenshot src={win3} alt={"Connect using the credentials above"} />
                        </Step>
                    </Box>
                )}
            />

            <CollapsibleBox
                head={<Heading.h4>macOS</Heading.h4>}
                body={(
                    <Box>
                        <Step>
                            <Heading.h5>Step 1</Heading.h5>
                            <p>
                                Open <i>Finder</i> and press <i>Cmd + K</i>. The <i>"Connect to Server"</i> window
                                should appear. Use the following server:
                            </p>

                            <code>
                                <pre>{server}</pre>
                            </code>

                            <Screenshot src={macos1} alt={`Connecting to ${PRODUCT_NAME} via macOS`} />
                        </Step>

                        <Step>
                            <Heading.h5>Step 2</Heading.h5>
                            <p>
                                Select <i>"Registered User"</i> and enter the following credentials:
                            </p>

                            <CredentialsInstructions token={props.token} />
                            <Screenshot src={macos2} alt={"Connect using the credentials above"} />
                        </Step>
                    </Box>
                )}
            />

            <CollapsibleBox
                head={<Heading.h4>Linux (Nautilus)</Heading.h4>}
                body={(
                    <Box>
                        <Step>
                            <Heading.h5>Step 1</Heading.h5>
                            <p>
                                This guide assumes that you will be using the Nautilus file browser. Nautilus is
                                the default file browser for operating systems such as Ubuntu. If you are not using
                                Nautilus see the "Linux (CLI)" section.
                            </p>

                            <p>
                                Open the file browser and go to <i>"Other Locations"</i>
                            </p>
                        </Step>

                        <Step>
                            <Heading.h5>Step 2</Heading.h5>
                            <p>
                                At the bottom of the window you should see <i>"Connect to Server"</i>. Type in the
                                following server:
                            </p>

                            <code>
                                <pre>{server}</pre>
                            </code>

                            <Screenshot src={nautilus1} alt={"Connect to server via Nautilus"} />
                        </Step>

                        <Step>
                            <Heading.h5>Step 3</Heading.h5>
                            <p>
                                When prompted for credentials enter the following:
                            </p>

                            <CredentialsInstructions token={props.token} />
                        </Step>
                    </Box>
                )}
            />

            <CollapsibleBox
                head={<Heading.h4>Linux (CLI)</Heading.h4>}
                body={(
                    <Box>
                        <Step>
                            <Heading.h5>Step 1</Heading.h5>
                            <p>
                                The <i>davfs2</i> package is required to mount {PRODUCT_NAME}. On Debian based systems this
                                can be accomplished with the following command:
                            </p>

                            <code>
                                <pre>sudo apt install davfs2</pre>
                            </code>

                            <p>
                                To mount {PRODUCT_NAME} enter in the following command:
                            </p>

                            <code>
                                <pre>mount -t davfs {server} $MOUNTPOINT</pre>
                            </code>

                            <p>
                                Note that <code>$MOUNTPOINT</code> should be replaced with your preferred mountpoint.
                                This directory most already exist and be empty.
                            </p>

                            <p>When prompted for credentials enter the following:</p>

                            <CredentialsInstructions token={props.token} />
                        </Step>
                    </Box>
                )}
            />

            <CollapsibleBox
                head={<Heading.h4>Other</Heading.h4>}
                body={(
                    <Box>
                        <p>
                            Using any WebDAV client you can mount {PRODUCT_NAME}. Use the following server:
                        </p>

                        <code>
                            <pre>{server}</pre>
                        </code>

                        <p>
                            With the following credentials:
                        </p>

                        <CredentialsInstructions token={props.token} />
                    </Box>
                )}
            />

            <Box m={10} />

            <a href={"/app"}><Button fullWidth>Return to {PRODUCT_NAME}</Button></a>
        </ContentContainer>
    );
};

interface CollapsibleBoxProps {
    head: React.ReactNode;
    body: React.ReactNode;
}

const CollapsibleBox: React.FunctionComponent<CollapsibleBoxProps> = props => {
    const [isOpen, setIsOpen] = useState(false);
    const toggleOpen = useCallback(() => {
        setIsOpen(!isOpen);
    }, [isOpen]);

    return (
        <Box>
            <ClickableBox onClick={toggleOpen}>
                <Spacer
                    left={<>{props.head}</>}
                    right={<Icon name={"chevronDown"} rotation={isOpen ? 180 : 0} />}
                />
            </ClickableBox>
            {!isOpen ? null : props.body}
        </Box>
    );
};

const ClickableBox = styled(Box)`
    cursor: pointer;
    margin-top: 16px;
    user-select: none;
    border-bottom: 1px solid black;
`;

const ContentContainer = styled.div`
    width: 800px;
    margin: 0 auto;
`;

const Screenshot = styled.img`
    max-height: 400px;
    text-align: center;
`;

const Step = styled.div`
    margin-top: 16px;
`;

const CredentialsInstructions: React.FunctionComponent<{token: string}> = props => {
    return (
        <Box>
            <b>Username: </b>
            <code>{PRODUCT_NAME.toLocaleLowerCase()}</code><br />

            <b>Password: </b>
            <code>{props.token}</code>
        </Box>
    );
};
