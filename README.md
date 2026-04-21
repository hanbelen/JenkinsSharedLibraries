# Jenkins Shared Libraries

Reusable CI/CD pipeline functions for the SRE ecosystem.

## Contents

- `vars/standardNetworkDeploy.groovy` — Network deployment pipeline (legacy FRR)
- `vars/day1Provision.groovy` — Day1 SONiC-VS provisioning pipeline

## Usage

```groovy
@Library('sre-lib') _

day1Provision(
    inventoryRepo:  'https://github.com/hanbelen/NetworkInventoryData.git',
    automationRepo: 'https://github.com/hanbelen/NetworkAutomationCore.git',
)
```

## day1Provision Parameters

| Parameter | Default | Description |
|-----------|---------|-------------|
| `inventoryRepo` | NetworkInventoryData | GitHub URL for the SoT repo |
| `automationRepo` | NetworkAutomationCore | GitHub URL for the automation repo |
| `inventoryBranch` | main | Branch for inventory |
| `automationBranch` | main | Branch for automation |
| `credentialsId` | github-hanbelen | Jenkins credential ID for GitHub |

## Pipeline Build Parameters

| Parameter | Type | Description |
|-----------|------|-------------|
| `DRY_RUN` | boolean | Show what would change without applying |
| `SITE` | choice | Target site (syd1, mel1) |

## Jenkins Setup

1. **Manage Jenkins > System > Global Pipeline Libraries** — add `sre-lib` pointing to this repo
2. Create a Pipeline job with SCM pointing to NetworkAutomationCore, script path `Jenkinsfile.day1`
