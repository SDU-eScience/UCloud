<template>
  <div><input v-bind:id="id" v-bind:name="nameDestination" class="form-control" type="text">
    <span class="help-block">{{description}}</span></div>
</template>

<script>
  export default {
    name: "text-file-input",
    props: { id: String, nameDestination: String, description: String }
  }
</script>
