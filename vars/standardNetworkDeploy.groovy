def call(Map config = [:]) {
    def playbook = config.get('playbook', 'deploy_underlay.yml')
    def bootstrapPlaybook = config.get('bootstrapPlaybook', 'bootstrap_frr.yml')
    def site = config.get('site', 'syd1')

    pipeline {
        agent { label 'ansible-agent' }

        parameters {
            booleanParam(name: 'DEPLOY', defaultValue: false, description: 'Apply config to devices (false = dry run only)')
            booleanParam(name: 'DAY1', defaultValue: false, description: 'Day 1: Install FRR + deploy underlay (first time only)')
        }

        stages {
            stage('Validate Inventory') {
                steps {
                    sh """
                        cd /workspace/automation
                        ansible-inventory -i inventory/hosts.yml --list --limit ${site} | head -20
                    """
                }
            }

            stage('Day 1 - Install FRR') {
                when {
                    expression { params.DAY1 == true && params.DEPLOY == true }
                }
                steps {
                    echo "Installing FRR on ${site} devices..."
                    sh """
                        cd /workspace/automation
                        ansible-playbook playbooks/${bootstrapPlaybook} \
                        -i inventory/hosts.yml \
                        --limit ${site} \
                        -e "site=${site}"
                    """
                }
            }

            stage('Dry Run (Diff)') {
                when {
                    expression { params.DAY1 == false }
                }
                steps {
                    echo "Comparing intent vs actual state on FRR devices..."
                    sh """
                        cd /workspace/automation
                        ansible-playbook playbooks/${playbook} \
                        -i inventory/hosts.yml \
                        --limit ${site} \
                        -e "site=${site}" \
                        --check --diff
                    """
                }
            }

            stage('Deploy Underlay') {
                when {
                    expression { params.DEPLOY == true && params.DAY1 == false }
                }
                steps {
                    echo "Pushing FRR config to ${site} fabric..."
                    sh """
                        cd /workspace/automation
                        ansible-playbook playbooks/${playbook} \
                        -i inventory/hosts.yml \
                        --limit ${site} \
                        -e "site=${site}"
                    """
                }
            }
        }
    }
}
