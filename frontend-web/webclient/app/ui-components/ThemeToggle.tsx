import styled from "styled-components";
import * as React from "react";
import Relative from "./Relative";

/*!

    The MIT License (MIT)

    Copyright (c) <2019> <Erin Freeman> <https://codepen.io/efreeman79/pen/XgZPGO>

    Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated 
    documentation files (the "Software"), to deal in the Software without restriction, including without limitation 
    the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and
    to permit persons to whom the Software is furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in all copies or substantial portions of
    the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO 
    THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE 
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
    TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE 
    SOFTWARE.

 */

interface ThemeToggleProps {
    size: number
    active: boolean
}
export function ThemeToggler({isLightTheme}: {isLightTheme: boolean}) {
    const [active, setActive] = React.useState<boolean>(isLightTheme);
    return (
        <Relative top="10px" left="82px">
            <Wrapper size={1} onClick={() => setActive(!active)} active={active}>
                <Switch size={1} active={active}>
                    <Moon>
                        <Crater active={active}/>
                        <Crater active={active}/>
                        <Crater active={active}/>
                    </Moon>
                </Switch>
                <Clouds active={active}>
                    <Cloud/>
                    <Cloud/>
                    <Cloud/>
                    <Cloud/>
                    <Cloud/>
                    <Cloud/>
                </Clouds>
                <Stars active={active}>
                    <Star active={active}/>
                    <Star active={active}/>
                    <Star active={active}/>
                    <Star active={active}/>
                    <Star active={active}/>
                    <Star active={active}/>
                </Stars>
            </Wrapper>
        </Relative>
    );
}

const Moon = styled.div`

`;


const activeWrapper = ({active}: ThemeToggleProps) => active ? ({
    background: "#09f"
}) : null;

const Wrapper = styled.div<ThemeToggleProps>`
    height: ${p => p.size * 1.2}em;
    width: ${p => p.size * 2.8}em;
    background: #3c4145;
    overflow: hidden;
    top: 50%;
    left: 50%;
    transform: translate(-50%,-50%);
    border-radius: ${p => p.size * 0.8}em;
    transition: all 0.35s ease;
    &:hover {
        cursor: pointer;
    }
    ${activeWrapper}
`;

const activeSwitch = ({active, size}: ThemeToggleProps) => active ? ({
    left: `${size * 1.6}em`,
    background: "#ffdf6d",
    borderColor: "#e1c448"
}) : null;

const Switch = styled.div<ThemeToggleProps>`
    position: absolute;
    z-index: 2;
    transition: all 0.35s ease;
    margin: ${p => p.size * 0.1}em;
    height: ${p => p.size * 1}em;
    width: ${p => p.size * 1}em;
    left: 0;
    border-radius: ${p => p.size * 0.6}em;
    background: #ffffff;
    border: ${p => p.size * 0.08}em solid #333;
    box-sizing: border-box;
    border-color: #e3e7c7;
    ${activeSwitch}
`;

const activeCrater = ({active}) => active ? ({
    display: "none"
}) : null;

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