import React from 'react';

class Layout extends React.Component {

    render() {
        return (
            <div className="layout-container">
                <div className="page-container bg-blue-grey-900">
                    {/* Page content */}
                    {React.cloneElement(this.props.children, {
                        key: Math.random()
                    })}
                </div>
            </div>
        );
    }
}

export default Layout;
