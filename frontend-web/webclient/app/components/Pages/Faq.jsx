import React from 'react';
import pubsub from 'pubsub-js';
import { Grid, Row, Col, Button, Panel, Accordion } from 'react-bootstrap';

class Faq extends React.Component {

    componentWillMount() {
        pubsub.publish('setPageTitle', this.constructor.name);
    }

    render() {

        let accordionHeader = (title) => {
            return (
                <h4>
                    <small>
                        <em className="ion-arrow-right-c text-muted mr icon-lg"></em>
                    </small>
                    {title}
                </h4>
            );
        }

        return (
            <section>
                <Grid fluid>
                    <div className="card">
                        <div className="card-body">
                            <div className="text-center pb-lg">
                                <h3 className="text-bold">Frequently Asked Questions</h3>
                                <p className="text-muted">Proin non lacinia sapien. Vivamus lorem justo, sollicitudin ac sollicitudin ut, aliquet sed tellus.<br/>Sed venenatis ullamcorper mauris et malesuada. Nunc vel ipsum vitae erat pharetra rhoncus eu eu tellus.</p><br/>
                                <p>Have more questions?</p>
                                <button type="button" className="btn btn-info">Contact with Us</button>
                            </div>
                            <div className="row mt-lg">
                                <div className="col-md-6">
                                   <Accordion>
                                        <Panel header={accordionHeader('What Assets Can I Use In My Items?')} eventKey="1" className="b0">
                                            <p>Donec congue sagittis mi sit amet tincidunt. Quisque sollicitudin massa vel erat tincidunt blandit. Curabitur quis leo nulla. Phasellus faucibus placerat luctus. Integer fermentum molestie massa at congue. Quisque quis quam dictum diam volutpat adipiscing.</p>
                                            <p>Proin ut urna enim. Nam non enim vitae mi semper egestas. Pellentesque convallis mauris eu elit imperdiet quis eleifend quam aliquet.</p>
                                            <div className="pull-right"><small className="text-muted mr">Was this article helpful?</small>
                                                <Button bsStyle="default" bsSize="xsmall"><em className="ion-chevron-up text-muted"></em></Button>
                                                <Button bsStyle="default" bsSize="xsmall"><em className="ion-chevron-down text-muted"></em></Button>
                                            </div>
                                        </Panel>
                                        <Panel header={accordionHeader('Where is the Market Terms?')} eventKey="2" className="b0">
                                            <p>Donec congue sagittis mi sit amet tincidunt. Quisque sollicitudin massa vel erat tincidunt blandit. Curabitur quis leo nulla. Phasellus faucibus placerat luctus. Integer fermentum molestie massa at congue. Quisque quis quam dictum diam volutpat adipiscing.</p>
                                            <p>Proin ut urna enim. Nam non enim vitae mi semper egestas. Pellentesque convallis mauris eu elit imperdiet quis eleifend quam aliquet.</p>
                                            <div className="pull-right"><small className="text-muted mr">Was this article helpful?</small>
                                                <Button bsStyle="default" bsSize="xsmall"><em className="ion-chevron-up text-muted"></em></Button>
                                                <Button bsStyle="default" bsSize="xsmall"><em className="ion-chevron-down text-muted"></em></Button>
                                            </div>
                                        </Panel>
                                        <Panel header={accordionHeader('Morbi pretium varius aliquam?')} eventKey="3" className="b0">
                                            <p>Donec congue sagittis mi sit amet tincidunt. Quisque sollicitudin massa vel erat tincidunt blandit. Curabitur quis leo nulla. Phasellus faucibus placerat luctus. Integer fermentum molestie massa at congue. Quisque quis quam dictum diam volutpat adipiscing.</p>
                                            <p>Proin ut urna enim. Nam non enim vitae mi semper egestas. Pellentesque convallis mauris eu elit imperdiet quis eleifend quam aliquet.</p>
                                            <div className="pull-right"><small className="text-muted mr">Was this article helpful?</small>
                                                <Button bsStyle="default" bsSize="xsmall"><em className="ion-chevron-up text-muted"></em></Button>
                                                <Button bsStyle="default" bsSize="xsmall"><em className="ion-chevron-down text-muted"></em></Button>
                                            </div>
                                        </Panel>
                                   </Accordion>
                                </div>
                                <div className="col-md-6">
                                   <Accordion>
                                        <Panel header={accordionHeader('How to Contact an Author')} eventKey="1" className="b0">
                                            <p>Donec congue sagittis mi sit amet tincidunt. Quisque sollicitudin massa vel erat tincidunt blandit. Curabitur quis leo nulla. Phasellus faucibus placerat luctus. Integer fermentum molestie massa at congue. Quisque quis quam dictum diam volutpat adipiscing.</p>
                                            <p>Proin ut urna enim. Nam non enim vitae mi semper egestas. Pellentesque convallis mauris eu elit imperdiet quis eleifend quam aliquet.</p>
                                            <div className="pull-right"><small className="text-muted mr">Was this article helpful?</small>
                                                <Button bsStyle="default" bsSize="xsmall"><em className="ion-chevron-up text-muted"></em></Button>
                                                <Button bsStyle="default" bsSize="xsmall"><em className="ion-chevron-down text-muted"></em></Button>
                                            </div>
                                        </Panel>
                                        <Panel header={accordionHeader('What is Item Support?')} eventKey="2" className="b0">
                                            <p>Donec congue sagittis mi sit amet tincidunt. Quisque sollicitudin massa vel erat tincidunt blandit. Curabitur quis leo nulla. Phasellus faucibus placerat luctus. Integer fermentum molestie massa at congue. Quisque quis quam dictum diam volutpat adipiscing.</p>
                                            <p>Proin ut urna enim. Nam non enim vitae mi semper egestas. Pellentesque convallis mauris eu elit imperdiet quis eleifend quam aliquet.</p>
                                            <div className="pull-right"><small className="text-muted mr">Was this article helpful?</small>
                                                <Button bsStyle="default" bsSize="xsmall"><em className="ion-chevron-up text-muted"></em></Button>
                                                <Button bsStyle="default" bsSize="xsmall"><em className="ion-chevron-down text-muted"></em></Button>
                                            </div>
                                        </Panel>
                                        <Panel header={accordionHeader('Where Is My Purchase Code?')} eventKey="3" className="b0">
                                            <p>Donec congue sagittis mi sit amet tincidunt. Quisque sollicitudin massa vel erat tincidunt blandit. Curabitur quis leo nulla. Phasellus faucibus placerat luctus. Integer fermentum molestie massa at congue. Quisque quis quam dictum diam volutpat adipiscing.</p>
                                            <p>Proin ut urna enim. Nam non enim vitae mi semper egestas. Pellentesque convallis mauris eu elit imperdiet quis eleifend quam aliquet.</p>
                                            <div className="pull-right"><small className="text-muted mr">Was this article helpful?</small>
                                                <Button bsStyle="default" bsSize="xsmall"><em className="ion-chevron-up text-muted"></em></Button>
                                                <Button bsStyle="default" bsSize="xsmall"><em className="ion-chevron-down text-muted"></em></Button>
                                            </div>
                                        </Panel>
                                   </Accordion>
                                </div>
                            </div>
                        </div>
                    </div>
                </Grid>
            </section>
        );
    }
}

export default Faq;

