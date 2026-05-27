import * as React from "react";
import {usePage} from "@/Navigation/Redux";
import {SidebarTabId} from "@/ui-components/SidebarComponents";
import {Client} from "@/Authentication/HttpClientInstance";
import {MainContainer} from "@/ui-components";
import UserCreation from "@/Admin/UserCreation";

function ManageProjects(): React.ReactNode {
    usePage("Manage projects", SidebarTabId.ADMIN);

    if (!Client.userIsAdmin) return null;

    return <MainContainer
        main={<>
            <h3 className="title">Project Management</h3>
            <p>Admins can manage projects on this page.</p>

        </>
        }>

    </MainContainer>
}

export default ManageProjects;
