import React from 'react'
import $ from 'jquery'
import LoadingIcon from './LoadingIconComponent'

class ProjectsOverviewComponent extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      projects: [],
      projectsLoading: false
    }
  }

  componentDidMount() {
    this.getProjects();
  }

  getProjects() {
    this.setState({
      projectsLoading: true
    });
    $.getJSON("/api/getProjects/").then((projects) => {
      projects.forEach((it) => {
        it.members.sort();
      });
      console.log(projects);
      this.setState({
        projects: projects,
        projectsLoading: false,
      })
    });
  }

  render() {
    return (
      <section>
        <div className="container container-fluid">
          <LoadingIcon isLoading={this.state.projectsLoading}/>
          <NoProjectsText elements={this.state.projects.length === 0}/>
          <Projects projects={this.state.projects}/>
        </div>
      </section>)
  }
}

function Projects(props) {
  const projects = props.projects;
  let i = 0;
  const projectsList = projects.map((project) =>
    <div key={i++} className="col-md-6 col-lg-4">
      <div className="card">
        <div className="card-heading">
          <div className="pull-right">
            <LabelColor project={project}/>
          </div>
          <div className="card-title">{project.name}</div>
          <small>{project.type}</small>
        </div>
        <div className="card-body"><p><strong>Description:</strong></p>
          <div className="pl-lg mb-lg">{project.description}</div>
          <p><strong>Team members:</strong></p>
          <People people={project.members}/>
          <p><strong>Activity:</strong></p>
          <div className="pl-lg">
            <ul className="list-inline m0">
              <li className="mr"><h4 className="m0">{new
              Date(project.projectstart).toLocaleString()}</h4>
                <p className="text-muted">Start time</p></li>
              <li className="mr"><h4 className="m0">{new
              Date(project.projectend).toLocaleString()}</h4>
                <p className="text-muted">End time</p></li>
            </ul>
          </div>
        </div>
      </div>
    </div>
  );

  return (
    <div className="row">
      {projectsList}
    </div>
  )
}

function NoProjectsText(props) {
  if (props.elements) {
    return (
      <h4>
        <small>No projects associated with user found.</small>
      </h4>)
  } else {
    return (<div></div>)
  }
}

function LabelColor(props) {
  const statusString = toStatusString(props.project);
  switch (statusString) {
    case "Upcoming":
      return (<div className="label label-info">{statusString}</div>);
    case "Active":
      return (<div className="label label-success">{statusString}</div>);
    case "Completed":
      return (<div className="label label-primary">{statusString}</div>);
    default:
      return (<div className="label label-basic">{statusString}</div>);
  }
}

function toStatusString(project) {
  let now = new Date();
  if (now < project.projectstart) {
    return "Upcoming";
  } else if (now < project.projectend) {
    return "Active";
  } else {
    return "Completed";
  }
}

function People(props) {
  const people = props.people;
  let i = 0;
  const peopleList = people.map((person) =>
    <span key={i++} className="inline">{person.name}</span>
  );

  return (
    <span className="text-center">
      {peopleList}
    </span>)
}

export { ProjectsOverviewComponent }
