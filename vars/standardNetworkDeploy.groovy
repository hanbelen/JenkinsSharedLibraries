def call(Map config = [:]) {
    def playbook = config.get('playbook', 'deploy_underlay.yml')
    def site = config.get('site', 'syd1')

    pipeline {
        agent { label 'ansible-agent' }

        parameters {
            booleanParam(name: 'DEPLOY', defaultValue: false, description: 'Apply config to devices (false = dry run only)')
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

            stage('Dry Run (Diff)') {
                steps {
                    echo "Comparing intent vs actual state on FRR devices..."
                    sh """
                        cd /workspace/automation
                        ansible-playbook playbooks/${playbook} \
                        -i inventory/hosts.yml \
                        --limit ${site} \
                        --check --diff
                    """
                }
            }

            stage('Deploy to Hardware') {
                when {
                    expression { params.DEPLOY == true }
                }
                steps {
                    echo "Pushing FRR config to ${site} fabric..."
                    sh """
                        cd /workspace/automation
                        ansible-playbook playbooks/${playbook} \
                        -i inventory/hosts.yml \
                        --limit ${site}
                    """
                }
            }
        }
    }
}
