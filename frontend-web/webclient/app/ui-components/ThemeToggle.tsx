import styled from "styled-components";
import * as React from "react";

// Inspired by https://codepen.io/efreeman79/pen/XgZPGO
export function ThemeToggler({isDarkTheme}) {
    const [active, setActive] = React.useState<boolean>(isDarkTheme);
    return (
        <Wrapper onClick={() => setActive(!active)} active={active}>
            <Switch active={active}>
                <Moon>
                    <Crater active={active} />
                    <Crater active={active} />
                    <Crater active={active} />
                </Moon>
            </Switch>
            <Clouds active={active}>
                <Cloud />
                <Cloud />
                <Cloud />
                <Cloud />
                <Cloud />
                <Cloud />
            </Clouds>
            <Stars active={active}>
                <Star active={active} />
                <Star active={active} />
                <Star active={active} />
                <Star active={active} />
                <Star active={active} />
                <Star active={active} />
            </Stars>
        </Wrapper>
    );
}

const Moon = styled.div`

`;


const activeWrapper = ({active}) => active ? ({
    background: "#09f"
}) : null;

const Wrapper = styled.div`
    height: 6em;
    width: 14em;
    background: #3c4145;
    position: absolute;
    overflow: hidden;
    top: 50%;
    left: 50%;
    transform: translate(-50%,-50%);
    border-radius: 4em;
    transition: all 0.35s ease;
    &:hover {
        cursor: pointer;
    }
    ${activeWrapper}
`
const activeSwitch = ({active}) => active ? ({
    left: "8em",
    background: "#ffdf6d",
    borderColor: "#e1c448"
}) : null;

const Switch = styled.div`
    position: absolute;
    z-index: 2;
    transition: all 0.35s ease;
    margin: 0.5em;
    height: 5em;
    width: 5em;
    left: 0;
    border-radius: 3em;
    background: #ffffff;
    border: 0.4em solid #333;
    box-sizing: border-box;
    border-color: #e3e7c7;
    ${activeSwitch}
`;

const activeCrater = ({active}) => active ? ({
    display: "none"
}) : null

const Crater = styled.div`
    position: absolute;
    border-radius: 50%;
    height: 25%;
    width: 25%;
    background: transparent;
    box-shadow: inset 0 0 0 4px #e3e7c7;
    &:nth-child(1) {
        top: 12%;
        left: 22%;
    }
    &:nth-child(2) {
        top: 49%;
        left: 10%;
        transform: scale(0.7);
    }
    &:nth-child(3) {
        top: 50%;
        left: 60%;
    }
    ${activeCrater}
`;

const StarsAndsCloudsBase = styled.div`
    position: absolute;
    height: 100%;
    width: 66%;
    z-index: 1;
    transition: all 0.35s ease;
`;


const Cloud = styled.div`
    position: absolute;
      background: #fff;
      border-radius: 999px;
      &:nth-child(1) {
        top: 45%;
        left: 25%;
        width: 50%;
        height: 25%;
        border-radius: 999px;
      }
      &:nth-child(2) {
        top: 30%;
        left: 52%;
        width: 15%;
        padding-bottom: 15%;
        height: 0;
      }
      &:nth-child(3) {
        top: 24%;
        left: 32%;
        width: 25%;
        padding-bottom: 25%;
        height: 0;
      }
    }
`;

const Clouds = styled(StarsAndsCloudsBase) <{active: boolean}>`
    left: ${props => props.active ? "-10%" : 0};
    opacity: ${props => props.active ? 1 : 0};
`;




const activeStar = ({active}) => active ? ({
    transform: "scale(0) !important",
    transition: "all 0.7s ease"
}) : null;

const Star = styled.div`
    position: absolute;
    height: 0;
    width: 6%;
    padding-bottom: 6%;
    border-radius: 50%;
    background: #fff;

    &:nth-child(1) {
        top: 26%;
        left: 64%;
        transform: scale(0.6);
        transition-delay: 0.2s;
    }
    &:nth-child(2) {
        top: 20%;
        left: 34%;
        transform: scale(0.7);
        transition-delay: 0.4s;
    }
    &:nth-child(3) {
        top: 38%;
        left: 18%;
        transform: scale(0.4);
        transition-delay: 0.65s;
    }
    &:nth-child(4) {
        top: 67%;
        left: 30%;
        transform: scale(0.55);
        transition-delay: 0.85s;
    }
    &:nth-child(5) {
        top: 49%;
        left: 48%;
        transform: scale(0.7);
        transition-delay: 1s;
    }
    &:nth-child(6) {
        top: 68%;
        left: 72%;
        transform: scale(0.7);
        transition-delay: 1.05s;
    }

    ${activeStar}
`;


const Stars = styled(StarsAndsCloudsBase) <{active: boolean}>`
    right: ${props => props.active ? "-10%" : 0};
    opacity: ${props => props.active ? 0 : 1};
`;