import * as React from "react";
import RFB from "@novnc/novnc/core/rfb";
import { MainContainer } from "MainContainer/MainContainer";
import { Button, Heading, OutlineButton } from "ui-components";
import { SnackType, AddSnackOperation } from "Snackbar/Snackbars";
import { connect } from "react-redux";
import { addSnack } from "Snackbar/Redux/SnackbarsActions";
import { Dispatch } from "redux";
import { errorMessageOrDefault } from "UtilityFunctions";

function NoVNCClient(props: AddSnackOperation) {
    const [isConnected, setConnected] = React.useState(false);
    const [rfb, setRFB] = React.useState<typeof RFB>(undefined);

    React.useEffect(() => () => {
        if (isConnected) rfb.disconnect();
    }, []);

    async function connect() {
        // FIXME: Not necessary when hooked up to backend
        const password = "vncpassword";
        // FIXME END
        try {
            const rfb = await new RFB(document.getElementsByClassName("noVNC")[0], "ws://127.0.0.1:6901", {
                credentials: { password }
            });

            /* FIXME: Doesn't seem to work properly, e.g. if connection fails */
            rfb.addEventListener("disconnect", () => setConnected(false));
            /* FIXME END */
            setRFB(rfb);
            setConnected(true);
        } catch (e) {
            props.addSnack({
                message: errorMessageOrDefault(e, "And error ocurred connecting"),
                type: SnackType.Failure
            });
        }
    }

    function disconnect() {
        rfb.disconnect();
        setConnected(false);
    }

    function requestFullScreen() {
        const el = document.getElementsByClassName("noVNC")[0]!;
        el.requestFullscreen();
    }

    const mountNode = <div className="noVNC" />
    const main = <>
        <Heading mb="5px">noVNC
        {isConnected ? <OutlineButton ml="15px" mr="10px" onClick={() => disconnect()}>
                Disconnect
        </OutlineButton> : <Button ml="15px" onClick={() => connect()}>
                Connect
        </Button>}
            {isConnected ? <OutlineButton onClick={() => requestFullScreen()}>Fullscreen</OutlineButton> : null}
        </Heading>
        {mountNode}
    </>;

    return <MainContainer
        main={main}
    />;
}

const mapDispatchToProps = (dispatch: Dispatch): AddSnackOperation => ({
    addSnack: snack => dispatch(addSnack(snack))
});

export default connect(null, mapDispatchToProps)(NoVNCClient);