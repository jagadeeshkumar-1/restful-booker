pipeline {

    agent any

    /*
     * TEST_GROUP is only used for manual / branch builds.
     * PR builds (CHANGE_ID is set by GitHub Branch Source plugin) always
     * force-run Smoke regardless of this parameter.
     */
    parameters {
        choice(
            name: 'TEST_GROUP',
            choices: ['Smoke', 'Regression', 'ExistingDefect', 'All'],
            description: '''Test group to run.
  • Smoke         — ~20 tests, fast sanity check
  • Regression    — all 98 tests, full coverage
  • ExistingDefect — tests documenting confirmed API bugs
  • All           — same as Regression (no -Dgroups filter)
NOTE: On PR builds this parameter is ignored — Smoke always runs.'''
        )
    }

    /*
     * Tells Jenkins to put the configured Maven installation on PATH for every
     * sh step. The name 'Maven' must match exactly what is configured under
     * Manage Jenkins → Tools → Maven installations.
     */
    tools {
        maven 'Maven'
    }

    options {
        timeout(time: 15, unit: 'MINUTES')   // abort runaway builds
        disableConcurrentBuilds()            // one build per branch at a time
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    stages {

        stage('Resolve test group') {
            steps {
                script {
                    /*
                     * env.CHANGE_ID is populated by the GitHub Branch Source
                     * plugin whenever this build was triggered by a pull request.
                     * Use it to force Smoke on every PR, allow free choice otherwise.
                     */
                    if (env.CHANGE_ID) {
                        env.RESOLVED_GROUP = 'Smoke'
                        echo "PR build detected (PR #${env.CHANGE_ID}) — forcing group: Smoke"
                    } else {
                        env.RESOLVED_GROUP = params.TEST_GROUP
                        echo "Manual / branch build — running group: ${params.TEST_GROUP}"
                    }
                }
            }
        }

        stage('Run tests') {
            steps {
                script {
                    def groupArg = (env.RESOLVED_GROUP == 'All') ? '' : "-Dgroups=${env.RESOLVED_GROUP}"
                    sh "mvn clean test ${groupArg} -B --no-transfer-progress"
                }
            }
        }

    }

    post {
        always {
            /*
             * Publish TestNG / Surefire XML results.
             * Jenkins marks the build UNSTABLE if any test fails,
             * which is enough to block a PR merge in GitHub.
             */
            junit(
                testResults: 'target/surefire-reports/**/*.xml',
                allowEmptyResults: false
            )

            /*
             * Publish the Allure report using the Allure Jenkins Plugin.
             * - Reads raw JSON from target/allure-results/ (written by AllureTestNg listener)
             * - Generates the interactive HTML report inside Jenkins
             * - Adds an "Allure Report" link on every build page
             * - Shows a pass/fail TREND graph across builds on the job page
             * - No CSP issues — report is served by Jenkins, not as a static file
             *
             * Requires: "Allure Jenkins Plugin" installed in Jenkins
             * (Manage Jenkins → Plugins → search "Allure")
             */
            allure([
                includeProperties: false,
                results          : [[path: 'target/allure-results']]
            ])
        }
        success {
            echo "All tests passed — group: ${env.RESOLVED_GROUP}"
        }
        unstable {
            echo "One or more tests FAILED — group: ${env.RESOLVED_GROUP}. PR merge is blocked."
        }
        failure {
            echo "Build ERROR (not a test failure) — check Maven output above."
        }
    }
}
