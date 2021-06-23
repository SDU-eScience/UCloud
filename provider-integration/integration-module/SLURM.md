# Slurm PoC

## Installation

Project https://github.com/giovtorres/slurm-docker-cluster is used to integrate with docker-compose  

The client node must mount all the slurm volumes and munge daemon must be running   
Slurm config directory is customized with env variable SLURM_CONF=/etc/slurm/slurm.conf  

Volumes and env variable are defined in compose/integration.yml  


Packages needed: 
* munge
* slurm-client (ubuntu) / for centos make instructions at https://slurm.schedmd.com/quickstart_admin.html and https://github.com/SchedMD/slurm/blob/master/INSTALL 

## Validate setup

1. Exec into IM  
`docker-compose exec integration-module bash`

2. Chdir to /data and create a sample batch file job.sbatch

```
#!/usr/bin/bash
#
#SBATCH --job-name=test
#SBATCH --output=res.txt
#SBATCH --error=error.txt
#
#SBATCH --nodes=1
#SBATCH --time=00:01:00

srun /usr/bin/echo "hellow_world"
srun /usr/bin/hostname

```

3. Submit job  
`sbatch job.sbatch `

4. Check successful completion
`saccnt`

## Validate setup for testuser

* Make sure testuser(1000):testuser(1001) exists on SLURM nodes  
* Change owner of /data to testuser:testuser  
* Create sample job through UCloud  
* Observe job is successful  