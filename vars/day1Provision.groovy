def call(Map config = [:]) {
    def inventoryRepo   = config.get('inventoryRepo',  'https://github.com/hanbelen/NetworkInventoryData.git')
    def inventoryBranch = config.get('inventoryBranch', 'main')
    def automationRepo  = config.get('automationRepo',  'https://github.com/hanbelen/NetworkAutomationCore.git')
    def automationBranch = config.get('automationBranch', 'main')

    pipeline {
        agent { label 'ansible-agent' }

        parameters {
            booleanParam(
                name: 'DRY_RUN',
                defaultValue: true,
                description: 'Show what would change without applying config'
            )
            choice(
                name: 'SITE',
                choices: ['syd1', 'mel1'],
                description: 'Target site (from SoT)'
            )
        }

        environment {
            OUT_DIR = "${WORKSPACE}/day1_output"
        }

        stages {

            stage('Checkout Repos') {
                steps {
                    dir('automation') {
                        git url: automationRepo, branch: automationBranch
                    }
                    dir('inventory') {
                        git url: inventoryRepo, branch: inventoryBranch
                    }
                }
            }

            stage('Discover Devices') {
                steps {
                    sh "mkdir -p ${OUT_DIR}"
                    sh """
                        python3 automation/scripts/discover_site.py ${params.SITE} \
                            --inventory-dir inventory \
                            --output-dir ${OUT_DIR}
                    """
                    sh "cat ${OUT_DIR}/discovery_report.txt"
                }
            }

            stage('Generate Day0 Configs') {
                steps {
                    sh """
                        python3 automation/scripts/generate_day0_config.py \
                            ${OUT_DIR}/devices.json \
                            automation/references/config_db.json \
                            --output-dir ${OUT_DIR}/configs
                    """
                }
            }

            stage('Apply Day0 Config') {
                steps {
                    script {
                        if (params.DRY_RUN) {
                            echo "DRY RUN — showing generated configs (no changes applied)"
                            sh """
                                for f in ${OUT_DIR}/configs/*.json; do
                                    echo ""
                                    echo "========== \$(basename \$f) =========="
                                    python3 -m json.tool \$f
                                done
                            """
                        } else {
                            echo "APPLYING Day0 config to ${params.SITE} devices..."
                            sh """
                                ansible-playbook automation/playbooks/day0_provision.yml \
                                    -i ${OUT_DIR}/inventory.yml \
                                    -e "config_dir=${OUT_DIR}/configs"
                            """
                        }
                    }
                }
            }

            stage('Verify Interfaces') {
                steps {
                    sh """
                        ansible-playbook automation/playbooks/verify_interfaces.yml \
                            -i ${OUT_DIR}/inventory.yml
                    """
                }
            }
        }

        post {
            success { echo "Day1 pipeline completed for ${params.SITE}." }
            failure { echo "Day1 pipeline failed for ${params.SITE}. Check logs." }
            always  { archiveArtifacts artifacts: 'day1_output/**', allowEmptyArchive: true }
        }
    }
}
