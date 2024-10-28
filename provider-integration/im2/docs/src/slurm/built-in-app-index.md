# Index of Built-in Applications

This appendix contains an index of all the built-in applications available in UCloud/IM for Slurm. Please refer to the
[main chapter](./built-in-apps.md) before reading this.

<div class="table-wrapper">
<table>
<thead>
<tr>
<th>Name</th>
<th>Notes</th>
</tr>
</thead>

<tbody>

<tr>
<td>

`gromacs`

</td>
<td>

Molecular dynamics software package mainly designed for simulations of proteins, lipids and nucleic acids. 
See their [website](https://gromacs.org) for details.

Example:

```
name: gromacs

configurations:
- versions: ["2021.5"]

  load: |
    module load GCC/11.2.0
    module load OpenMPI/4.1.1

    {% if ucloud.machine.gpu > 0 %}
        module load GROMACS/{{ appVersion }}-CUDA-11.4.1
    {% else %}
        module load GROMACS/{{ appVersion }}
    {% endif %}
```

</td>
</tr>

</tbody>
</table>
</div>
