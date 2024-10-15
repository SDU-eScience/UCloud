import * as React from "react";
import {Feature, hasFeature} from "@/Features";
import {Editor, Vfs, VirtualFile} from "@/Editor/Editor";
import {useMemo} from "react";
import {delay} from "@/UtilityFunctions";

const Fork: React.FunctionComponent = () => {
    if (!hasFeature(Feature.COPY_APP_MOCKUP)) return null;
    const vfs = useMemo(() => {
        return new DummyVfs();
    }, []);

    return <Editor vfs={vfs} title={"Editing application: foobar 2024"} initialPath={"/001_job_init.sh"}/>;
};

class DummyVfs implements Vfs {
    async listFiles(path: string): Promise<VirtualFile[]> {
        let files: VirtualFile[] = [];
        switch (path) {
            case "":
            case "/":
                files = [
                    {absolutePath: "/001_job_init.sh", isDirectory: false},
                    {absolutePath: "/002_job.sh", isDirectory: false},
                    {absolutePath: "/Examples", isDirectory: true},
                ];
                break;

            case "/Examples":
                files = [
                    {absolutePath: "/Examples/A", isDirectory: true},
                    {absolutePath: "/Examples/B", isDirectory: true},
                    {absolutePath: "/Examples/C", isDirectory: true},
                ];
                break;

            case "/Examples/A":
                files = [
                    {absolutePath: "/Examples/A/001_job_init.sh", isDirectory: false},
                    {absolutePath: "/Examples/A/002_job.sh", isDirectory: false},
                ];
                break;

            case "/Examples/B":
                files = [
                    {absolutePath: "/Examples/B/001_job_init.sh", isDirectory: false},
                    {absolutePath: "/Examples/B/002_job.sh", isDirectory: false},
                ];
                break;

            case "/Examples/C":
                files = [
                    {absolutePath: "/Examples/C/001_job_init.sh", isDirectory: false},
                    {absolutePath: "/Examples/C/002_job.sh", isDirectory: false},
                ];
                break;
        }

        await delay(Math.random() * 250);
        return files;
    }

    async readFile(path: string): Promise<string> {
        let result = "";
        switch (path) {
            case "/001_job_init.sh":
                // TODO link
                result = `
{#

This script will run on a node equivalent to the HPC-frontend. It has outgoing Internet access and should be used to
download and compile software required for the job.

The script evaluates a Jinja2 template which produces a bash script. You can read more about writing these scripts here:
https://docs.cloud.sdu.dk.

The following Jinja2 variables are available and populated from the job submission page:

- {{ app.param1 }}: lorem ipsum dolar sit amet

#}

{# The following line will fetch required modules from the job script and automatically install them if needed. #} 
{{ installRequiredModules() }}

{# Submits the job to the underlying scheduler. The job script is defined in '002_job.sh'. #}
{{ submitJob() }}
                `.trim() + "\n";
                break;

            case "/002_job.sh":
                result = `
{#

This script is submitted by 001_job_init.sh and will be interpreted by Slurm.

The following sbatch directives are automatically applied based on job parameters:

#SBATCH --account=...
#SBATCH --partition=...
#SBATCH --qos=...
#SBATCH --constraint=...
#SBATCH --output=...
#SBATCH --error=...
#SBATCH --time=...
#SBATCH --cpus-per-task=...
#SBATCH --gpus-per-task=...
#SBATCH --mem=...
#SBATCH --nodes=...
#SBATCH --job-name=...
#SBATCH --chdir=...

Directives can be overwritten via the following method: {{ sbatch("chdir", "/tmp/new/directory") }} 

#}

{# Preambles start - Avoid touching these unless required. #}
    {- preamble -}
    {- quantumEspressoPreamble -}
{# Preambles end #}

{# Modules start - Change as required #}
    {{ loadModule("quantum-espresso", ">7.0") }}
{# Modules end #}

{# =============================================================================================================== #}
{# Start the job - change as required                                                                              #}
{# =============================================================================================================== #}

echo Running on "$(hostname)" with {{ ucloud.machine.name }}
echo Available nodes: "$SLURM_NODELIST"
echo Slurm_submit_dir: "$SLURM_SUBMIT_DIR"
echo Start time: "$(date)"

export OMP_NUM_THREADS=1

cd {{ input_dir }}
srun -n $SLURM_NTASKS pw.x < pw.Si.in | tee pw.Si.out
srun -n $SLURM_NTASKS ph.x < ph.Si.in | tee ph.Si.out
srun -n $SLURM_NTASKS dynmat.x < dynmat.Si.in | tee dynmat.Si.out

{# =============================================================================================================== #}
{# End of job                                                                                                      #}
{# =============================================================================================================== #}

{# Postambles start - Avoid touching these unless required. #}
    {- quantumEspressoPostamble -}
    {- postamble -}
{# Postambles end #}

                `.trim() + "\n";
                break;
        }
        await delay(Math.random() * 250);
        return result;
    }

    async writeFile(path: string, content: string): Promise<void> {
        await delay(Math.random() * 250);
    }
}

export default Fork;
