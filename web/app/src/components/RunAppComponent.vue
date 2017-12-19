<template>
  <section xmlns:v-on="http://www.w3.org/1999/xhtml">
    <div class="container-fluid">
      <div class="card">
        <div class="card-body">
          <div v-cloak v-if="application.info">
            <h1>{{ application.info.name }}</h1>
            <h3>{{ application.info.description }}</h3>
            <h4>Author: {{ application.info.author }}</h4>
          </div>
          <hr>
          <form id="run-app" v-on:submit.prevent="submit" class="form-horizontal">
            <fieldset v-for="parameter, index in application.parameters">
              <div class="form-group" v-if="parameter.type === 'input_file'">
                <label class="col-sm-2 control-label">{{parameter.prettyName}}</label>
                <div class="col-md-4">
                  <file-selector @select="updateParam(index, 'source', $event)" :id="parameter.name + '-src'"></file-selector>
                  <span><em :id="parameter.name + '-src-error'"></em></span>
                  <span class="help-block">Source of the file</span>
                  <input v-bind:id="parameter.name + '-dst'"
                         :placeholder="parameter.defaultValue ? 'Default value: ' + parameter.defaultValue : ''"
                         :required="!parameter.optional" v-model="parameter.destination" class="form-control"
                         type="text">
                  <div>Destination of the file</div>
                </div>
              </div>
              <!--<div v-else-if="parameter.type === 'output_file'" class="form-group">
                <label v-bind:for="parameter.name" class="col-sm-2 control-label">{{parameter.prettyName}}</label>
                <div class="col-md-4">
                  <input v-bind:id="parameter.name" :required="!parameter.optional" v-model="parameter.source" class="form-control" type="text">
                  <span class="help-block">Source of the file</span>
                  <file-selector @select="updateParam(index, 'destination', $event)"></file-selector>
                  <span class="help-block">Destination of the file.</span>
                  </div>
              </div>-->
              <div class="form-group" v-else-if="parameter.type === 'float'">
                <label v-bind:for="parameter.name" class="col-sm-2 control-label">{{parameter.prettyName}}</label>
                <div class="col-md-4">
                  <input v-bind:id="parameter.name"
                         :placeholder="parameter.defaultValue ? 'Default value: ' + parameter.defaultValue : ''"
                         :required="!parameter.optional" :name="parameter.name" class="form-control"
                         type="number"
                         step="any" v-model="parameter.content">
                  <span class="help-block">{{ parameter.description }}</span>
                </div>
              </div>
              <div class="form-group" v-else-if="parameter.type === 'integer'">
                <label v-bind:for="parameter.name" class="col-sm-2 control-label">{{parameter.prettyName}}</label>
                <div class="col-md-4">
                  <input v-bind:id="parameter.name"
                         :placeholder="parameter.defaultValue ? 'Default value: ' + parameter.defaultValue : ''"
                         :required="!parameter.optional" :name="parameter.name" class="form-control"
                         type="number"
                         step="1" v-model="parameter.content">
                  <span class="help-block">{{ parameter.description }}</span>
                </div>
              </div>
              <div class="form-group" v-else-if="parameter.type === 'text'">
                <label v-bind:for="parameter.name" class="col-sm-2 control-label">{{parameter.prettyName}}</label>
                <div class="col-md-4">
                  <input v-bind:id="parameter.name"
                         :placeholder="parameter.defaultValue ? 'Default value: ' + parameter.defaultValue : ''"
                         :required="!parameter.optional" v-model="parameter.content" :name="parameter.name"
                         class="form-control"
                         type="text">
                  <span class="help-block">{{ parameter.description }}</span>
                </div>
              </div>
            </fieldset>
            <input value="Submit" :click="submit" class="btn btn-info" type="submit">
          </form>
        </div>
      </div>
    </div>
  </section>
</template>

<script>
  import Vue from 'vue'
  import FileSelector from './FileSelector.vue'

  Vue.component('file-selector', FileSelector);

  export default {
    name: 'run-app-component',
    props: {application: Object},
    methods: {
      updateParam(index, type, payload) {
        this.application.parameters[index][type] = payload
      },
      validateInputFields(parameters) {
        for (let i = 0; i < parameters.length; i++) {
          let current = parameters[i];
          switch (current.type) {
            case 'input_file':
              if (!current.optional)
                if (!current.source) {
                  document.getElementById(current.name + '-src-error').innerText = "Please select a file as input.";
                  return false;
                } else if (!current.destination) {
                  document.getElementById(current.name + '-dst').focus();
                  return false;
                }
              break;
          }
        }
        return true;
      },
      submit() {
        if (!this.validateInputFields(this.application.parameters)) return;

        let parameters = Array.from(this.application.parameters);

        let app = {
          name: this.application.info.name,
          version: this.application.info.version,
          parameters: {}
        };

        parameters.forEach((it) => {
          switch (it.type) {
            case 'input_file':
              if (it.source) {
                app.parameters[it.name] = {
                  source: it.source.path.uri,
                  destination: it.destination
                };
              }
              break;
            /*case 'output_file': // FIXME find out if I am dead code, or to be used later
              if (it.destination) {
                app.parameters[it.name] = {
                  destination: it.destination.path.uri,
                  source: it.source
                };
              }
              break;*/
            case 'float':
            case 'integer':
            case 'text':
              app.parameters[it.name] = it.content;
              break;
          }
        });

        $.post("/api/startJob/", {application: JSON.stringify(app)}, () => {
          window.location.href = "/analyses/";
        })
      }
    }
  }
</script>
