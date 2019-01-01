import * as React from "react";
import styled from "styled-components";
import { BoxShadowProps, boxShadow } from "styled-system"
import { Cloud } from "Authentication/SDUCloudObject";
import { successNotification, failureNotification } from "UtilityFunctions";
import Relative from "./Relative";
import TextArea from "./TextArea";
import { KeyCode } from "DefaultObjects";
import Flex from "./Flex";
import Box from "./Box";
import Icon from "./Icon";
import Button from "./Button";
import * as Heading from "ui-components/Heading";
import Link from "./Link";


interface SupportBoxProps extends BoxShadowProps { visible: boolean }
const SupportBox = styled.div<SupportBoxProps>`
    display: ${props => props.visible ? "block" : "none"};
    position: absolute;
    right: -100px;
    top: 12px;
    border: 1px solid ${props => props.theme.colors.borderGray};
    border-radius: 5px;
    background-color: ${props => props.theme.colors.white};
    ${boxShadow}

    &&&&&&&&&&& {
        width: 600px;
        height: 350px;
    }

    // &:before {
    //     display: block;
    //     width: 16px;
    //     height: 16px;
    //     content: '';
    //     transform: rotate(45deg);
    //     position: relative;
    //     top: 300px;
    //     left: -9px;
    //     background: ${props => props.theme.colors.white};
    //     border-left: 1px solid ${props => props.theme.colors.borderGray};
    //     border-bottom: 1px solid ${props => props.theme.colors.borderGray};
    // }

    & ${TextArea} {
        width: 100%;
        border: 1px solid ${props => props.theme.colors.borderGray};
    }

    & ${Box} {
        margin: 16px;
        overflow-y: auto;
        height: calc(100% - 32px);
        width: calc(100% - 32px);
    }
`;

interface SupportState {
    visible: boolean
    loading: boolean
}

class Support extends React.Component<{}, SupportState> {
    private textArea = React.createRef<HTMLTextAreaElement>();
    private supportBox = React.createRef<HTMLDivElement>();

    constructor(props) {
        super(props);

        this.state = {
            visible: false,
            loading: false
        };
        document.addEventListener("keydown", this.handleESC);
        document.addEventListener("mousedown", this.handleClickOutside);
    }

    componentWillUnmount = () => {
        document.removeEventListener("keydown", this.handleESC);
        document.removeEventListener("mousedown", this.handleClickOutside);
    }

    private handleESC = (e) => {
        if (e.keyCode == KeyCode.ESC) this.setState(() => ({ visible: false }))
    }

    onSupportClick(event: React.SyntheticEvent) {
        event.preventDefault();
        this.setState(() => ({ visible: !this.state.visible }));
    }

    private handleClickOutside = event => {
        if (this.supportBox.current && !this.supportBox.current.contains(event.target) && this.state.visible)
            this.setState(() => ({ visible: false }));
    }

    onSubmit(event: React.FormEvent) {
        event.preventDefault();
        const text = this.textArea.current;
        if (!!text) {
            this.setState(() => ({ loading: true }));
            Cloud.post("/support/ticket", { message: text.value }).then(e => {
                text.value = "";
                this.setState(({ visible: false, loading: false }));
                successNotification("Support ticket submitted!");
            }).catch(e => {
                if (!!e.response.why) {
                    failureNotification(e.response.why);
                } else {
                    failureNotification("An error occured");
                }
            });
        }
    }

    render() {
        return <div>
            <Link to="#support" onClick={e => this.onSupportClick(e)}>
                <Flex width="48px" justifyContent="center" >
                    <Icon name={"chat"} size="24px" color="headerIconColor" color2={"headerBg"} />
                </Flex>
            </Link>
            <Relative>
                <SupportBox ref={this.supportBox} visible={this.state.visible} boxShadow="sm">
                    <Box color="text">
                        <Heading.h3>Support Form</Heading.h3>
                        <p>Describe your problem below and we will investigate it.</p>
                        <form onSubmit={e => this.onSubmit(e)}>
                            <TextArea ref={this.textArea} rows={6} />
                            <Button fullWidth type="submit" disabled={this.state.loading}>
                                <Icon name="mail" size="1em" mr=".5em" color2="midGray"/>
                                Send
                            </Button>
                        </form>
                    </Box>
                </SupportBox>
            </Relative>
        </div>;
    }
}

export default Support;