// FIXME START: Should be retrieved from DB
const DashboardOption = {
  name: "Dashboard",
  icon: "nav-icon",
  href: "/dashboard",
  children: null
};

const FilesOptions = {
  name: "Files",
  icon: "nav-icon",
  href: "/files",
  children: null
};

const ProjectsOption = {
  name: "Projects",
  icon: "nav-icon",
  href: "/projects",
  children: null
};

const AppsApplicationsOption = {
  name: "Applications",
  icon: "",
  href: "/applications",
  children: null
};

const AppsWorkflowsOption = {
  name: "Workflows",
  icon: "",
  href: "/workflows",
  children: null,
};

const AppsAnalysesOption = {
  name: "Analyses",
  icon: "",
  href: "/analyses",
  children: null,
};

const AppsOptions = {
  name: "Apps",
  icon: "",
  href: "",
  children: [AppsApplicationsOption, AppsWorkflowsOption, AppsAnalysesOption]
};

const ActivityNotificationsOption = {
  name: "Notifications",
  icon: "",
  href: "/activity/notications"
};

const ActivityOptions = {
  name: "Activity",
  icon: "",
  href: "",
  children: [ActivityNotificationsOption]
};

const SidebarOptionsList = [
  DashboardOption, FilesOptions, ProjectsOption, AppsOptions, ActivityOptions
];
// FIXME END

import React from 'react';

class SidebarComponent extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      options: SidebarOptionsList,
      name: ""
    }
  }

  componentDidMount() {
    console.log("It's been mounted")
    this.getName();
  }

  getName() {
    this.setState({name: "Jonas"})
  }


  render() {
    return (
      <aside className="sidebar-container bg-white">
        <div className="sidebar-header">
          <div className="pull-right pt-lg text-muted hidden"><em className="ion-close-round"></em></div>
          <a href="#" className="sidebar-header-logo"><img src="img/logo.png" data-svg-replace="img/logo.svg"
                                                           alt="Logo"/><span
            className="sidebar-header-logo-text">Centric</span></a>
        </div>
        <div className="sidebar-content">
          <div className="sidebar-toolbar text-center">
            <a href=""><img src="img/user/01.jpg" alt="Profile" className="img-circle thumb64"/></a>
            <div className="mt">Welcome, Willie Webb</div>
          </div>
          <nav className="sidebar-nav">
            <SidebarOptions sidebarOptions={this.state.options}/>
          </nav>
        </div>
      </aside>)

  }
}

function SidebarOptions(props) {
  let options = props.sidebarOptions.slice();
  let i = 0;
  let optionsList = options.map(option =>
    <SingleSidebarOption key={i++} option={option}/>
  );
  return (
    <ul>
      {optionsList}
    </ul>)

}

function SingleSidebarOption(props) {
  if (props.option.href) {
    return (
      <li><a href={props.option.href} className="ripple"><span className="pull-right nav-label"/>
        <span className="nav-icon"/><img src=""
                                         data-svg-replace="/img/icons/aperture.svg"
                                         alt="MenuItem"
                                         className="hidden"/><span>{props.option.name}</span>
      </a></li>);
  } else {
    let children = props.option.children.slice();
    let i = 0;
    let optionsList = children.map(option =>
      <SingleSidebarOption key={i++} option={option}/>
    );

    return (
      <ul>
        <li><a href="#" className="ripple"><span className="pull-right nav-caret"><em
          className="ion-ios-arrow-right"/></span><span className="pull-right nav-label"/><span
          className="nav-icon"/><img src="" data-svg-replace="/img/icons/connection-bars.svg" alt="MenuItem"
                                     className="hidden"/><span className="gray">{props.option.name}</span></a>
          <ul className="sidebar-subnav">
            {optionsList}
          </ul>
        </li>
      </ul>)
  }
}

export {SidebarComponent}
