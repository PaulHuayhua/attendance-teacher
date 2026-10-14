pipeline {
    agent any

    environment {
        APP_NAME         = 'vg-ms-attendanceTeacher'
        APP_VERSION      = '0.0.1-SNAPSHOT'
        DOCKER_IMAGE     = 'vg-ms-attendance-teacher'
        DOCKER_TAG       = "${BUILD_NUMBER}"
        SONAR_PROJECT_KEY  = 'vg-ms-attendanceTeacher'
        SONAR_PROJECT_NAME = 'VG MS Attendance Teacher'
        PROJECT_DIR      = '/workspace/vg-ms-attendanceTeacher'
        JMETER_RESULTS   = 'jmeter-results'
        SELENIUM_RESULTS = 'selenium-results'
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
        timeout(time: 45, unit: 'MINUTES')
        timestamps()
    }

    stages {

        // ── 1. CHECKOUT ───────────────────────────────────────────────────────
        stage('Checkout') {
            steps {
                echo '=== Obteniendo codigo fuente ==='
                checkout scm
            }
        }

        // ── 2. BUILD & TEST ───────────────────────────────────────────────────
        stage('Build and Test') {
            steps {
                echo '=== Compilando y ejecutando pruebas unitarias ==='
                dir('vg-ms-attendanceTeacher') {
                    sh './mvnw clean verify -Pci -DskipTests=false --no-transfer-progress'
                }
            }
            post {
                always {
                    junit testResults: 'vg-ms-attendanceTeacher/target/surefire-reports/*.xml',
                          allowEmptyResults: true
                    jacoco(
                        execPattern:      'vg-ms-attendanceTeacher/target/jacoco.exec',
                        classPattern:     'vg-ms-attendanceTeacher/target/classes',
                        sourcePattern:    'vg-ms-attendanceTeacher/src/main/java',
                        exclusionPattern: '**/*Application*,**/*Config*,**/*DTO*,**/*Mapper*'
                    )
                }
            }
        }

        // ── 3. SONARQUBE ──────────────────────────────────────────────────────
        stage('SonarQube Analysis') {
            steps {
                echo '=== Analizando calidad del codigo con SonarQube ==='
                dir('vg-ms-attendanceTeacher') {
                    withSonarQubeEnv('SonarQube') {
                        sh """
                            ./mvnw sonar:sonar \\
                                -Dsonar.projectKey=${SONAR_PROJECT_KEY} \\
                                -Dsonar.projectName="${SONAR_PROJECT_NAME}" \\
                                -Dsonar.projectVersion=${APP_VERSION} \\
                                -Dsonar.java.source=17 \\
                                -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \\
                                -Dsonar.sources=src/main/java \\
                                -Dsonar.tests=src/test/java \\
                                --no-transfer-progress
                        """
                    }
                }
            }
        }

        // ── 4. QUALITY GATE ───────────────────────────────────────────────────
        stage('Quality Gate') {
            steps {
                echo '=== Esperando Quality Gate de SonarQube ==='
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: false
                }
            }
        }

        // ── 5. DOCKER BUILD ───────────────────────────────────────────────────
        stage('Docker Build') {
            steps {
                echo '=== Construyendo imagen Docker ==='
                dir('vg-ms-attendanceTeacher') {
                    sh """
                        docker build \\
                            -t ${DOCKER_IMAGE}:${DOCKER_TAG} \\
                            -t ${DOCKER_IMAGE}:latest .
                    """
                }
            }
        }

        // ── 6. DEPLOY TEST ────────────────────────────────────────────────────
        stage('Deploy Test Environment') {
            steps {
                echo '=== Levantando microservicio en entorno de pruebas ==='
                dir('vg-ms-attendanceTeacher') {
                    sh """
                        docker rm -f attendance-teacher-test 2>/dev/null || true

                        docker run -d \\
                            --name attendance-teacher-test \\
                            --network ci-network \\
                            -p 8089:8089 \\
                            --env-file .env \\
                            -e SECURITY_ENABLED=false \\
                            -e ATTENDANCE_PROCESSING_ENABLED=false \\
                            ${DOCKER_IMAGE}:${DOCKER_TAG}

                        echo 'Esperando que el servicio responda...'
                        for i in \$(seq 1 30); do
                            STATUS=\$(curl -s -o /dev/null -w '%{http_code}' \\
                                http://localhost:8089/actuator/health 2>/dev/null || echo '000')
                            if [ "\$STATUS" = "200" ]; then
                                echo "Servicio listo en intento \$i"
                                break
                            fi
                            echo "Intento \$i/30 - Status: \$STATUS"
                            sleep 5
                        done
                    """
                }
            }
        }

        // ── 7. SELENIUM ───────────────────────────────────────────────────────
        stage('Selenium Functional Tests') {
            steps {
                echo '=== Ejecutando pruebas funcionales con Selenium ==='
                dir('vg-ms-attendanceTeacher') {
                    sh """
                        mkdir -p ${SELENIUM_RESULTS}

                        docker run --rm \\
                            --network ci-network \\
                            -v \$(pwd)/selenium-tests:/tests \\
                            -v \$(pwd)/${SELENIUM_RESULTS}:/results \\
                            -e TARGET_URL=http://attendance-teacher-test:8089 \\
                            -e selenium.hub.url=http://selenium-hub:4444/wd/hub \\
                            maven:3.9.9-eclipse-temurin-17 \\
                            bash -c "cd /tests && mvn test \\
                                -Dtarget.url=http://attendance-teacher-test:8089 \\
                                -Dselenium.hub.url=http://selenium-hub:4444/wd/hub \\
                                -Dsurefire.reportsDirectory=/results \\
                                --no-transfer-progress"
                    """
                }
            }
            post {
                always {
                    junit testResults: "vg-ms-attendanceTeacher/${SELENIUM_RESULTS}/*.xml",
                          allowEmptyResults: true
                }
            }
        }

        // ── 8. JMETER ─────────────────────────────────────────────────────────
        stage('JMeter Load Tests') {
            steps {
                echo '=== Ejecutando pruebas de carga con JMeter (3 escenarios) ==='
                dir('vg-ms-attendanceTeacher') {
                    sh """
                        mkdir -p ${JMETER_RESULTS}

                        echo '--- Escenario VERDE: 10 usuarios / 30s ---'
                        docker run --rm \\
                            --network ci-network \\
                            -v \$(pwd)/jmeter-tests:/jmeter \\
                            -v \$(pwd)/${JMETER_RESULTS}:/results \\
                            justb4/jmeter:5.6.3 \\
                            -n -t /jmeter/attendance_load_test.jmx \\
                            -l /results/green-results.jtl \\
                            -e -o /results/green-report \\
                            -Jusers=10 -Jrampup=10 -Jduration=30 \\
                            -Jtarget_host=attendance-teacher-test \\
                            -Jtarget_port=8089

                        echo '--- Escenario AMARILLO: 50 usuarios / 60s ---'
                        docker run --rm \\
                            --network ci-network \\
                            -v \$(pwd)/jmeter-tests:/jmeter \\
                            -v \$(pwd)/${JMETER_RESULTS}:/results \\
                            justb4/jmeter:5.6.3 \\
                            -n -t /jmeter/attendance_load_test.jmx \\
                            -l /results/yellow-results.jtl \\
                            -e -o /results/yellow-report \\
                            -Jusers=50 -Jrampup=20 -Jduration=60 \\
                            -Jtarget_host=attendance-teacher-test \\
                            -Jtarget_port=8089

                        echo '--- Escenario ROJO: 150 usuarios / 90s ---'
                        docker run --rm \\
                            --network ci-network \\
                            -v \$(pwd)/jmeter-tests:/jmeter \\
                            -v \$(pwd)/${JMETER_RESULTS}:/results \\
                            justb4/jmeter:5.6.3 \\
                            -n -t /jmeter/attendance_load_test.jmx \\
                            -l /results/red-results.jtl \\
                            -e -o /results/red-report \\
                            -Jusers=150 -Jrampup=30 -Jduration=90 \\
                            -Jtarget_host=attendance-teacher-test \\
                            -Jtarget_port=8089

                        echo 'Pruebas JMeter finalizadas'
                    """
                }
            }
            post {
                always {
                    publishHTML([
                        allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true,
                        reportDir:   "vg-ms-attendanceTeacher/${JMETER_RESULTS}/green-report",
                        reportFiles: 'index.html', reportName: 'JMeter - VERDE'
                    ])
                    publishHTML([
                        allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true,
                        reportDir:   "vg-ms-attendanceTeacher/${JMETER_RESULTS}/yellow-report",
                        reportFiles: 'index.html', reportName: 'JMeter - AMARILLO'
                    ])
                    publishHTML([
                        allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true,
                        reportDir:   "vg-ms-attendanceTeacher/${JMETER_RESULTS}/red-report",
                        reportFiles: 'index.html', reportName: 'JMeter - ROJO'
                    ])
                }
            }
        }

        // ── 9. ARCHIVE ────────────────────────────────────────────────────────
        stage('Archive Artifacts') {
            steps {
                echo '=== Archivando artefactos ==='
                archiveArtifacts artifacts: 'vg-ms-attendanceTeacher/target/*.jar',
                                 allowEmptyArchive: true
            }
        }
    }

    // ── NOTIFICACIONES SLACK ──────────────────────────────────────────────────
    post {
        always {
            sh 'docker rm -f attendance-teacher-test 2>/dev/null || true'
        }

        success {
            slackSend(
                channel: '#ms-attendance-teacher',
                color:   'good',
                message: """*BUILD EXITOSO* - ${APP_NAME} #${BUILD_NUMBER}
Rama: ${GIT_BRANCH ?: 'local'}
Duracion: ${currentBuild.durationString}
SonarQube: ${JENKINS_URL}job/${JOB_NAME}/${BUILD_NUMBER}/
Pipeline: ${BUILD_URL}"""
            )
        }

        failure {
            slackSend(
                channel: '#ms-attendance-teacher',
                color:   'danger',
                message: """*BUILD FALLIDO* - ${APP_NAME} #${BUILD_NUMBER}
Etapa fallida: ${env.STAGE_NAME ?: 'Ver consola'}
Logs: ${BUILD_URL}console"""
            )
        }

        unstable {
            slackSend(
                channel: '#ms-attendance-teacher',
                color:   'warning',
                message: """*BUILD INESTABLE* - ${APP_NAME} #${BUILD_NUMBER}
Revisar Quality Gate o pruebas: ${BUILD_URL}"""
            )
        }
    }
}
