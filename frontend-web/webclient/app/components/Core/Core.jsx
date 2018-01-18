import React from 'react';
import ReactCSSTransitionGroup from 'react-addons-css-transition-group';

import './Core.scss';
import './LayoutVariants.scss';

import Header from '../Header/Header';
import HeaderSearch from '../Header/HeaderSearch';
import Sidebar from '../../SiteComponents/Sidebar';
import Settings from '../Settings/Settings';

class Core extends React.Component {
    render() {

        return (
            <div className="layout-container">

                <Header />

                <Sidebar />
                <div className="sidebar-layout-obfuscator"/>

                <div className="main-container">
                    {/* Page content */}
                    {React.cloneElement(this.props.children, {
                        key: this.props.location.pathname
                    })}

                    {/* Page footer */}
                    <footer>
                        <span>{new Date().getFullYear()} - SDUCloud.</span>
                    </footer>
                </div>
                {/* Search template */}
                <HeaderSearch/>

                {/* Settings template */}
                <Settings/>

                {/*> yield region="bodyChild"} */}

            </div>
        );
    }
}

export default Core;
