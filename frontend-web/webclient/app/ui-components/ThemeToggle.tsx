import * as React from "react";
import Relative from "./Relative";
import {injectStyle} from "@/Unstyled";

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

export function ThemeToggler({isLightTheme, onClick}: {
    isLightTheme: boolean;
    onClick: (e: React.SyntheticEvent<HTMLDivElement>) => void
}): JSX.Element {
    function toggleActive(): void {
        setActive(!active);
    }

    const [active, setActive] = React.useState<boolean>(isLightTheme);
    return (
        <Relative onClick={onClick} marginLeft="auto" marginRight="auto">
            <div className={Wrapper} onClick={toggleActive} data-active={active}>
                <div className={Switch} data-active={active}>
                    <div className={Moon}>
                        <div className={Crater} data-active={active}/>
                        <div className={Crater} data-active={active}/>
                        <div className={Crater} data-active={active}/>
                    </div>
                </div>
                <div className={StarsAndsCloudsBase + " " + Clouds} data-active={active}>
                    <div className={Cloud}/>
                    <div className={Cloud}/>
                    <div className={Cloud}/>
                </div>
                <div className={StarsAndsCloudsBase + " " + Stars} data-active={active}>
                    <div className={Star} data-active={active}/>
                    <div className={Star} data-active={active}/>
                    <div className={Star} data-active={active}/>
                    <div className={Star} data-active={active}/>
                    <div className={Star} data-active={active}/>
                    <div className={Star} data-active={active}/>
                </div>
            </div>
        </Relative>
    );
}

const Moon = injectStyle("moon", k => ``);

const Wrapper = injectStyle("switch-wrapper", k => `
    ${k} {
        height: 1.2em;
        width: 2.8em;
        background: #3c4145;
        overflow: hidden;
        border-radius: 0.4em;
        transition: all 0.35s ease;
    }
    
    ${k}:hover {
        cursor: pointer;
    }
    
    ${k}[data-active="true"] {
        background: #3f97ff;
    }
`);

const Switch = injectStyle("switch", k => `
    ${k} {
        position: absolute;
        z-index: 2;
        transition: all 0.35s ease;
        margin: 0.1em;
        height: 1em;
        width: 1em;
        left: 0.1em;
        border-radius: 50%;
        background: #ffffff;
        border: 0.15em solid #333;
        box-sizing: border-box;
        border-color: #e3e7c7;
    }
    
    ${k}[data-active="true"] {
        left: 1.5em;
        background: $ffdf6d;
        border-color: #e1c448;
    }
`);

const Crater = injectStyle("crater", k => `
    ${k} {
        position: absolute;
        border-radius: 50%;
        height: 25%;
        width: 25%;
        background: transparent;
        box-shadow: inset 0 0 0 4px #e3e7c7;
    }
    
    ${k}:nth-child(1) {
        top: 12%;
        left: 22%;
    }
    
    ${k}:nth-child(2) {
        top: 49%;
        left: 10%;
        transform: scale(0.7);
    }
    
    ${k}:nth-child(3) {
        top: 50%;
        left: 60%;
    }
    
    ${k}[data-active="true"] {
        display: none;
    }
`);

const StarsAndsCloudsBase = injectStyle("stars-and-clouds", k => `
    ${k} {
        position: absolute;
        height: 100%;
        width: 66%;
        z-index: 1;
        transition: all 0.35s ease;
    }
`);

const Cloud = injectStyle("cloud", k => `
    ${k} {
        position: absolute;
        background: #fff;
        border-radius: 999px;
    }
    
    ${k}:nth-child(1) {
        top: 45%;
        left: 25%;
        width: 50%;
        height: 30%;
        border-radius: 999px;
    }
    
    ${k}:nth-child(2) {
        top: 30%;
        left: 52%;
        width: 15%;
        padding-bottom: 15%;
        height: 0;
    }
    
    ${k}:nth-child(3) {
        top: 24%;
        left: 32%;
        width: 25%;
        padding-bottom: 25%;
        height: 0;
    }
`);

const Clouds = injectStyle("clouds", k => `
    ${k} {
        left: -5%;
        opacity: 0;
    }
    
    ${k}[data-active="true"] {
        opacity: 1;
    }
`);

const Star = injectStyle("star", k => `
    ${k} {
        position: absolute;
        height: 3px;
        width: 3px;
        border-radius: 50%;
        background: #fff;
    }

    ${k}:nth-child(1) {
        top: 26%;
        left: 64%;
        transform: scale(0.6);
        transition-delay: 0.2s;
    }
    
    ${k}:nth-child(2) {
        top: 20%;
        left: 34%;
        transform: scale(0.7);
        transition-delay: 0.4s;
    }
    
    ${k}:nth-child(3) {
        top: 38%;
        left: 18%;
        transform: scale(0.4);
        transition-delay: 0.65s;
    }
    
    ${k}:nth-child(4) {
        top: 67%;
        left: 30%;
        transform: scale(0.55);
        transition-delay: 0.85s;
    }
    
    ${k}:nth-child(5) {
        top: 49%;
        left: 48%;
        transform: scale(0.7);
        transition-delay: 1s;
    }
    
    ${k}:nth-child(6) {
        top: 68%;
        left: 72%;
        transform: scale(0.7);
        transition-delay: 1.05s;
    }
    
    ${k}[data-active="true"] {
        transform: scale(0) !important;
        transition: all 0.7s ease;
    }
`);

const Stars = injectStyle("stars", k => `
    ${k} {
        right: 0;
        opacity: 1;
    }
    
    ${k}[data-active="true"] {
        opacity: 0;
    }
`);
