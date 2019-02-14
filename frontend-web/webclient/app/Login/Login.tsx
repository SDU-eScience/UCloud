import * as React from "react";
import { Box, Button, Input, Card, Flex, Text, Image } from "ui-components";
import * as Heading from "ui-components/Heading";
import styled from "styled-components";
const sduPlainBlack = require("Assets/Images/sdu_plain_black.png");
const bg1 = require("Assets/LoginImages/cloud1.jpg");
const bg2 = require("Assets/LoginImages/cloud2.jpg");
const bg3 = require("Assets/LoginImages/cloud3.jpg");

function randImage() {
    switch ((Math.random() * 3) | 0) {
        case 0:
            return bg1;
        case 1:
            return bg2;
        case 2:
        default:
            return bg3;
    }
}

const FixedSizeImage = styled(Image)`
    max-height: 100vh;
    max-width: 100vw;
    min-height: 100vh;
    min-width: 100vw;
`;

export const LoginPage = () => {
    return (<>
        <FixedSizeImage src={randImage()} />
        <Box alignItems="center" width="25%" minWidth="400px" maxWidth="420px">
            <CenteredBox>
                <Flex><Box mr="auto" /><Heading.h2>SDUCloud</Heading.h2><Box ml="auto" /></Flex>
                <form onSubmit={e => e.preventDefault()}>
                    <Card bg="lightGray" borderRadius="0.5em" p="1em 1em 1em 1em">
                        <Input type="hidden" value="web-csrf" name="service" />
                        <Input mb="0.5em" type="text" name="username" id="username" placeholder="Username" />
                        <Input mb="0.8em" type="password" name="password" id="password" placeholder="Password" />
                        <Button fullWidth onClick={() => console.log("submit")}>Login</Button>
                    </Card>
                </form>
                <Card borderRadius="0.5em" mt="0.3em" height="auto" p="1em 1em 1em 1em" bg="lightBlue">
                    <Flex>
                        <Box><Text fontSize={1} color="textColor">Under construction - Not yet available to the public.</Text></Box>
                    </Flex>
                </Card>
                <Flex mt="0.3em"><Box ml="auto" /><Image width="80px" src={sduPlainBlack} /></Flex>
            </CenteredBox>
        </Box>
    </>);
}

const CenteredBox = styled(Box)`
    margin: 0;
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
`;