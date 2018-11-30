import styled from "styled-components";
import { Button, Flex } from ".";

const ButtonGroup = styled(Flex)`
    & > ${Button} {
        height: 35px;
        width: 100%;
        padding: 0 0 0 0;
        padding-left: 0px;
        padding-right: 0px;
        border-radius: 0px;
    }

    & > ${Button}:last-child {
        border-radius: 0 3px 3px 0;
    }

    & > ${Button}:first-child {
        border-radius: 3px 0 0 3px;
    }
`;

export default ButtonGroup;