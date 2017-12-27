<template>
  <section>
    <div class="container container-fluid">
      <loading-icon v-cloak v-if="projectsLoading"></loading-icon>
      <h4 v-if="!projects.length"><small>No projects associated with user found.</small></h4>
      <div class="row" v-else>
        <div v-for="project in projects" class="col-md-6 col-lg-4">
          <div class="card">
            <div class="card-heading">
              <div class="pull-right">
                <div class="label" :class="labelColor(project)">{{ toStatusString(project) }}</div>
              </div>
              <div class="card-title">{{ project.name}}</div>
              <small>{{ project.type }}</small>
            </div>
            <div class="card-body"><p><strong>Description:</strong></p>
              <div class="pl-lg mb-lg">{{ project.description }}</div>
              <p><strong>Team members:</strong></p>
              <span class="pl-lg mb-lg" v-for="person in project.members">
                <a v-if="person.orcid" class="inline" :href="'https://orcid.org/' + person.orcid">{{ person.name }}</a>
                <span v-else>{{ person.name }}</span>
              </span>
              <p><strong>Activity:</strong></p>
              <div class="pl-lg">
                <ul class="list-inline m0">
                  <li class="mr"><h4 class="m0">{{ new
                    Date(project.projectstart).toLocaleString() }}</h4>
                    <p class="text-muted">Start time</p></li>
                  <li class="mr"><h4 class="m0">{{ new
                    Date(project.projectend).toLocaleString() }}</h4>
                    <p class="text-muted">End time</p></li>
                </ul>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script>
  import $ from 'jquery';
  import LoadingIcon from "./LoadingIcon";

  export default {
    components: {LoadingIcon},
    name: "projects",
    data() {
      return {
        projectsLoading: false,
        projects: {}
      }
    },
    mounted() {
      this.projectsLoading = true;
      $.getJSON("/api/getProjects/").then( (projects) => {
        this.projects = projects;
        this.projects.forEach( (it) => {
          it.members.sort((a, b) => {
            return a.name.localeCompare(b.name)
          });
        });
        this.projectsLoading = false;
      });
    },
    methods: {
      toInitials(name) {
        let initials = "";
        name.split(" ").forEach((it) => {
          initials +=  it[0];
        });
        return initials;
      },
      toStatusString(project) {
        let now = new Date();
        if (now < project.projectstart) {
          return "Upcoming";
        } else if (now < project.projectend) {
          return "Active";
        } else {
          return "Completed";
        }
      },
      labelColor(project) {
        let name = this.toStatusString(project);
        switch (name) {
          case "Upcoming":
            return "label-info";
          case "Active":
            return "label-success";
          case "Completed":
            return "label-primary";
          default:
            return "label-basic";
        }
      }
    }
  }
</script>

<style scoped>
  .flex-container {
    display: -ms-flexbox;
    display: -webkit-flex;
    display: flex;
    -ms-flex-wrap: wrap;
    -webkit-flex-wrap: wrap;
    flex-wrap: wrap;
  }
</style>
