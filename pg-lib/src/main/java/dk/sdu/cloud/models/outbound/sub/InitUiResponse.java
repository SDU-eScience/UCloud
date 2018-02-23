package dk.sdu.cloud.models.outbound.sub;

import dk.sdu.cloud.models.outbound.InitUi;

public class InitUiResponse {

    private String rc;
    private InitUi initUi;

    public String getRc() {
        return rc;
    }

    public void setRc(String rc) {
        this.rc = rc;
    }

    public InitUi getInitUi() {
        return initUi;
    }

    public void setInitUi(InitUi initUi) {
        this.initUi = initUi;
    }
}
