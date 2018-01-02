import React from 'react';


function Status(props) {
  return (<button className="btn btn-info">Status goes here!</button>)
}

function TopnavbarComponent(props) {
  return (
    <header className="header-container">
      <nav>
        <ul className="visible-xs visible-sm">
          <li><a id="sidebar-toggler" href="#" className="menu-link menu-link-slide"><span><em/></span></a></li>
        </ul>
        <ul className="hidden-xs">
          <li><a id="offcanvas-toggler" href="#" className="menu-link menu-link-slide"><span><em/></span></a></li>
        </ul>
        <h2 className="header-title">{props.title}</h2>
        <ul className="pull-right">
          <li>
            <Status/>
          </li>
          <li><a id="header-search" href="#" className="ripple"><em className="ion-ios-search-strong"/></a></li>
          <li className="dropdown"><a href="#" data-toggle="dropdown" className="dropdown-toggle has-badge ripple"><em
            className="ion-person"/><sup className="badge bg-danger"/></a>
            <ul className="dropdown-menu dropdown-menu-right md-dropdown-menu">
              <li><a href="/logout"><em className="ion-log-out icon-fw"/>Logout</a></li>
            </ul>
          </li>
        </ul>

      </nav>
    </header>)
}

export {TopnavbarComponent}
