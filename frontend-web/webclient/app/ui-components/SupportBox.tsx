import * as React from "react";
import { Cloud } from "Authentication/SDUCloudObject";
import { successNotification, failureNotification } from "UtilityFunctions";
import TextArea from "./TextArea";
import { KeyCode } from "DefaultObjects";
import Flex from "./Flex";
import Box from "./Box";
import Icon from "./Icon";
import Button from "./Button";
import * as Heading from "ui-components/Heading";
import ClickableDropdown from "./ClickableDropdown";

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
        return (
            <ClickableDropdown colorOnHover={false} keepOpenOnClick trigger={
                <Flex width="48px" justifyContent="center" >
                    <Icon name={"chat"} size="24px" color="headerIconColor" color2={"headerBg"} />
                </Flex>
            }
                width={"650px"}
                height={"350px"}
                right="10px"
                top="37px"
            >
                <Box color="text">
                    <Heading.h3>Support Form</Heading.h3>
                    <p>Describe your problem below and we will investigate it.</p>
                    <form onSubmit={e => this.onSubmit(e)}>
                        <TextArea width="100%" ref={this.textArea} rows={6} />
                        <Button mt="0.4em" fullWidth type="submit" disabled={this.state.loading}>
                            <Icon name="mail" size="1em" mr=".5em" color2="midGray" />
                            Send
                        </Button>
                    </form>
                </Box>
            </ClickableDropdown>);
    }
}

export default Support;