# Slurm PoC

## Installation

Project https://github.com/giovtorres/slurm-docker-cluster is used to integrate with docker-compose

The client node must mount all the slurm volumes and munge daemon must be running
Slurm config directory is customized with env variable SLURM_CONF=/etc/slurm/slurm.conf

Packages needed: 
* munge
* slurm-client (ubuntu) / for centos make instructions at https://slurm.schedmd.com/quickstart_admin.html and https://github.com/SchedMD/slurm/blob/master/INSTALL

## Validate docker-compose setup

1. Exec into IM  
`docker-compose exec integration-module bash`

2. Create a sample batch file job.sbatch

```
#!/bin/bash
#
#SBATCH --job-name=test
#SBATCH --output=res.txt
#
#SBATCH --ntasks=1
#SBATCH --time=10:00
#SBATCH --mem-per-cpu=100

echo "hello world"

srun hostname
srun sleep 60

```

3. Chdir to /data and submit job  
`sbatch job.sbatch `

4. Check successful completion
`saccnt`