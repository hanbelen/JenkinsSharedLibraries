def call(Map config = [:]) {
    def inventoryRepo    = config.get('inventoryRepo',  'https://github.com/hanbelen/NetworkInventoryData.git')
    def inventoryBranch  = config.get('inventoryBranch', 'main')
    def automationRepo   = config.get('automationRepo',  'https://github.com/hanbelen/NetworkAutomationCore.git')
    def automationBranch = config.get('automationBranch', 'main')
    def credentialsId    = config.get('credentialsId', 'github-hanbelen')

    // Device lists per site (from SoT devices.yml)
    def siteDevices = [
        'syd1': ['all', 'syd1-a-p0-spn-01', 'syd1-a-p0-ssp-01', 'syd1-a-p0-ssp-02',
                  'syd1-a-p0-brl-01', 'syd1-a-p0-brl-05', 'syd1-a-p0-brl-06',
                  'syd1-a-p1-lef-01', 'syd1-a-p1-lef-02', 'syd1-a-p1-spn-01', 'syd1-a-p1-spn-02',
                  'syd1-a-p2-lef-01', 'syd1-a-p2-spn-01'],
        'mel1': ['all', 'mel1-a-p0-spn-01', 'mel1-a-p0-ssp-01',
                  'mel1-a-p1-lef-01', 'mel1-a-p1-spn-01'],
    ]

    properties([
        parameters([
            booleanParam(
                name: 'DRY_RUN',
                defaultValue: true,
                description: 'Show what would change without applying config'
            ),
            choice(
                name: 'SITE',
                choices: ['syd1', 'mel1'],
                description: 'Target site (from SoT)'
            ),
            [$class: 'CascadeChoiceParameter',
                name: 'TARGET_DEVICE',
                description: 'Deploy to a single device or all devices in the site',
                referencedParameters: 'SITE',
                choiceType: 'PT_SINGLE_SELECT',
                script: [$class: 'GroovyScript',
                    script: [
                        classpath: [],
                        sandbox: true,
                        script: """
                            def devices = ${siteDevices.inspect()}
                            return devices.get(SITE, ['all'])
                        """.stripIndent()
                    ],
                    fallbackScript: [
                        classpath: [],
                        sandbox: true,
                        script: "return ['all']"
                    ]
                ]
            ]
        ])
    ])

    pipeline {
        agent { label 'ansible-agent' }

        environment {
            OUT_DIR = "${WORKSPACE}/day1_output"
        }

        stages {

            stage('Checkout Repos') {
                steps {
                    dir('automation') {
                        git url: automationRepo, branch: automationBranch, credentialsId: credentialsId
                    }
                    dir('inventory') {
                        git url: inventoryRepo, branch: inventoryBranch, credentialsId: credentialsId
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
                            automation/references/default.json \
                            --output-dir ${OUT_DIR}/configs
                    """
                }
            }

            stage('Apply Day0 Config') {
                steps {
                    script {
                        if (params.DRY_RUN) {
                            echo "DRY RUN — configs generated but not applied"
                            sh "ls -1 ${OUT_DIR}/configs/"
                        } else {
                            def target = params.TARGET_DEVICE ?: 'all'
                            def limit = target != 'all' ? "--limit ${target}" : ""
                            if (limit) {
                                echo "TARGETING: ${target}"
                            }
                            sh """
                                ansible-playbook automation/playbooks/day0_provision.yml \
                                    -i ${OUT_DIR}/inventory.yml \
                                    -e "config_dir=${OUT_DIR}/configs" \
                                    ${limit}
                            """
                        }
                    }
                }
            }

            stage('Verify Interfaces') {
                when {
                    expression { return !params.DRY_RUN }
                }
                steps {
                    script {
                        def target = params.TARGET_DEVICE ?: 'all'
                        def limit = target != 'all' ? "--limit ${target}" : ""
                        sh """
                            ansible-playbook automation/playbooks/verify_interfaces.yml \
                                -i ${OUT_DIR}/inventory.yml \
                                ${limit}
                        """
                    }
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
