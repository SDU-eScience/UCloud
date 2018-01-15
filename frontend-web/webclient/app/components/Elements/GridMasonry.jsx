import React from 'react';
import pubsub from 'pubsub-js';

import './GridMasonry.scss';
import GridMasonryRun from './GridMasonry.run';

class GridMasonry extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    componentDidMount() {
        GridMasonryRun();
    }

    render() {
        return (
            <section>
                <div className="container container-md">
                    <div className="grid">
                        <div className="grid-sizer"></div>
                        <div className="grid-item">
                            <div className="photo">
                                <div className="photo-wrapper"><img src="img/user/01.jpg" alt="user image" className="img-responsive fw"/>
                                    <div className="photo-loader">
                                        <div className="sk-spinner sk-spinner-rotating-plane"></div>
                                    </div>
                                </div>
                                <div className="photo-description">
                                    <h3>Awesome large photo</h3>
                                    <p>Nice photo, eh.</p>
                                </div>
                            </div>
                        </div>
                        <div className="grid-item">
                            <div className="photo">
                                <div className="photo-wrapper"><img src="img/user/02.jpg" alt="user image" className="img-responsive fw"/>
                                    <div className="photo-loader">
                                        <div className="sk-spinner sk-spinner-rotating-plane"></div>
                                    </div>
                                </div>
                                <div className="photo-description">
                                    <h3>Great user photo</h3>
                                    <p>Nice photo, eh.</p>
                                </div>
                            </div>
                        </div>
                        <div className="grid-item">
                            <div className="photo">
                                <div className="photo-wrapper"><img src="img/user/03.jpg" alt="user image" className="img-responsive fw"/>
                                    <div className="photo-loader">
                                        <div className="sk-spinner sk-spinner-rotating-plane"></div>
                                    </div>
                                </div>
                                <div className="photo-description">
                                    <h3>Silly photo</h3>
                                    <p>Nice photo, eh.</p>
                                </div>
                            </div>
                        </div>
                        <div className="grid-item">
                            <div className="photo">
                                <div className="photo-wrapper"><img src="img/user/04.jpg" alt="user image" className="img-responsive fw"/>
                                    <div className="photo-loader">
                                        <div className="sk-spinner sk-spinner-rotating-plane"></div>
                                    </div>
                                </div>
                                <div className="photo-description">
                                    <h3>Is this a photo?</h3>
                                    <p>Nice photo, eh.</p>
                                </div>
                            </div>
                        </div>
                        <div className="grid-item">
                            <div className="photo">
                                <div className="photo-wrapper"><img src="img/user/05.jpg" alt="user image" className="img-responsive fw"/>
                                    <div className="photo-loader">
                                        <div className="sk-spinner sk-spinner-rotating-plane"></div>
                                    </div>
                                </div>
                                <div className="photo-description">
                                    <h3>What a photo man!</h3>
                                    <p>Nice photo, eh.</p>
                                </div>
                            </div>
                        </div>
                        <div className="grid-item">
                            <div className="photo">
                                <div className="photo-wrapper"><img src="img/user/02.jpg" alt="user image" className="img-responsive fw"/>
                                    <div className="photo-loader">
                                        <div className="sk-spinner sk-spinner-rotating-plane"></div>
                                    </div>
                                </div>
                                <div className="photo-description">
                                    <h3>Silly photo</h3>
                                    <p>Nice photo, eh.</p>
                                </div>
                            </div>
                        </div>
                        <div className="grid-item">
                            <div className="photo">
                                <div className="photo-wrapper"><img src="img/user/01.jpg" alt="user image" className="img-responsive fw"/>
                                    <div className="photo-loader">
                                        <div className="sk-spinner sk-spinner-rotating-plane"></div>
                                    </div>
                                </div>
                                <div className="photo-description">
                                    <h3>Weird photo</h3>
                                    <p>Nice photo, eh.</p>
                                </div>
                            </div>
                        </div>
                        <div className="grid-item">
                            <div className="photo">
                                <div className="photo-wrapper"><img src="img/user/04.jpg" alt="user image" className="img-responsive fw"/>
                                    <div className="photo-loader">
                                        <div className="sk-spinner sk-spinner-rotating-plane"></div>
                                    </div>
                                </div>
                                <div className="photo-description">
                                    <h3>Bumpy photo</h3>
                                    <p>Nice photo, eh.</p>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </section>
        );
    }
}

export default GridMasonry;



