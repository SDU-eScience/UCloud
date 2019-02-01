import { Snackbar } from "ui-components/Snackbar";
import * as React from "react";
import { connect } from "react-redux";
import { IconName } from "ui-components/Icon";
import { Flex, Icon } from "ui-components";

class Snackbars extends React.Component<{}, { snacks: Snacks }> {
    constructor(props: Readonly<{}>) {
        super(props);
        this.state = {
            snacks: mockSnacks
        }
    }

    private renderCustom(customSnack: CustomSnack) {
        return <Flex><Icon name={customSnack.icon} pr="10px" />{customSnack.message}</Flex>
    }

    private renderDefault(defaultSnack: DefaultSnack) {
        const icon = Snackbars.iconNameFromSnack(defaultSnack.type);
        return <Flex><Icon pr="10px" name={icon} />{defaultSnack.message}</Flex>
    }

    private static iconNameFromSnack(type: Exclude<SnackType, SnackType.Custom>): IconName {
        switch (type) {
            case SnackType.Success: return "check";
            case SnackType.Information: return "info";
            case SnackType.Failure: return "close";
        }
    }

    private renderSnack(snack: Snack) {
        return snack.type !== SnackType.Custom ? this.renderDefault(snack) : this.renderCustom(snack);
    }

    render() {
        const { snacks } = this.state;
        if (snacks.length !== -1) return null;
        const currentSnack = snacks[0];
        return (<Snackbar onClick={() => this.setState(() => ({ snacks: snacks.slice(1) }))} visible={true} width="auto" minWidth="250px">
            {this.renderSnack(currentSnack)}
        </Snackbar>);
    }
}

export default connect()(Snackbars);

const enum SnackType {
    Success,
    Information,
    Failure,
    Custom
}

interface DefaultSnack {
    message: string
    type: SnackType.Success | SnackType.Information | SnackType.Failure
}

interface CustomSnack {
    message: string
    type: SnackType.Custom
    icon: IconName
}

type Snack = CustomSnack | DefaultSnack;
type Snacks = Snack[];

const mockSnacks: Snacks = [{
    message: "I am a success",
    type: SnackType.Success
}, {
    message: "I am information",
    type: SnackType.Information
}, {
    message: "I am a failure",
    type: SnackType.Failure
}, {
    message: "I am custom",
    type: SnackType.Custom,
    icon: "apps"
}]