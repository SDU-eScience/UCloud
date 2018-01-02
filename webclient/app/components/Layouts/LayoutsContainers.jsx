import React from 'react';
import pubsub from 'pubsub-js';

class LayoutsContainers extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    render() {
        return (
            <section>
                <div className="container-fluid text-center">
                    <h4>Fluid container</h4>
                    <p><code>.container-fluid</code></p>
                    <div className="container container-lg b">
                        <h4 className="text-center">Large container</h4>
                        <p><code>.container.container-lg</code></p>
                        <div className="container container-md b">
                            <h4 className="text-center">Medium container</h4>
                            <p><code>.container.container-md</code></p>
                            <div className="container container-sm b">
                                <h4 className="text-center">Small container</h4>
                                <p><code>.container.container-sm</code></p>
                            </div>
                        </div>
                    </div>
                </div>
            </section>
        );
    }
}

export default LayoutsContainers;
