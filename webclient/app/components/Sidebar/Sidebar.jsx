import React from 'react';
import { Link } from 'react-router'

import './Sidebar.scss';

import SidebarRun from './Sidebar.run';
import {initSvgReplace} from '../Utils/Utils';

class Sidebar extends React.Component {

    constructor(props, context){
        super(props, context);
    }

    componentDidMount() {
        SidebarRun();
        initSvgReplace();
    }

    routeActive(paths) {
        paths = Array.isArray(paths) ? paths : [paths];
        for (let p in paths) {
            if (this.context.router.isActive(''+paths[p]) === true)
                return 'active';
        }
        return '';
    }

    render() {
        return (
            <aside className="sidebar-container">
                <div className="sidebar-header">
                    <div className="pull-right pt-lg text-muted hidden"><em className="ion-close-round"></em></div>
                    <a href="#" className="sidebar-header-logo"><img src="img/logo.png" data-svg-replace="img/logo.svg" alt="Logo" /><span className="sidebar-header-logo-text">Centric</span></a>
                </div>
                <div className="sidebar-content">
                    <div className="sidebar-toolbar text-center">
                        <a href=""><img src="img/user/01.jpg" alt="Profile" className="img-circle thumb64" /></a>
                        <div className="mt">Welcome, Willie Webb</div>
                    </div>
                    <nav className="sidebar-nav">
                        <ul>
                            <li className={this.routeActive('/dashboard') ? 'active':''}>
                                <Link to="dashboard" className="ripple">
                                    <span className="pull-right nav-label"><span className="badge bg-success">2</span></span><span className="nav-icon">
                                    <img src="" data-svg-replace="img/icons/aperture.svg" alt="MenuItem" className="hidden" /></span>
                                    <span>Dashboard</span>
                                </Link>
                            </li>
                            <li className={this.routeActive('/cards') ? 'active':''}>
                                <Link to="cards" className="ripple">
                                    <span className="pull-right nav-label"></span><span className="nav-icon">
                                    <img src="" data-svg-replace="img/icons/radio-waves.svg" alt="MenuItem" className="hidden" /></span>
                                    <span>Cards</span>
                                </Link>
                            </li>
                            <li className={this.routeActive(['charts/flot','charts/radial','charts/rickshaw'])}>
                                <a href="#" className="ripple">
                                    <span className="pull-right nav-caret"><em className="ion-ios-arrow-right"></em></span><span className="pull-right nav-label"></span><span className="nav-icon">
                                    <img src="" data-svg-replace="img/icons/connection-bars.svg" alt="MenuItem" className="hidden" /></span>
                                    <span>Charts</span>
                                </a>
                                <ul className="sidebar-subnav">
                                    <li className={this.routeActive(['charts/flot'])}>
                                        <Link to='/charts/flot' className="ripple">
                                            <span className="pull-right nav-label"></span><span>Flot</span></Link>
                                    </li>
                                    <li className={this.routeActive(['charts/radial'])}>
                                        <Link to='/charts/radial' className="ripple">
                                            <span className="pull-right nav-label"></span><span>Radial</span></Link>
                                    </li>
                                    <li className={this.routeActive(['charts/rickshaw'])}>
                                        <Link to='/charts/rickshaw' className="ripple">
                                            <span className="pull-right nav-label"></span><span>Rickshaw</span></Link>
                                    </li>
                                </ul>
                            </li>
                            <li className={this.routeActive(['forms/dropzone', 'forms/editor', 'forms/advanced', 'forms/classic', 'forms/material', 'forms/validation', 'forms/wizard', 'forms/xeditable'])}>
                                <a href="#" className="ripple">
                                    <span className="pull-right nav-caret"><em className="ion-ios-arrow-right"></em></span><span className="pull-right nav-label"></span><span className="nav-icon">
                                    <img src="" data-svg-replace="img/icons/clipboard.svg" alt="MenuItem" className="hidden" /></span>
                                    <span>Forms</span>
                                </a>
                                <ul className="sidebar-subnav">
                                    <li className={this.routeActive('forms/classic')} >
                                        <Link to="forms/classic" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Classic</span>
                                        </Link>
                                    </li>
                                    <li className={this.routeActive('forms/validation')} >
                                        <Link to="forms/validation" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Validation</span>
                                        </Link>
                                    </li>
                                    <li className={this.routeActive('forms/advanced')} >
                                        <Link to="forms/advanced" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Advanced</span>
                                        </Link>
                                    </li>
                                    <li className={this.routeActive('forms/material')} >
                                        <Link to="forms/material" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Material</span>
                                        </Link>
                                    </li>
                                    <li className={this.routeActive('forms/editor')} >
                                        <Link to="forms/editor" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Editors</span>
                                        </Link>
                                    </li>
                                    <li className={this.routeActive('forms/dropzone')} >
                                        <Link to="forms/dropzone" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Dropzone</span>
                                        </Link>
                                    </li>
                                    <li className={this.routeActive('forms/xeditable')} >
                                        <Link to="forms/xeditable" className="ripple">
                                            <span className="pull-right nav-label"></span><span>xEditable</span>
                                        </Link>
                                    </li>
                                    <li className={this.routeActive('forms/wizard')} >
                                        <Link to="forms/wizard" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Wizard</span>
                                        </Link>
                                    </li>
                                </ul>
                            </li>
                            <li className={this.routeActive(['tables/tableclassic','tables/datatable','tables/bootgrid'])}>
                                <a href="#" className="ripple">
                                    <span className="pull-right nav-caret"><em className="ion-ios-arrow-right"></em></span><span className="pull-right nav-label"></span><span className="nav-icon">
                                    <img src="" data-svg-replace="img/icons/navicon.svg" alt="MenuItem" className="hidden" /></span>
                                    <span>Tables</span>
                                </a>
                                <ul id="tables" className="sidebar-subnav">
                                    <li className={this.routeActive(['tables/tableclassic'])}>
                                        <Link to="tables/tableclassic" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Classic</span>
                                        </Link>
                                    </li>
                                    <li className={this.routeActive(['tables/datatable'])}>
                                        <Link to="tables/datatable" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Datatable</span>
                                        </Link>
                                    </li>
                                    <li className={this.routeActive('tables/bootgrid')}>
                                        <Link to="tables/bootgrid" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Bootgrid</span>
                                        </Link>
                                    </li>
                                </ul>
                            </li>
                            <li className={this.routeActive(['layouts/boxed','layouts/columns','layouts/containers','layouts/overlap','layouts/tabs'])}>
                                <a href="#" className="ripple">
                                    <span className="pull-right nav-caret"><em className="ion-ios-arrow-right"></em></span><span className="pull-right nav-label"></span><span className="nav-icon">
                                    <img src="" data-svg-replace="img/icons/grid.svg" alt="MenuItem" className="hidden" /></span>
                                    <span>Layouts</span>
                                </a>
                                <ul id="layouts" className="sidebar-subnav">
                                    <li className={this.routeActive('layouts/columns')}>
                                        <Link to="layouts/columns" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Columns</span>
                                        </Link>
                                    </li>
                                    <li className={this.routeActive('layouts/overlap')}>
                                        <Link to="layouts/overlap" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Overlap</span>
                                        </Link>
                                    </li>
                                    <li className={this.routeActive('layouts/boxed')}>
                                        <Link to="layouts/boxed" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Boxed</span>
                                        </Link>
                                    </li>
                                    <li className={this.routeActive('layouts/tabs')}>
                                        <Link to="layouts/tabs" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Tabs</span>
                                        </Link>
                                    </li>
                                    <li className={this.routeActive('layouts/containers')}>
                                        <Link to="layouts/containers" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Containers</span>
                                        </Link>
                                    </li>
                                </ul>
                            </li>
                            <li className={this.routeActive(['elements/colors','elements/whiteframes','elements/lists','elements/bootstrap','elements/buttons','elements/sweetalert','elements/spinners','elements/nestable','elements/grid','elements/gridmasonry','elements/typography','elements/icons','elements/utilities'])}>
                                <a href="#" className="ripple">
                                    <span className="pull-right nav-caret">
                                        <em className="ion-ios-arrow-right"></em>
                                    </span>
                                    <span className="pull-right nav-label"></span>
                                    <span className="nav-icon">
                                        <img src="" data-svg-replace="img/icons/levels.svg" alt="MenuItem" className="hidden" />
                                    </span>
                                    <span>Elements</span>
                                </a>
                                <ul id="elements" className="sidebar-subnav">
                                    <li className={this.routeActive('elements/colors')}>
                                        <Link to="elements/colors" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Colors</span></Link>
                                    </li>
                                    <li className={this.routeActive('elements/whiteframes')}>
                                        <Link to="elements/whiteframes" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Whiteframes</span></Link>
                                    </li>
                                    <li className={this.routeActive('elements/lists')}>
                                        <Link to="elements/lists" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Lists</span></Link>
                                    </li>
                                    <li className={this.routeActive('elements/bootstrap')}>
                                        <Link to="elements/bootstrap" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Bootstrap</span></Link>
                                    </li>
                                    <li className={this.routeActive('elements/buttons')}>
                                        <Link to="elements/buttons" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Buttons</span></Link>
                                    </li>
                                    <li className={this.routeActive('elements/sweetalert')}>
                                        <Link to="elements/sweetalert"><span className="pull-right nav-label"></span><span>Sweet-alert</span></Link>
                                    </li>
                                    <li className={this.routeActive('elements/spinners')}>
                                        <Link to="elements/spinners" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Spinners</span></Link>
                                    </li>
                                    <li className={this.routeActive('elements/nestable')}>
                                        <Link to="elements/nestable" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Nestable</span></Link>
                                    </li>
                                    <li className={this.routeActive('elements/grid')}>
                                        <Link to="elements/grid" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Grid</span></Link>
                                    </li>
                                    <li className={this.routeActive('elements/gridmasonry')}>
                                        <Link to="elements/gridmasonry" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Grid Masonry</span></Link>
                                    </li>
                                    <li className={this.routeActive('elements/typography')}>
                                        <Link to="elements/typography" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Typography</span></Link>
                                    </li>
                                    <li className={this.routeActive('elements/icons')}>
                                        <Link to="elements/icons" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Icons</span></Link>
                                    </li>
                                    <li className={this.routeActive('elements/utilities')}>
                                        <Link to="elements/utilities" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Utilities</span></Link>
                                    </li>
                                </ul>
                            </li>
                            <li className={this.routeActive(['maps/google','maps/googlefull','maps/vector', 'maps/datamaps'])}>
                                <a href="#" className="ripple">
                                    <span className="pull-right nav-caret">
                                        <em className="ion-ios-arrow-right"></em>
                                    </span>
                                    <span className="pull-right nav-label"></span>
                                    <span className="nav-icon">
                                        <img src="" data-svg-replace="img/icons/planet.svg" alt="MenuItem" className="hidden" />
                                    </span>
                                    <span>Maps</span>
                                </a>
                                <ul id="maps" className="sidebar-subnav">
                                    <li className={this.routeActive(['maps/googlefull'])}>
                                        <Link to="maps/googlefull" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Google Maps Full</span>
                                        </Link>
                                    </li>
                                    <li className={this.routeActive(['maps/google'])}>
                                        <Link to="maps/google" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Google Maps</span>
                                        </Link>
                                    </li>
                                    <li className={this.routeActive(['maps/vector'])}>
                                        <Link to="maps/vector" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Vector Maps</span>
                                        </Link>
                                    </li>
                                    <li className={this.routeActive(['maps/datamaps'])}>
                                        <Link to="maps/datamaps" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Datamaps</span>
                                        </Link>
                                    </li>
                                </ul>
                            </li>
                            <li className={this.routeActive(['pages/blog','pages/blogarticle','pages/contacts','pages/faq','pages/gallery','pages/invoice','pages/messages','pages/pricing','pages/profile','pages/projects','pages/search','pages/timeline','pages/wall'])}>
                                <a href="#" className="ripple">
                                    <span className="pull-right nav-caret"><em className="ion-ios-arrow-right"></em></span><span className="pull-right nav-label"></span><span className="nav-icon">
                                    <img src="" data-svg-replace="img/icons/ios-browsers.svg" alt="MenuItem" className="hidden" /></span>
                                    <span>Pages</span>
                                </a>
                                <ul id="pages" className="sidebar-subnav">
                                    <li className={this.routeActive('timeline')}>
                                        <Link to="pages/timeline" className="ripple">
                                            <span className="pull-right nav-label"></span>
                                            <span>Timeline</span>
                                        </Link>
                                    </li>
                                    <li className={this.routeActive('pages/invoice')}>
                                        <Link to="pages/invoice" className="ripple">
                                            <span className="pull-right nav-label"></span>
                                            <span>Invoice</span>
                                        </Link>
                                    </li>
                                    <li className={this.routeActive('pages/pricing')}>
                                        <Link to="pages/pricing" className="ripple">
                                            <span className="pull-right nav-label"></span>
                                            <span>Pricing</span>
                                        </Link>
                                    </li>
                                    <li className={this.routeActive('pages/contacts')}>
                                        <Link to="pages/contacts" className="ripple">
                                            <span className="pull-right nav-label"></span>
                                            <span>Contacts</span>
                                        </Link>
                                    </li>
                                    <li className={this.routeActive('pages/faq')}>
                                        <Link to="pages/faq" className="ripple">
                                            <span className="pull-right nav-label"></span>
                                            <span>FAQ</span>
                                        </Link>
                                    </li>
                                    <li className={this.routeActive('pages/projects')}>
                                        <Link to="pages/projects" className="ripple">
                                            <span className="pull-right nav-label"></span>
                                            <span>Projects</span>
                                        </Link>
                                    </li>
                                    <li className={this.routeActive('pages/blog')}>
                                        <Link to="pages/blog" className="ripple">
                                            <span className="pull-right nav-label"></span>
                                            <span>Blog</span>
                                        </Link>
                                    </li>
                                    <li className={this.routeActive('pages/blog-article')}>
                                        <Link to="pages/blogarticle" className="ripple">
                                            <span className="pull-right nav-label"></span>
                                            <span>Article</span>
                                        </Link>
                                    </li>
                                    <li className={this.routeActive('pages/profile')}>
                                        <Link to="pages/profile" className="ripple">
                                            <span className="pull-right nav-label"></span>
                                            <span>Profile</span>
                                        </Link>
                                    </li>
                                    <li className={this.routeActive('pages/gallery')}>
                                        <Link to="pages/gallery" className="ripple">
                                            <span className="pull-right nav-label"></span>
                                            <span>Gallery</span>
                                        </Link>
                                    </li>
                                    <li className={this.routeActive('pages/wall')}>
                                        <Link to="pages/wall" className="ripple">
                                            <span className="pull-right nav-label"></span>
                                            <span>Wall</span>
                                        </Link>
                                    </li>
                                    <li className={this.routeActive('pages/search')}>
                                        <Link to="pages/search" className="ripple">
                                            <span className="pull-right nav-label"></span>
                                            <span>Search</span>
                                        </Link>
                                    </li>
                                    <li className={this.routeActive('pages/messages')}>
                                        <Link to="pages/messages" className="ripple">
                                            <span className="pull-right nav-label"></span>
                                            <span>Messages Board</span>
                                        </Link>
                                    </li>
                                </ul>
                            </li>
                            <li>
                                <a href="#" className="ripple">
                                    <span className="pull-right nav-caret"><em className="ion-ios-arrow-right"></em></span><span className="pull-right nav-label"></span><span className="nav-icon">
                                    <img src="" data-svg-replace="img/icons/person-stalker.svg" alt="MenuItem" className="hidden" /></span>
                                    <span>User</span>
                                </a>
                                <ul id="user" className="sidebar-subnav">
                                    <li>
                                        <Link to="login" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Login</span>
                                        </Link>
                                    </li>
                                    <li>
                                        <Link to="signup" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Signup</span>
                                        </Link>
                                    </li>
                                    <li>
                                        <Link to="lock" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Lock</span>
                                        </Link>
                                    </li>
                                    <li>
                                        <Link to="recover" className="ripple">
                                            <span className="pull-right nav-label"></span><span>Recover</span>
                                        </Link>
                                    </li>
                                </ul>
                            </li>
                        </ul>
                    </nav>
                </div>
            </aside>
        );
    }
}

Sidebar.contextTypes = {
    router: React.PropTypes.object
};

export default Sidebar;
