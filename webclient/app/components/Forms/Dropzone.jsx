import React from 'react';
import pubsub from 'pubsub-js';
import Dropzone from 'react-dropzone';
import { Grid, Row, Col, Button } from 'react-bootstrap';

import './Dropzone.scss';

class DropzoneUpload extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    constructor(props) {
        super(props);
        this.state = {
            files: []
        };
    }

    onDrop (files) {
        // console.log(files);
        this.setState({
            files: files
        });
    }

    onOpenClick () {
        this.refs.dropzone.open();
    }

    createImageItem(file, index){
        return (
            <Col md={3} key={index}>
                <img className="img-responsive" src={file.preview} />
            </Col>
        );
    }

    render() {
        let allFiles = this.state.files;
        return (
            <section>
                <div className="container container-lg">
                    <div className="mb-lg">
                        <h4>React Dropzone</h4>
                        <small>DropzoneJS is an open source library that provides drag&apos;n&apos;drop file uploads with image previews.</small><br/><small className="spr">It’s lightweight, doesn’t depend on any other library (like jQuery) and is</small><small><a href="http://www.dropzonejs.com/" target="_blank">highly customizable</a></small>
                    </div>
                    <Row>
                        <Col md={3}>
                            <p><small>Click to open upload dialog.</small></p>
                            <Button bsStyle="default" onClick={this.onOpenClick.bind(this)}>
                                Open Dropzone
                            </Button>
                        </Col>
                        <Col md={9}>
                            <Dropzone className="well p-lg" ref="dropzone" onDrop={this.onDrop.bind(this)} >
                              <div>Try dropping some files here, or click to select files to upload.</div>
                            </Dropzone>
                        </Col>
                    </Row>
                    <div className="mt-lg">
                        {this.state.files.length > 0 ?
                            <Row>{allFiles.map(this.createImageItem)}</Row>
                            :
                            <p><small>This demo does not upload files to any server.</small></p>
                        }
                    </div>
                </div>
            </section>
        );
    }
}

export default DropzoneUpload;
