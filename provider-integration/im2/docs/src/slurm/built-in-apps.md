# Built-in Applications

This chapter will cover how to use and configure the built-in applications which UCloud offers for Slurm-based
providers. This chapter assumes familiarity with how applications work. Refer to [this](./app-management.md) for more
details.

The built-in application repository is designed to provide end-users with essential applications for the service
provider. Access to the right software is crucial for users to perform their tasks effectively. The repository offers a
diverse selection of software commonly used in HPC environments. Including interactive applications such as JupyterLab
and RStudio, but also a selection of batch software such as Quantum ESPRESSO and GROMACS.

## Setup and Configuration

TODO Hastily written documentation, rewrite this section.

The UCloud team maintains a list of software and libraries which are loadable via the 
`software.load`/`{{ loadApplication("name", "version") }}` calls. You can find this list
[here](./built-in-app-index.md). In this section, we will explain how to enable software from this list at your service
provider.

For a piece of software/library to be enabled at your provider, you must define it in the `/etc/ucloud/applications`
folder. This is done by defining an object, which explain to the integration module how to load and unload the software.
The instructions given in this document all assume that the software is _already_ installed on your service provider.

<div class="info-box warning">
<i class="fa fa-warning"></i>
<div>

Remember that there are _no_ guarantees that any of the code in the load/unload sections are executed. The only 
guarantee that UCloud/IM provides is that the script will contain the defined instructions. They can and _will_
sometimes not fully execute. This can be due to a variety of reasons, such as Slurm cancellation, software or hardware
failure.

The load and unload instructions are executed in the context of the user. 

_Do not depend on the pre/postambles running. Do not use them to enforce any rules._

</div>
</div>

```yaml
name: gromacs

configurations:
- versions: ["2021.5"]

  # NOTE: The load is mandatory and triggered by {- applicationLoad("app", "version") -}
  # appVersion is equal to the selected version from one of the values in "versions". Multiple versions can be 
  # specified if the load/unload instructions are identical with the exception of the version number.
  #
  # NOTE: These load and unload are also Jinja2 templates and are executed in the same environment as the script.
  # This allows you to get information about the machine it will be scheduled on.
  load: |
    module load GCC/11.2.0
    module load OpenMPI/4.1.1
    module load GROMACS/{{ appVersion }}

  # NOTE: The unload is optional and triggered by {- applicationUnload("app", "version") -}
  unload: |
    echo Unloading 'GROMACS'/{{ appVersion }}

  # This allows you to override the srun command for a given application. This is optional and will default to the
  # following value:
  srun:
    command: "srun"
    flags: []
```

## Version Resolution

TODO Hastily written documentation, rewrite this section.

UCloud/IM will automatically find the best matching version based on the requested version. Thus, the version written in
`/etc/ucloud/applications` should use the actual versions being loaded while the versions specified in the application
YAML is purposefully under-specified. By default, UCloud/IM will find the newest version which matches the requested
version. UCloud uses a heuristic for determining the ordering of application. This heuristic is based off semantic
versioning, but generalized to contain an arbitrary amount of components instead of exactly 3.

Version resolution can be overriden in customizable applications by the user. This is done using 
`{- versionResolver(policy) -}`, where policy is one of the following:

- `"loose"`: Find the newest version which matches the requested version. If none is found, use the newest available.
- `"loose-forward"`: Like loose, but only select versions newer than the requested. Fail if an older version is selected.
- `"loose-backward"`: Like loose, but only select versions less than equal what is requested. Fail if an newer version is selected.
- `"strict"`: Like loose, but do not select a version which does not match the requested version.
- Any other value is considered the version to use. Fails if the exact version is not found.

The default policy is `"loose"`.

Below is a list of example of which versions will be selected in different environments.

<div class="table-wrapper">
<table>
<thead>
<tr>
<th>Requested</th>
<th>Available</th>
<th>Chosen</th>
</tr>
</thead>

<tbody>

<tr>
<td>

`1.2`

</td>

<td>

`[1.2-cray]`

</td>

<td>

`1.2-cray`

</td>
</tr>


<tr>
<td>

`1.2`

</td>

<td>

`[1.1, 1.2.3, 1.2.4, 1.2.93, 1.3]`

</td>

<td>

`1.2.93`

</td>
</tr>

<tr>
<td>

`1.2`

</td>

<td>

`[1.1, 1.3, 1.3.5]`

</td>

<td>

`1.3.5` (fails if not using `loose` or `loose-forward`)

</td>
</tr>

<tr>
<td>

`1.2`

</td>

<td>

`[1.1]`

</td>

<td>

`1.1` (fails is not using `loose` or `loose-backward`)

</td>
</tr>


</tbody>
</table>
</div>


## Summary

TODO

-	Recap of key points discussed in the chapter
-	Final tips and best practices for managing applications with the UCloud repository