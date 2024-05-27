package slurm

import (
    "ucloud.dk/pkg/util"
)

type Account struct {
    Name          string         `slurm:"Account"`
    Parent        util.OptString `slurm:"ParentName"`
    Description   string         `slurm:"Descr"            format:"description"`
    Organization  string         `slurm:"Org"              format:"organization"`
    DefaultQoS    string         `slurm:"Def QOS"          format:"defaultqos"`
    QoS           []string       `slurm:"QOS"              format:"qos"`
    MaxJobs       int            `slurm:"MaxJobs"          format:"maxjobs"`
    MaxSubmitJobs int            `slurm:"MaxSubmit"        format:"maxsubmitjobs"`
    Users         []string       `slurm:"multiline,User"`
    RawShares     int            `slurm:"RawShares"        format:"fairshare"`
    RawUsage      int            `slurm:"RawUsage"`
    QuotaTRES     map[string]int `slurm:"GrpTRESMins"      format:"grptresmins"`
    UsageTRES     map[string]int `slurm:"GrpTRESRaw"`
}

type User struct {
    Name           string   `slurm:"User"`
    DefaultAccount string   `slurm:"Def Acct"             format:"defaultaccount"`
    Accounts       []string `slurm:"multiline,Account"`
    Privilege      string   `slurm:"Admin"                format:"adminlevel"`
}

type Job struct {
    JobID     int            `slurm:"JobID"`
    Name      string         `slurm:"JobName"`
    User      string         `slurm:"User"`
    Account   string         `slurm:"Account"`
    Partition string         `slurm:"Partition"`
    State     string         `slurm:"State"`
    Elapsed   int            `slurm:"Elapsed"`
    TimeLimit int            `slurm:"Timelimit"`
    AllocTRES map[string]int `slurm:"AllocTRES"`
}

func NewAccount() *Account {
    return &Account{
        MaxJobs:       -1,
        MaxSubmitJobs: -1,
    }
}
