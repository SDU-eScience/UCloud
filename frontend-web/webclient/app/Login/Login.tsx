import * as React from "react";
import { Box, Button, Input, Card, Flex, Text, Image } from "ui-components";
import * as Heading from "ui-components/Heading";
import styled from "styled-components";
const sduPlainBlack = require("Assets/Images/sdu_plain_black.png");

export const LoginPage = () => {
    return (
        <Box alignItems="center" width="25%" minWidth="400px" maxWidth="420px">
            <CenteredBox>
                <Flex><Box mr="auto" /><Heading.h2>SDU Cloud</Heading.h2><Box ml="auto" /></Flex>
                <form onSubmit={e => e.preventDefault()}>
                    <Card borderRadius="0.5em" p="1em 1em 1em 1em">
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
    )
}

const CenteredBox = styled(Box)`
    margin: 0;
    position: absolute;
    top: 50%;
    left: 50%;
    transform: translate(-50%, -50%);
`;