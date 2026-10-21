pipeline {
    agent any

    tools {
        maven 'Maven'
        jdk   'JDK17'
    }

    environment {
        APP_NAME           = 'vg-ms-attendanceTeacher'
        DOCKER_IMAGE       = 'paulhuayhua/vg-ms-attendanceteacher:latest'
        CONTAINER_NAME     = 'attendance-teacher-test'
        APP_PORT           = '8089'
        SONAR_PROJECT_KEY  = 'vg-ms-attendanceTeacher'
        SONAR_PROJECT_NAME = 'VG MS Attendance Teacher'
        JMETER_HOME        = '/opt/jmeter'
        JMETER_RESULTS     = "${WORKSPACE}/jmeter-results"
        SELENIUM_RESULTS   = "${WORKSPACE}/selenium-results"
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
                echo '=== Obteniendo codigo fuente desde GitHub ==='
                checkout scm
                sh 'echo "Rama: $GIT_BRANCH" && echo "Commit: $GIT_COMMIT"'
            }
        }

        // ── 2. BUILD & TEST ───────────────────────────────────────────────────
        stage('Build and Test') {
            steps {
                echo '=== Compilando y ejecutando pruebas unitarias ==='
                sh 'mvn clean verify -Pci -DskipTests=false --no-transfer-progress'
            }
            post {
                always {
                    junit testResults: 'target/surefire-reports/*.xml',
                          allowEmptyResults: true
                    jacoco(
                        execPattern:      'target/jacoco.exec',
                        classPattern:     'target/classes',
                        sourcePattern:    'src/main/java',
                        exclusionPattern: '**/*Application*,**/*Config*,**/*DTO*,**/*Mapper*'
                    )
                }
            }
        }

        // ── 3. SONARQUBE ──────────────────────────────────────────────────────
        stage('SonarQube Analysis') {
            steps {
                echo '=== Analizando calidad del codigo con SonarQube ==='
                withSonarQubeEnv('SonarQube') {
                    sh """
                        mvn sonar:sonar \
                            -Dsonar.projectKey=${SONAR_PROJECT_KEY} \
                            -Dsonar.projectName="${SONAR_PROJECT_NAME}" \
                            -Dsonar.java.source=17 \
                            -Dsonar.sources=src/main/java \
                            -Dsonar.tests=src/test/java \
                            -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
                            --no-transfer-progress
                    """
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

        // ── 5. DEPLOY TEST ────────────────────────────────────────────────────
        stage('Deploy Test Environment') {
            steps {
                echo '=== Levantando microservicio desde DockerHub ==='
                sh """
                    docker rm -f ${CONTAINER_NAME} 2>/dev/null || true

                    docker run -d \
                        --name ${CONTAINER_NAME} \
                        --network ci-network \
                        -p ${APP_PORT}:${APP_PORT} \
                        -e SERVER_PORT=${APP_PORT} \
                        -e R2DBC_URL='r2dbc:postgresql://ep-bitter-grass-apxly8gf-pooler.c-7.us-east-1.aws.neon.tech/neondb?sslMode=REQUIRE&channel_binding=require' \
                        -e R2DBC_USERNAME=neondb_owner \
                        -e R2DBC_PASSWORD=npg_Gn0j4EmMovgH \
                        -e SECURITY_ENABLED=false \
                        -e ATTENDANCE_PROCESSING_ENABLED=false \
                        -e SCHEDULES_SERVICE_URL=https://lab.vallegrande.edu.pe/prs02/via-academy/gateway/schedule/api/v1 \
                        -e USERS_SERVICE_URL=https://lab.vallegrande.edu.pe/prs02/via-academy/gateway/user/api/v1 \
                        ${DOCKER_IMAGE}

                    echo 'Esperando que el servicio levante...'
                    for i in \$(seq 1 30); do
                        STATUS=\$(curl -s -o /dev/null -w '%{http_code}' \
                            http://localhost:${APP_PORT}/actuator/health 2>/dev/null || echo '000')
                        if [ "\$STATUS" = "200" ]; then
                            echo "Servicio listo en el intento \$i"
                            break
                        fi
                        echo "Intento \$i/30 - HTTP \$STATUS"
                        sleep 5
                    done
                """
            }
        }

        // ── 6. SELENIUM ───────────────────────────────────────────────────────
        stage('Selenium Functional Tests') {
            steps {
                echo '=== Ejecutando pruebas funcionales con Selenium ==='
                sh """
                    mkdir -p ${SELENIUM_RESULTS}
                    cd selenium-tests
                    mvn test \
                        -Dtarget.url=http://localhost:${APP_PORT} \
                        -Dselenium.hub.url=http://selenium-hub:4444/wd/hub \
                        -Dsurefire.reportsDirectory=${SELENIUM_RESULTS} \
                        --no-transfer-progress || true
                """
            }
            post {
                always {
                    junit testResults: 'selenium-results/*.xml',
                          allowEmptyResults: true
                }
            }
        }

        // ── 7. JMETER ─────────────────────────────────────────────────────────
        stage('JMeter Load Tests') {
            steps {
                echo '=== Ejecutando pruebas de carga JMeter (Verde / Amarillo / Rojo) ==='
                sh """
                    mkdir -p ${JMETER_RESULTS}

                    echo '>>> VERDE: 10 usuarios / 30 segundos'
                    ${JMETER_HOME}/bin/jmeter -n \
                        -t ${WORKSPACE}/jmeter-tests/attendance_load_test.jmx \
                        -l ${JMETER_RESULTS}/green-results.jtl \
                        -e -o ${JMETER_RESULTS}/green-report \
                        -Jusers=10 -Jrampup=10 -Jduration=30 \
                        -Jtarget_host=localhost \
                        -Jtarget_port=${APP_PORT}

                    echo '>>> AMARILLO: 50 usuarios / 60 segundos'
                    ${JMETER_HOME}/bin/jmeter -n \
                        -t ${WORKSPACE}/jmeter-tests/attendance_load_test.jmx \
                        -l ${JMETER_RESULTS}/yellow-results.jtl \
                        -e -o ${JMETER_RESULTS}/yellow-report \
                        -Jusers=50 -Jrampup=20 -Jduration=60 \
                        -Jtarget_host=localhost \
                        -Jtarget_port=${APP_PORT}

                    echo '>>> ROJO: 150 usuarios / 90 segundos'
                    ${JMETER_HOME}/bin/jmeter -n \
                        -t ${WORKSPACE}/jmeter-tests/attendance_load_test.jmx \
                        -l ${JMETER_RESULTS}/red-results.jtl \
                        -e -o ${JMETER_RESULTS}/red-report \
                        -Jusers=150 -Jrampup=30 -Jduration=90 \
                        -Jtarget_host=localhost \
                        -Jtarget_port=${APP_PORT}

                    echo 'JMeter finalizado'
                """
            }
            post {
                always {
                    publishHTML([allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true,
                        reportDir: 'jmeter-results/green-report',
                        reportFiles: 'index.html', reportName: 'JMeter - VERDE'])
                    publishHTML([allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true,
                        reportDir: 'jmeter-results/yellow-report',
                        reportFiles: 'index.html', reportName: 'JMeter - AMARILLO'])
                    publishHTML([allowMissing: true, alwaysLinkToLastBuild: true, keepAll: true,
                        reportDir: 'jmeter-results/red-report',
                        reportFiles: 'index.html', reportName: 'JMeter - ROJO'])
                }
            }
        }

        // ── 8. ARCHIVE ────────────────────────────────────────────────────────
        stage('Archive Artifacts') {
            steps {
                echo '=== Archivando artefactos ==='
                archiveArtifacts artifacts: 'target/*.jar', allowEmptyArchive: true
            }
        }
    }

    // ── SLACK ─────────────────────────────────────────────────────────────────
    post {
        always {
            sh "docker rm -f ${CONTAINER_NAME} 2>/dev/null || true"
        }
        success {
            slackSend(
                channel: '#ms-attendance-teacher',
                color:   'good',
                message: """:white_check_mark: *BUILD EXITOSO* - ${APP_NAME} #${BUILD_NUMBER}
*Rama:* ${GIT_BRANCH}
*Commit:* ${GIT_COMMIT?.take(8)}
*Duracion:* ${currentBuild.durationString}
*Pipeline:* ${BUILD_URL}"""
            )
        }
        failure {
            slackSend(
                channel: '#ms-attendance-teacher',
                color:   'danger',
                message: """:x: *BUILD FALLIDO* - ${APP_NAME} #${BUILD_NUMBER}
*Rama:* ${GIT_BRANCH}
*Etapa:* ${env.STAGE_NAME ?: 'Ver consola'}
*Logs:* ${BUILD_URL}console"""
            )
        }
        unstable {
            slackSend(
                channel: '#ms-attendance-teacher',
                color:   'warning',
                message: """:warning: *BUILD INESTABLE* - ${APP_NAME} #${BUILD_NUMBER}
*Revisar:* ${BUILD_URL}"""
            )
        }
    }
}
